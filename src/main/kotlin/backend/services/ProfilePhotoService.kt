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

class ProfilePhotoService(
    private val userRepository: UserRepository,
    private val auditRepository: AuditRepository
) {
    private val uploadDir: Path = Path.of(Env.get("PROFILE_UPLOAD_DIR") ?: "uploads/profile-photos")
        .toAbsolutePath().normalize()
    private val maxBytes = 5L * 1024 * 1024

    init { Files.createDirectories(uploadDir) }

    fun submit(userId: Int, originalFileName: String?, contentType: ContentType?, bytes: ByteArray): ProfilePhotoRequest {
        require(!userRepository.hasPendingProfilePhoto(userId)) { "A profile photo is already waiting for admin approval." }
        require(bytes.isNotEmpty()) { "Please choose an image to upload." }
        require(bytes.size.toLong() <= maxBytes) { "Image must be 5 MB or smaller." }
        val extension = validExtension(originalFileName, contentType, bytes)
            ?: throw IllegalArgumentException("Only JPG, PNG, and WEBP images are allowed.")
        val filename = "${UUID.randomUUID()}.$extension"
        val target = uploadDir.resolve(filename).normalize()
        check(target.parent == uploadDir) { "Invalid upload path" }
        try {
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW)
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

    fun review(requestId: Int, reviewerId: Int, approved: Boolean): ProfilePhotoRequest {
        val request = pending().firstOrNull { it.id == requestId } ?: error("Profile photo request was not found or was already reviewed.")
        val owner = userRepository.findById(request.userId) ?: error("Profile photo owner was not found.")
        val reviewer = userRepository.findById(reviewerId) ?: throw SecurityException("Reviewer account was not found.")
        require(owner.id != reviewer.id) { "You cannot review your own profile photo." }
        val allowed = when (owner.role) {
            UserRole.END_USER -> reviewer.role in setOf(UserRole.ADMIN, UserRole.SUPERADMIN)
            UserRole.ADMIN -> reviewer.role in setOf(UserRole.ADMIN, UserRole.SUPERADMIN)
            UserRole.SUPERADMIN -> reviewer.role == UserRole.SUPERADMIN
        }
        if (!allowed) throw SecurityException("You are not allowed to review this profile photo request.")
        val reviewed = userRepository.reviewProfilePhotoRequest(requestId, reviewerId, approved)
            ?: error("Profile photo request was not found or was already reviewed.")
        auditRepository.log(reviewerId, "${if (approved) "Approved" else "Rejected"} profile photo request ${reviewed.id}", "profile_photo_requests")
        return reviewed
    }

    private fun validExtension(filename: String?, type: ContentType?, bytes: ByteArray): String? {
        val nameExtension = filename?.substringAfterLast('.', "")?.lowercase()
        val allowed = mapOf("jpg" to "jpg", "jpeg" to "jpg", "png" to "png", "webp" to "webp")
        val mimeAllowed = type?.withoutParameters() in setOf(ContentType.Image.JPEG, ContentType.Image.PNG, ContentType("image", "webp"))
        val magicExtension = when {
            bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "jpg"
            bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) -> "png"
            bytes.size >= 12 && String(bytes.copyOfRange(0, 4)) == "RIFF" && String(bytes.copyOfRange(8, 12)) == "WEBP" -> "webp"
            else -> null
        }
        return if (mimeAllowed && allowed[nameExtension] == magicExtension) magicExtension else null
    }
}
