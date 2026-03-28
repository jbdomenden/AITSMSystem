package backend.services

import backend.models.Ticket
import backend.models.TicketRequest
import backend.models.TicketStatus
import backend.models.UserRole
import backend.repository.AuditRepository
import backend.repository.TicketRepository
import backend.repository.UserRepository
import java.time.Duration
import java.time.LocalDateTime

class TicketService(
    private val repository: TicketRepository,
    private val audit: AuditRepository,
    private val notifications: NotificationService,
    private val users: UserRepository
) {
    private val followUpThresholdHours = 24L

    fun create(userId: Int, req: TicketRequest): Ticket = repository.create(userId, req).also {
        audit.log(userId, "Created ticket #${it.id}", "tickets")
        notifyAdmins("New ticket #${it.id} filed: ${it.title}", "info")
        notifications.push(userId, "Your ticket #${it.id} has been created.", "success")
    }
    fun list(userId: Int?, admin: Boolean, limit: Int, offset: Long) = repository.list(userId, admin, limit, offset)
    fun get(id: Int): Ticket? = repository.get(id)

    fun update(id: Int, req: TicketRequest, userId: Int?) = repository.update(id, req).also { audit.log(userId, "Updated ticket #$id", "tickets") }

    fun updateStatus(id: Int, statusValue: String, actor: String, userId: Int?, role: UserRole): Ticket? {
        val existing = repository.get(id) ?: return null
        val status = parseStatus(statusValue)
        val admin = role in setOf(UserRole.ADMIN, UserRole.SUPERADMIN)

        when {
            admin -> require(status in setOf(TicketStatus.OPEN, TicketStatus.PENDING, TicketStatus.RESOLVED, TicketStatus.CLOSED)) { "Unsupported status for admin" }
            else -> {
                require(existing.userId == (userId ?: -1)) { "You can only modify your own tickets" }
                when (status) {
                    TicketStatus.CLOSED -> require(existing.status == TicketStatus.RESOLVED) { "Only resolved tickets can be closed" }
                    TicketStatus.PENDING -> {
                        require(existing.status != TicketStatus.RESOLVED && existing.status != TicketStatus.CLOSED) { "Resolved/closed tickets cannot move to pending" }
                        val updatedAt = runCatching { LocalDateTime.parse(existing.updatedAt) }.getOrNull() ?: LocalDateTime.now()
                        val ageHours = Duration.between(updatedAt, LocalDateTime.now()).toHours()
                        require(ageHours >= followUpThresholdHours) { "Pending follow-up can be requested after $followUpThresholdHours hours without resolution" }
                    }
                    TicketStatus.OPEN, TicketStatus.RESOLVED -> error("Unsupported status for end-user")
                }
            }
        }

        return repository.updateStatus(id, status.name, actor)?.also {
            audit.log(userId, "Changed ticket #$id to ${status.name}", "tickets")
        }
    }

    private fun parseStatus(raw: String): TicketStatus {
        val normalized = raw.trim().lowercase()
        return when (normalized) {
            "open" -> TicketStatus.OPEN
            "in progress", "in-progress", "pending", "follow-up requested", "follow up requested" -> TicketStatus.PENDING
            "resolved" -> TicketStatus.RESOLVED
            "closed", "cancelled", "canceled" -> TicketStatus.CLOSED
            else -> throw IllegalArgumentException("Unsupported ticket status: $raw")
        }
    }

    private fun notifyAdmins(message: String, type: String) {
        val adminIds = users.listUsers()
            .filter { it.role == UserRole.ADMIN || it.role == UserRole.SUPERADMIN }
            .map { it.id }

        adminIds.forEach { notifications.push(it, message, type) }
    }
}
