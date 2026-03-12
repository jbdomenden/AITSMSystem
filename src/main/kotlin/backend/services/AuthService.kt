package backend.services

import backend.models.AuthResponse
import backend.models.LoginRequest
import backend.models.RegisterRequest
import backend.repository.AuditRepository
import backend.repository.UserRepository
import backend.security.PasswordHasher
import java.util.Base64

class AuthService(
    private val userRepository: UserRepository,
    private val auditRepository: AuditRepository
) {
    fun register(request: RegisterRequest): AuthResponse {
        require(request.password == request.confirmPassword) { "Passwords do not match." }
        require(request.eulaAccepted) { "EULA acceptance is required." }
        val user = userRepository.create(request, PasswordHasher.hash(request.password), role = "end-user")
        auditRepository.log(user.id, "User registration", "users")
        return AuthResponse(token = tokenFor(user.id, user.role), user = user)
    }

    fun login(request: LoginRequest): AuthResponse {
        val (user, hash) = userRepository.findByEmail(request.email) ?: error("Invalid credentials")
        require(PasswordHasher.verify(request.password, hash)) { "Invalid credentials" }
        auditRepository.log(user.id, "User login", "auth")
        return AuthResponse(token = tokenFor(user.id, user.role), user = user)
    }

    private fun tokenFor(userId: Int, role: String): String = Base64.getEncoder().encodeToString("$userId:$role".toByteArray())
}
