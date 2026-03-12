package backend.services

import backend.models.AuthResponse
import backend.models.LoginRequest
import backend.models.RegisterRequest
import backend.models.RegistrationResponse
import backend.models.User
import backend.repository.AuditRepository
import backend.repository.UserRepository
import backend.security.PasswordHasher
import java.time.LocalDateTime
import java.util.Base64

class AuthService(
    private val userRepository: UserRepository,
    private val auditRepository: AuditRepository
) {
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
            devVerificationCode = verificationCode
        )
    }

    fun verifyEmail(email: String, code: String): AuthResponse {
        val user = userRepository.verifyEmail(email, code) ?: error("Invalid or expired verification code")
        auditRepository.log(user.id, "Email verified", "users")
        return AuthResponse(token = tokenFor(user.id, user.role), user = user)
    }

    fun resendVerification(email: String): RegistrationResponse {
        val code = generateVerificationCode()
        val updated = userRepository.regenerateVerificationCode(email, code, LocalDateTime.now().plusMinutes(15))
        require(updated) { "Unable to resend verification code. Email may already be verified or missing." }

        return RegistrationResponse(
            message = "Verification code regenerated.",
            email = email.lowercase(),
            devVerificationCode = code
        )
    }

    fun login(request: LoginRequest): AuthResponse {
        val (user, hash) = userRepository.findByEmail(request.email) ?: error("Invalid credentials")
        require(PasswordHasher.verify(request.password, hash)) { "Invalid credentials" }
        require(user.emailVerified || user.role == "admin" || user.role == "superadmin") {
            "Please verify your email before logging in."
        }

        auditRepository.log(user.id, "User login", "auth")
        return AuthResponse(token = tokenFor(user.id, user.role), user = user)
    }

    fun listUsers(): List<User> = userRepository.listUsers()

    fun updateUserRole(targetUserId: Int, role: String, actorUserId: Int?): User {
        require(role in setOf("admin", "end-user")) { "Unsupported role" }
        val updated = userRepository.updateRole(targetUserId, role) ?: error("User not found or cannot be modified")
        auditRepository.log(actorUserId, "Updated role for user ${updated.email} to $role", "users")
        return updated
    }

    private fun generateVerificationCode(): String = (100000..999999).random().toString()

    private fun tokenFor(userId: Int, role: String): String = Base64.getEncoder().encodeToString("$userId:$role".toByteArray())
}
