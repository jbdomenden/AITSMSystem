package backend.services

import backend.config.Env
import backend.models.ProfilePhotoRequest
import backend.models.UserRole
import backend.repository.AuditRepository
import backend.repository.UserRepository
import io.ktor.http.ContentType
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.util.UUID
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ProfilePhotoService(
    private val userRepository: UserRepository,
    private val auditRepository: AuditRepository,
    private val notificationService: NotificationService
) {
    private val uploadDir: Path = Path.of(Env.get("PROFILE_UPLOAD_DIR") ?: "uploads/profile-photos")
        .toAbsolutePath().normalize()
    private val maxBytes = 5L * 1024 * 1024

    init { Files.createDirectories(uploadDir) }

    fun submit(userId: Int, originalFileName: String?, contentType: ContentType?, bytes: ByteArray): ProfilePhotoRequest {
        require(!userRepository.hasPendingProfilePhoto(userId)) { "A profile photo is already waiting for admin approval." }
        require(bytes.isNotEmpty()) { "Please choose an image to upload." }
        require(bytes.size.toLong() <= maxBytes) { "Image must be 5 MB or smaller." }
        val processed = SecureImageProcessor.process(originalFileName, contentType, bytes)
        val extension = processed.extension
        val filename = "${UUID.randomUUID()}.$extension"
        val target = uploadDir.resolve(filename).normalize()
        check(target.parent == uploadDir) { "Invalid upload path" }
        try {
            Files.write(target, processed.bytes, StandardOpenOption.CREATE_NEW)
            val request = userRepository.createProfilePhotoRequest(userId, "/uploads/profile-photos/$filename")
            auditRepository.log(userId, "Submitted profile photo for approval", "profile_photo_requests")
            return request
        } catch (error: Exception) {
            Files.deleteIfExists(target)
            throw error
        }
    }

    fun pending(): List<ProfilePhotoRequest> = userRepository.pendingProfilePhotoRequests()

    fun pendingFor(userId: Int): ProfilePhotoRequest? = userRepository.pendingProfilePhotoRequest(userId)

    fun photoForViewer(filename: String, viewerId: Int): Path? {
        if (!filename.matches(Regex("[0-9a-fA-F-]{36}\\.(jpg|png)"))) return null
        val request = userRepository.profilePhotoRequestByUrl("/uploads/profile-photos/$filename") ?: return null
        val viewer = userRepository.findById(viewerId) ?: return null
        val owner = userRepository.findById(request.userId) ?: return null
        val canReview = when (owner.role) {
            UserRole.END_USER -> viewer.role in setOf(UserRole.ADMIN, UserRole.SUPERADMIN)
            UserRole.ADMIN, UserRole.IT_ADMIN, UserRole.SUPPORT_AGENT, UserRole.ASSET_MANAGER, UserRole.AUDITOR ->
                viewer.role in setOf(UserRole.ADMIN, UserRole.IT_ADMIN, UserRole.SUPERADMIN)
            UserRole.SUPERADMIN -> viewer.role == UserRole.SUPERADMIN
        }
        val allowed = request.status == backend.models.ProfilePhotoStatus.APPROVED ||
            request.userId == viewerId ||
            (request.status == backend.models.ProfilePhotoStatus.PENDING && canReview)
        if (!allowed) return null
        val target = uploadDir.resolve(filename).normalize()
        return target.takeIf { it.parent == uploadDir && Files.isRegularFile(it) }
    }

    fun review(requestId: Int, reviewerId: Int, approved: Boolean, reason: String? = null): ProfilePhotoRequest {
        val sanitizedReason = reason?.trim()?.replace(Regex("[\\p{Cc}&&[^\\n\\t]]"), "")?.takeIf { it.isNotBlank() }
        require((sanitizedReason?.length ?: 0) <= 500) { "Reviewer explanation must be 500 characters or fewer." }
        val request = userRepository.profilePhotoRequestById(requestId) ?: error("Profile photo request was not found.")
        val owner = userRepository.findById(request.userId) ?: error("Profile photo owner was not found.")
        val reviewer = userRepository.findById(reviewerId) ?: throw SecurityException("Reviewer account was not found.")
        require(owner.id != reviewer.id) { "You cannot review your own profile photo." }
        val allowed = when (owner.role) {
            UserRole.END_USER -> reviewer.role in setOf(UserRole.ADMIN, UserRole.SUPERADMIN)
            UserRole.ADMIN, UserRole.IT_ADMIN, UserRole.SUPPORT_AGENT, UserRole.ASSET_MANAGER, UserRole.AUDITOR ->
                reviewer.role in setOf(UserRole.ADMIN, UserRole.IT_ADMIN, UserRole.SUPERADMIN)
            UserRole.SUPERADMIN -> reviewer.role == UserRole.SUPERADMIN
        }
        if (!allowed) throw SecurityException("You are not allowed to review this profile photo request.")
        val requestedStatus = if (approved) backend.models.ProfilePhotoStatus.APPROVED else backend.models.ProfilePhotoStatus.REJECTED
        if (request.status != backend.models.ProfilePhotoStatus.PENDING) {
            if (request.status != requestedStatus) error("Profile photo request was already reviewed with a different outcome.")
            pushReviewNotification(request, approved, sanitizedReason)
            return request
        }
        val reviewed = userRepository.reviewProfilePhotoRequest(requestId, reviewerId, approved)
            ?: error("Profile photo request was not found or was already reviewed.")
        pushReviewNotification(reviewed, approved, sanitizedReason)
        if (!approved) {
            val filename = request.photoUrl.substringAfterLast('/')
            val target = uploadDir.resolve(filename).normalize()
            if (target.parent == uploadDir) Files.deleteIfExists(target)
        }
        auditRepository.log(
            reviewerId,
            "${if (approved) "Approved" else "Rejected"} profile photo request ${reviewed.id}",
            "profile_photo_requests",
            target = "user:${reviewed.userId}",
            metadata = "requestId=${reviewed.id};outcome=${if (approved) "approved" else "rejected"}${sanitizedReason?.let { ";reason=$it" }.orEmpty()}"
        )
        return reviewed
    }

    private fun pushReviewNotification(request: ProfilePhotoRequest, approved: Boolean, reason: String?) {
        val standardRejection = "Your profile photo was rejected because it does not comply with the company’s code of conduct or ethics guidelines. Please upload a different photo."
        notificationService.push(
            userId = request.userId,
            title = if (approved) "Profile photo approved" else "Profile photo rejected",
            message = if (approved) "Your profile photo was approved and is now visible on your account."
                else listOfNotNull(standardRejection, reason?.let { "Reviewer explanation: $it" }).joinToString(" "),
            type = if (approved) "profile_photo_approved" else "profile_photo_rejected",
            relatedUserId = request.userId,
            eventKey = "profile-photo:${request.id}:${if (approved) "approved" else "rejected"}"
        )
    }

}

