package backend.services

import backend.config.Env
import backend.models.AdminEligibilityResponse
import backend.models.AdminGrantResponse
import backend.models.AuthResponse
import backend.models.LoginRequest
import backend.models.InternalUserCreateRequest
import backend.models.ProfileUpdateRequest
import backend.models.RegisterRequest
import backend.models.RegistrationResponse
import backend.models.User
import backend.models.UserRole
import backend.models.AccountState
import backend.models.AuditLog
import backend.repository.AuditRepository
import backend.repository.UserRepository
import backend.repository.PasswordResetRepository
import backend.security.PasswordHasher
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

class AuthService(
    private val userRepository: UserRepository,
    private val auditRepository: AuditRepository,
    private val notificationService: NotificationService? = null,
    private val emailService: EmailService? = null,
    private val sessions: SessionService? = null,
    private val passwordResets: PasswordResetRepository? = null,
    private val mfa: MfaService? = null
) {
    private data class SensitiveVerification(val userId: Int, val expiresAt: LocalDateTime)
    private val sensitiveVerifications = ConcurrentHashMap<String, SensitiveVerification>()
    private val exposeDevVerificationCode = ((Env.get("AUTH_EXPOSE_DEV_VERIFICATION_CODE") ?: "false").lowercase() == "true")
    private val emailPattern = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")

    fun register(request: RegisterRequest): RegistrationResponse {
        require(request.fullName.trim().isNotBlank()) { "Full name is required." }
        require(request.company.trim().isNotBlank()) { "Company is required." }
        require(request.department.trim().isNotBlank()) { "Department is required." }
        requireValidEmail(request.email)
        require(request.password.length >= 8) { "Password must be at least 8 characters." }
        require(request.password == request.confirmPassword) { "Passwords do not match." }
        require(request.eulaAccepted) { "EULA acceptance is required." }

        val verificationCode = generateVerificationCode()
        val expiresAt = LocalDateTime.now().plusMinutes(15)
        val user = userRepository.create(
            request,
            PasswordHasher.hash(request.password),
            role = UserRole.END_USER,
            emailVerified = false,
            verificationCode = SessionService.sha256(verificationCode),
            verificationExpiry = expiresAt
        )
        auditRepository.log(user.id, "User registration (email pending verification)", "users")
        val emailDelivered = emailService?.sendVerificationCode(user.email, user.fullName, verificationCode) == true

        return RegistrationResponse(
            message = when {
                emailDelivered -> "Account created. We sent a verification code to your email."
                emailService == null -> "Account created, but email delivery is not configured."
                else -> "Account created, but the verification email could not be delivered. Use Resend Code to try again."
            },
            email = user.email,
            devVerificationCode = if (exposeDevVerificationCode) verificationCode else null
        )
    }

    fun verifyEmail(email: String, code: String): AuthResponse {
        requireValidEmail(email)
        val user = userRepository.verifyEmail(email.trim(), SessionService.sha256(code.trim())) ?: error("Invalid or expired verification code")
        auditRepository.log(user.id, "Email verified", "users")
        val administrator = user.role in setOf(UserRole.ADMIN, UserRole.IT_ADMIN, UserRole.SUPERADMIN)
        if (administrator && mfa?.isEnabled(user.id) != true) {
            return AuthResponse(user, mfaEnrollmentRequired = true)
        }
        if (mfa?.isEnabled(user.id) == true) {
            return AuthResponse(user, mfaRequired = true)
        }
        return AuthResponse(user)
    }

    fun resendVerification(email: String): RegistrationResponse {
        val normalizedEmail = email.trim()
        requireValidEmail(normalizedEmail)
        val code = generateVerificationCode()
        val updated = userRepository.regenerateVerificationCode(normalizedEmail, SessionService.sha256(code), LocalDateTime.now().plusMinutes(15))
        require(updated) { "Unable to resend verification code. Email may already be verified or missing." }
        val user = userRepository.findByEmailOnly(normalizedEmail)
        val emailDelivered = emailService?.sendVerificationCode(
            recipient = normalizedEmail.lowercase(),
            recipientName = user?.fullName,
            code = code
        ) == true

        return RegistrationResponse(
            message = when {
                emailDelivered -> "A new verification code was sent to your email."
                emailService == null -> "Verification code regenerated, but email delivery is not configured."
                else -> "A new code was generated, but the verification email could not be delivered. Please try again."
            },
            email = normalizedEmail.lowercase(),
            devVerificationCode = if (exposeDevVerificationCode) code else null
        )
    }

    fun login(request: LoginRequest): AuthResponse {
        val normalizedEmail = request.email.trim()
        requireValidEmail(normalizedEmail)
        require(request.password.isNotBlank()) { "Password is required" }
        if (userRepository.isLoginLocked(normalizedEmail)) {
            auditRepository.log(null, "Password login while account locked", "auth", "FAILURE", target = normalizedEmail)
            error("Invalid credentials")
        }
        val pair = userRepository.findByEmail(normalizedEmail)
        if (pair == null || !PasswordHasher.verify(request.password, pair.second)) {
            val attempts = userRepository.recordLoginFailure(normalizedEmail)
            if (attempts != null && attempts >= 5) {
                userRepository.findByEmailOnly(normalizedEmail)?.let {
                    emailService?.sendSecurityNotice(it.email, "AITSM account temporarily locked", "Multiple unsuccessful sign-in attempts temporarily locked your account.")
                }
            }
            auditRepository.log(pair?.first?.id, "Password login", "auth", "FAILURE", target = normalizedEmail)
            error("Invalid credentials")
        }
        val (user, _) = pair
        require(user.emailVerified && user.accountState == AccountState.ACTIVE) { "Invalid credentials" }
        userRepository.recordLoginSuccess(user.id)

        auditRepository.log(user.id, "User login", "auth")
        val administrator = user.role in setOf(UserRole.ADMIN, UserRole.IT_ADMIN, UserRole.SUPERADMIN)
        if (administrator && mfa?.isEnabled(user.id) != true) return AuthResponse(user, mfaEnrollmentRequired = true)
        if (mfa?.isEnabled(user.id) == true) {
            if (request.mfaCode.isNullOrBlank() && request.recoveryCode.isNullOrBlank()) return AuthResponse(user, mfaRequired = true)
            require(mfa.verifyLogin(user.id, request.mfaCode, request.recoveryCode)) { "Invalid credentials" }
        }
        emailService?.sendSecurityNotice(user.email, "New AITSM sign-in", "A new session signed in to your AITSM account.")
        return AuthResponse(user)
    }

    fun approveExternalAccount(userId: Int, department: String, role: UserRole, actorUserId: Int?): User {
        require(department.isNotBlank()) { "Department is required" }
        require(role in setOf(UserRole.END_USER, UserRole.ADMIN)) { "Unsupported access role" }
        val updated = userRepository.approveExternalAccount(userId, department.trim(), role)
            ?: error("User not found or cannot be approved")
        auditRepository.log(actorUserId, "Approved external account ${updated.email} as ${role.name} in ${updated.department}", "users")
        emailService?.sendSecurityNotice(updated.email, if (role == UserRole.ADMIN) "AITSM administrator account activated" else "AITSM account activated", if (role == UserRole.ADMIN) "Your account was approved with administrator access. MFA enrollment is required at first sign-in." else "Your account was approved for access.")
        return updated
    }

    fun listUsers(): List<User> = userRepository.listUsers()

    fun currentUser(userId: Int): User = userRepository.findById(userId) ?: error("User not found")

    fun recentSecurityEvents(userId: Int): List<AuditLog> = auditRepository.recentForActor(userId)

    fun notifySecurityEvent(userId: Int, subject: String, message: String) {
        userRepository.findById(userId)?.let { emailService?.sendSecurityNotice(it.email, subject, message) }
    }

    fun verifyReauthentication(userId: Int, password: String?, mfaCode: String?, recoveryCode: String?): Boolean {
        val passwordValid = !password.isNullOrBlank() && userRepository.findHashById(userId)?.let { PasswordHasher.verify(password, it) } == true
        val mfaValid = (!mfaCode.isNullOrBlank() || !recoveryCode.isNullOrBlank()) && mfa?.verifyLogin(userId, mfaCode, recoveryCode) == true
        val verified = passwordValid || mfaValid
        auditRepository.log(userId, "Sensitive action reauthentication", "auth", if (verified) "SUCCESS" else "FAILURE")
        return verified
    }

    fun updateOwnProfile(userId: Int, req: ProfileUpdateRequest): User {
        require(req.fullName.isNotBlank()) { "Full name is required" }
        require(req.department.isNotBlank()) { "Department is required" }

        val existing = userRepository.findById(userId) ?: error("User not found")
        val updated = userRepository.updateProfile(userId, req.fullName.trim(), existing.company, req.department.trim())
            ?: error("Unable to update profile")
        auditRepository.log(userId, "Updated own profile", "users")
        return updated
    }



    fun changeOwnPassword(userId: Int, currentPassword: String, newPassword: String, confirmPassword: String): User {
        require(newPassword == confirmPassword) { "Passwords do not match" }
        require(newPassword.length >= 8) { "Password must be at least 8 characters" }

        val currentHash = userRepository.findHashById(userId) ?: error("User not found")
        require(PasswordHasher.verify(currentPassword, currentHash)) { "Current password is incorrect" }

        val updated = userRepository.updateOwnPassword(userId, PasswordHasher.hash(newPassword))
            ?: error("Unable to update password")
        auditRepository.log(userId, "Changed own password", "users")
        sessions?.revokeAll(userId)
        emailService?.sendPasswordChanged(updated.email)
        return updated
    }

    fun resetUserPassword(targetUserId: Int, newPassword: String, confirmPassword: String, actorUserId: Int?): User {
        require(newPassword == confirmPassword) { "Passwords do not match" }
        require(newPassword.length >= 8) { "Password must be at least 8 characters" }

        val updated = userRepository.updatePassword(targetUserId, PasswordHasher.hash(newPassword))
            ?: error("User not found or cannot be modified")
        auditRepository.log(actorUserId, "Reset password for ${updated.email}", "users")
        sessions?.revokeAll(targetUserId)
        emailService?.sendPasswordChanged(updated.email)
        return updated
    }

    fun deleteUserAccount(targetUserId: Int, actorUserId: Int?): User {
        require(actorUserId != targetUserId) { "You cannot delete your own account" }
        val deleted = userRepository.deleteUser(targetUserId) ?: error("User not found or cannot be deleted")
        auditRepository.log(actorUserId, "Deleted user ${deleted.email}", "users")
        return deleted
    }

    fun updateUserRole(targetUserId: Int, role: UserRole, actorUserId: Int?): User {
        require(role == UserRole.END_USER) { "Direct admin grant disabled. Use secure admin-grant flow." }

        val updated = userRepository.updateRole(targetUserId, role) ?: error("User not found or cannot be modified")
        auditRepository.log(actorUserId, "Updated role for user ${updated.email} to ${role.name}", "users")
        sessions?.revokeAll(targetUserId)
        emailService?.sendSecurityNotice(updated.email, "AITSM role changed", "Your account role changed to ${role.name} and existing sessions were revoked.")
        return updated
    }

    fun resetAdministratorMfa(targetUserId: Int, actorUserId: Int, actorRole: UserRole): User {
        require(actorRole == UserRole.SUPERADMIN) { "Only a system administrator can reset administrator MFA" }
        require(targetUserId != actorUserId) { "Use your recovery code or the documented break-glass process for your own account" }
        val target = userRepository.findById(targetUserId) ?: error("User not found")
        require(target.role in setOf(UserRole.ADMIN, UserRole.IT_ADMIN)) { "MFA recovery is limited to administrator accounts" }
        require(mfa?.isEnabled(targetUserId) == true) { "MFA is not enabled for this administrator" }
        mfa.disable(targetUserId)
        sessions?.revokeAll(targetUserId)
        auditRepository.log(actorUserId, "Reset MFA for administrator ${target.email}; all sessions revoked", "auth")
        emailService?.sendSecurityNotice(target.email, "AITSM MFA recovery performed", "A system administrator reset MFA and revoked all active sessions for your account. You must enroll MFA again at next sign-in.")
        return target
    }

    fun forgotPassword(email: String) {
        val user = userRepository.findByEmailOnly(email.trim()) ?: return
        if (user.accountState != AccountState.ACTIVE) return
        val raw = passwordResets?.create(user.id) ?: return
        val url = "${(Env.get("APP_BASE_URL") ?: "http://127.0.0.1:8070").removeSuffix("/")}/login.html?reset=$raw"
        emailService?.sendPasswordReset(user.email, url)
        auditRepository.log(user.id, "Password reset requested", "auth")
    }

    fun completePasswordReset(token: String, newPassword: String, confirmation: String): User {
        require(newPassword.length >= 12 && newPassword == confirmation) { "Password must be at least 12 characters and match confirmation" }
        val userId = passwordResets?.consume(token) ?: error("Reset link is invalid or expired")
        val user = userRepository.updateOwnPassword(userId, PasswordHasher.hash(newPassword)) ?: error("Reset link is invalid or expired")
        sessions?.revokeAll(userId)
        emailService?.sendPasswordChanged(user.email)
        auditRepository.log(userId, "Password reset completed", "auth")
        return user
    }

    fun updateAccountState(targetUserId: Int, state: AccountState, actorUserId: Int?): User {
        require(state in setOf(AccountState.ACTIVE, AccountState.SUSPENDED, AccountState.DISABLED)) { "Unsupported account state transition" }
        require(targetUserId != actorUserId || state == AccountState.ACTIVE) { "You cannot deactivate your own account" }
        val updated = userRepository.updateAccountState(targetUserId, state) ?: error("User not found or cannot be modified")
        if (state != AccountState.ACTIVE) sessions?.revokeAll(targetUserId)
        auditRepository.log(actorUserId, "Changed account state for ${updated.email} to ${state.name}", "users")
        emailService?.sendSecurityNotice(updated.email, "AITSM account status changed", "Your account status changed to ${state.name}.")
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
        requireValidEmail(request.email)
        require(request.password == request.confirmPassword) { "Passwords do not match" }
        require(request.password.length >= 8) { "Password must be at least 8 characters" }
        require(request.role in setOf(UserRole.END_USER, UserRole.ADMIN)) { "Unsupported role" }

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
        if (created.role in setOf(UserRole.ADMIN, UserRole.IT_ADMIN)) emailService?.sendSecurityNotice(created.email, "AITSM administrator account activated", "An administrator account was created for you. MFA enrollment is required at first sign-in.")
        return created
    }

    fun adminGrantEligibility(targetEmail: String): AdminEligibilityResponse {
        val user = userRepository.findByEmailOnly(targetEmail)
            ?: return AdminEligibilityResponse(found = false, eligible = false, alreadyAdmin = false, message = "User not found")

        if (user.role == UserRole.ADMIN || user.role == UserRole.SUPERADMIN) {
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

        val updated = userRepository.updateRoleByEmail(targetEmail, UserRole.ADMIN)
            ?: return AdminGrantResponse(false, null, "Unable to grant admin role")

        sensitiveVerifications.remove(verificationToken)
        auditRepository.log(actorUserId, "Granted admin role to ${updated.email}", "users")
        sessions?.revokeAll(updated.id)
        emailService?.sendSecurityNotice(updated.email, "AITSM administrator access granted", "Your account was granted administrator access. Existing sessions were revoked and MFA is required.")
        return AdminGrantResponse(true, updated, "Admin role granted successfully")
    }

    private fun generateVerificationCode(): String = (SecureRandom().nextInt(900000) + 100000).toString()
    private fun requireValidEmail(email: String) {
        val normalized = email.trim()
        require(normalized.isNotBlank()) { "Email is required" }
        require(emailPattern.matches(normalized)) { "Invalid email format" }
    }
}
