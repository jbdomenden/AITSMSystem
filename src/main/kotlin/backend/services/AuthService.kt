package backend.services

import backend.models.AdminEligibilityResponse
import backend.models.AdminGrantResponse
import backend.models.AuthResponse
import backend.models.LoginRequest
import backend.models.InternalUserCreateRequest
import backend.models.ProfileUpdateRequest
import backend.models.RegisterRequest
import backend.models.RegistrationResponse
import backend.models.User
import backend.repository.AuditRepository
import backend.repository.UserRepository
import backend.security.PasswordHasher
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AuthService(
    private val userRepository: UserRepository,
    private val auditRepository: AuditRepository
) {
    private data class SensitiveVerification(val userId: Int, val expiresAt: LocalDateTime)
    private val sensitiveVerifications = ConcurrentHashMap<String, SensitiveVerification>()
    private val exposeDevVerificationCode = ((System.getenv("AUTH_EXPOSE_DEV_VERIFICATION_CODE") ?: "false").lowercase() == "true")

    fun register(request: RegisterRequest): RegistrationResponse {
        require(request.password == request.confirmPassword) { "Passwords do not match." }
        require(request.eulaAccepted) { "EULA acceptance is required." }

        val verificationCode = generateVerificationCode()
        val expiresAt = LocalDateTime.now().plusMinutes(15)
        val user = userRepository.create(
            request,
            PasswordHasher.hash(request.password),
            role = "end-user",
            emailVerified = false,
            verificationCode = verificationCode,
            verificationExpiry = expiresAt
        )
        auditRepository.log(user.id, "User registration (email pending verification)", "users")

        return RegistrationResponse(
            message = "Account created. Verify your email before logging in.",
            email = user.email,
            devVerificationCode = if (exposeDevVerificationCode) verificationCode else null
        )
    }

    fun verifyEmail(email: String, code: String): AuthResponse {
        val user = userRepository.verifyEmail(email.trim(), code.trim()) ?: error("Invalid or expired verification code")
        auditRepository.log(user.id, "Email verified", "users")
        return AuthResponse(token = tokenFor(user.id, user.role), user = user)
    }

    fun resendVerification(email: String): RegistrationResponse {
        val normalizedEmail = email.trim()
        val code = generateVerificationCode()
        val updated = userRepository.regenerateVerificationCode(normalizedEmail, code, LocalDateTime.now().plusMinutes(15))
        require(updated) { "Unable to resend verification code. Email may already be verified or missing." }

        return RegistrationResponse(
            message = "Verification code regenerated.",
            email = normalizedEmail.lowercase(),
            devVerificationCode = if (exposeDevVerificationCode) code else null
        )
    }

    fun login(request: LoginRequest): AuthResponse {
        val normalizedEmail = request.email.trim()
        val (user, hash) = userRepository.findByEmail(normalizedEmail) ?: error("Invalid credentials")
        require(PasswordHasher.verify(request.password, hash)) { "Invalid credentials" }
        require(user.emailVerified || user.role == "admin" || user.role == "superadmin") {
            "Please verify your email before logging in."
        }

        auditRepository.log(user.id, "User login", "auth")
        return AuthResponse(token = tokenFor(user.id, user.role), user = user)
    }

    fun listUsers(): List<User> = userRepository.listUsers()

    fun currentUser(userId: Int): User = userRepository.findById(userId) ?: error("User not found")

    fun updateOwnProfile(userId: Int, req: ProfileUpdateRequest): User {
        require(req.fullName.isNotBlank()) { "Full name is required" }
        require(req.company.isNotBlank()) { "Company is required" }
        require(req.department.isNotBlank()) { "Department is required" }

        val updated = userRepository.updateProfile(userId, req.fullName.trim(), req.company.trim(), req.department.trim())
            ?: error("Unable to update profile")
        auditRepository.log(userId, "Updated own profile", "users")
        return updated
    }

    fun resetUserPassword(targetUserId: Int, newPassword: String, confirmPassword: String, actorUserId: Int?): User {
        require(newPassword == confirmPassword) { "Passwords do not match" }
        require(newPassword.length >= 8) { "Password must be at least 8 characters" }

        val updated = userRepository.updatePassword(targetUserId, PasswordHasher.hash(newPassword))
            ?: error("User not found or cannot be modified")
        auditRepository.log(actorUserId, "Reset password for ${updated.email}", "users")
        return updated
    }

    fun deleteUserAccount(targetUserId: Int, actorUserId: Int?): User {
        require(actorUserId != targetUserId) { "You cannot delete your own account" }
        val deleted = userRepository.deleteUser(targetUserId) ?: error("User not found or cannot be deleted")
        auditRepository.log(actorUserId, "Deleted user ${deleted.email}", "users")
        return deleted
    }

    fun updateUserRole(targetUserId: Int, role: String, actorUserId: Int?): User {
        require(role in setOf("admin", "end-user")) { "Unsupported role" }
        if (role == "admin") error("Direct admin grant disabled. Use secure admin-grant flow.")

        val updated = userRepository.updateRole(targetUserId, role) ?: error("User not found or cannot be modified")
        auditRepository.log(actorUserId, "Updated role for user ${updated.email} to $role", "users")
        return updated
    }

    fun updateUserEmailApproval(targetUserId: Int, approved: Boolean, actorUserId: Int?): User {
        val updated = userRepository.updateEmailVerified(targetUserId, approved)
            ?: error("User not found or cannot be modified")
        val state = if (approved) "approved" else "set to pending verification"
        auditRepository.log(actorUserId, "Email $state for ${updated.email}", "users")
        return updated
    }

    fun createUserInternally(request: InternalUserCreateRequest, actorUserId: Int?): User {
        require(request.fullName.isNotBlank()) { "Full name is required" }
        require(request.email.isNotBlank()) { "Email is required" }
        require(request.company.isNotBlank()) { "Company is required" }
        require(request.department.isNotBlank()) { "Department is required" }
        require(request.password == request.confirmPassword) { "Passwords do not match" }
        require(request.password.length >= 8) { "Password must be at least 8 characters" }
        require(request.role in setOf("end-user", "admin")) { "Unsupported role" }

        val normalizedEmail = request.email.trim().lowercase()
        require(userRepository.findByEmailOnly(normalizedEmail) == null) { "Email already exists" }

        val created = userRepository.createInternalUser(
            fullName = request.fullName.trim(),
            email = normalizedEmail,
            company = request.company.trim(),
            department = request.department.trim(),
            passwordHash = PasswordHasher.hash(request.password),
            role = request.role,
            emailVerified = request.emailVerified
        )
        auditRepository.log(actorUserId, "Created internal user ${created.email} (${created.role})", "users")
        return created
    }

    fun adminGrantEligibility(targetEmail: String): AdminEligibilityResponse {
        val user = userRepository.findByEmailOnly(targetEmail)
            ?: return AdminEligibilityResponse(found = false, eligible = false, alreadyAdmin = false, message = "User not found")

        if (user.role == "admin" || user.role == "superadmin") {
            return AdminEligibilityResponse(found = true, eligible = false, alreadyAdmin = true, targetUserId = user.id, message = "Target already has admin rights")
        }

        return AdminEligibilityResponse(found = true, eligible = true, alreadyAdmin = false, targetUserId = user.id, message = "Target is eligible for admin role")
    }

    fun verifySensitiveAction(actorUserId: Int, password: String): Triple<Boolean, String?, String> {
        val hash = userRepository.findHashById(actorUserId) ?: return Triple(false, null, "Acting admin not found")
        if (!PasswordHasher.verify(password, hash)) return Triple(false, null, "Verification failed: password incorrect")

        val token = UUID.randomUUID().toString()
        val expiresAt = LocalDateTime.now().plusMinutes(3)
        sensitiveVerifications[token] = SensitiveVerification(actorUserId, expiresAt)
        return Triple(true, token, expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
    }

    fun grantAdminByEmail(targetEmail: String, actorUserId: Int, verificationToken: String): AdminGrantResponse {
        val verified = sensitiveVerifications[verificationToken]
        if (verified == null || verified.userId != actorUserId || verified.expiresAt.isBefore(LocalDateTime.now())) {
            sensitiveVerifications.remove(verificationToken)
            return AdminGrantResponse(false, null, "Sensitive-action verification expired or invalid")
        }

        val eligibility = adminGrantEligibility(targetEmail)
        if (!eligibility.found) return AdminGrantResponse(false, null, "Target email not found")
        if (!eligibility.eligible) return AdminGrantResponse(false, null, eligibility.message)

        val updated = userRepository.updateRoleByEmail(targetEmail, "admin")
            ?: return AdminGrantResponse(false, null, "Unable to grant admin role")

        sensitiveVerifications.remove(verificationToken)
        auditRepository.log(actorUserId, "Granted admin role to ${updated.email}", "users")
        return AdminGrantResponse(true, updated, "Admin role granted successfully")
    }

    private fun generateVerificationCode(): String = (100000..999999).random().toString()
    private fun tokenFor(userId: Int, role: String): String = Base64.getEncoder().encodeToString("$userId:$role".toByteArray())
}