data class ProcessedImage(val extension: String, val bytes: ByteArray)

object SecureImageProcessor {
    private const val MAX_DIMENSION = 4096
    private const val MAX_PIXELS = 16_000_000L

    fun process(filename: String?, type: ContentType?, bytes: ByteArray): ProcessedImage {
        val ext = filename?.substringAfterLast('.', "")?.lowercase()
        val format = when {
            ext in setOf("jpg", "jpeg") && type?.withoutParameters() == ContentType.Image.JPEG &&
                bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
            ext == "png" && type?.withoutParameters() == ContentType.Image.PNG && bytes.size >= 8 &&
                bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) -> "png"
            else -> throw IllegalArgumentException("Only valid JPG and PNG images are allowed.")
        }
        val input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) ?: throw IllegalArgumentException("Invalid image")
        input.use { stream ->
            val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: throw IllegalArgumentException("Invalid image")
            try {
                reader.input = stream
                val width = reader.getWidth(0); val height = reader.getHeight(0)
                require(width in 1..MAX_DIMENSION && height in 1..MAX_DIMENSION && width.toLong() * height <= MAX_PIXELS) { "Image dimensions are too large." }
                val image = reader.read(0) ?: throw IllegalArgumentException("Invalid image")
                val output = ByteArrayOutputStream()
                check(ImageIO.write(image, if (format == "jpg") "jpeg" else "png", output)) { "Image format is unavailable" }
                return ProcessedImage(format, output.toByteArray())
            } finally { reader.dispose() }
        }
    }
}
