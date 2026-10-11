package backend.services

import backend.config.Env
import backend.models.InvitationAcceptRequest
import backend.models.InvitationView
import backend.models.InvitationInfo
import backend.models.User
import backend.models.UserRole
import backend.repository.AuditRepository
import backend.repository.InvitationRepository
import backend.repository.UserRepository
import backend.security.PasswordHasher
import java.util.UUID

class InvitationService(
    private val invitations: InvitationRepository,
    private val users: UserRepository,
    private val audit: AuditRepository,
    private val email: EmailService?
) {
    fun invite(address: String, role: UserRole, actor: Int, actorRole: UserRole): InvitationView {
        val normalized = address.trim().lowercase()
        require(normalized.contains('@')) { "Valid email is required" }
        require(users.findByEmailOnly(normalized) == null) { "Unable to create invitation" }
        require(role != UserRole.SUPERADMIN || actorRole == UserRole.SUPERADMIN) { "Only a system administrator can invite a system administrator" }
        val (view, raw) = invitations.create(normalized, role, actor, (Env.get("INVITATION_TTL_HOURS")?.toLongOrNull() ?: 24L))
        val url = "${(Env.get("APP_BASE_URL") ?: "http://127.0.0.1:8070").removeSuffix("/")}/signup?invite=$raw"
        val delivered = email?.sendInvitation(normalized, url) == true
        if (!delivered) {
            invitations.revoke(UUID.fromString(view.id))
            audit.log(actor, "Invitation email delivery failed for $normalized", "invitations", "FAILURE")
            error("Invitation email could not be sent. Verify the email delivery configuration and try again.")
        }
        audit.log(actor, "Created invitation for $normalized as ${role.name}", "invitations")
        return view
    }

    fun accept(request: InvitationAcceptRequest): User {
        require(request.eulaAccepted) { "EULA acceptance is required" }
        require(request.password.length >= 12 && request.password == request.confirmPassword) { "Password must be at least 12 characters and match confirmation" }
        require(request.fullName.isNotBlank() && request.department.isNotBlank()) { "Name and department are required" }
        val invitation = invitations.validate(request.token) ?: error("Invitation is invalid or expired")
        require(users.findByEmailOnly(invitation.email) == null) { "Invitation is invalid or expired" }
        val user = users.createInternalUser(
            request.fullName.trim(), invitation.email, Env.get("COMPANY_NAME") ?: "AITSM",
            request.department.trim(), PasswordHasher.hash(request.password), invitation.role, true
        )
        users.recordEulaAcceptance(user.id, request.eulaVersion)
        check(invitations.accept(invitation.id)) { "Invitation is invalid or expired" }
        audit.log(user.id, "Accepted invitation", "invitations")
        return user
    }

    fun list(): List<InvitationView> = invitations.list()
    fun inspect(token: String): InvitationInfo? = invitations.validate(token)?.let { InvitationInfo(it.email, it.role) }
    fun revoke(id: String, actor: Int) {
        require(invitations.revoke(UUID.fromString(id))) { "Invitation not found" }
        audit.log(actor, "Revoked invitation $id", "invitations")
    }
}
