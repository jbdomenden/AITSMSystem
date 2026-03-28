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
        notifyAdminsOncePerTicket(
            eventType = "ticket_created",
            ticketId = it.id,
            title = "New Ticket Created",
            message = "Ticket #${it.id} created by user #$userId"
        )
    }
    fun list(userId: Int?, admin: Boolean, limit: Int, offset: Long) = repository.list(userId, admin, limit, offset)
    fun get(id: Int): Ticket? = repository.get(id)

    fun update(id: Int, req: TicketRequest, userId: Int?) = repository.update(id, req).also { audit.log(userId, "Updated ticket #$id", "tickets") }

    fun updateStatus(id: Int, statusValue: String, actor: String, userId: Int?, role: UserRole): Ticket? {
        val existing = repository.get(id) ?: return null
        val requestedAction = statusValue.trim().lowercase()
        val status = parseStatus(statusValue)
        val admin = role in setOf(UserRole.ADMIN, UserRole.SUPERADMIN)
        if (existing.status == status) return existing

        when {
            admin -> {
                require(status in setOf(TicketStatus.OPEN, TicketStatus.PENDING, TicketStatus.RESOLVED, TicketStatus.CLOSED)) { "Unsupported status for admin" }
                require(requestedAction !in setOf("cancelled", "canceled", "cancel")) { "Admin cannot cancel tickets" }
            }
            else -> {
                require(existing.userId == (userId ?: -1)) { "You can only modify your own tickets" }
                when (status) {
                    TicketStatus.CLOSED -> {
                        if (requestedAction in setOf("cancelled", "canceled", "cancel")) {
                            require(existing.status in setOf(TicketStatus.OPEN, TicketStatus.PENDING)) { "Only open or pending tickets can be cancelled" }
                        } else {
                            require(existing.status == TicketStatus.RESOLVED) { "Only resolved tickets can be closed" }
                        }
                    }
                    TicketStatus.PENDING -> {
                        require(existing.status != TicketStatus.RESOLVED && existing.status != TicketStatus.CLOSED) { "Resolved/closed tickets cannot move to pending" }
                        val updatedAt = runCatching { LocalDateTime.parse(existing.updatedAt) }.getOrNull() ?: LocalDateTime.now()
                        val ageHours = Duration.between(updatedAt, LocalDateTime.now()).toHours()
                        require(ageHours >= followUpThresholdHours) { "Pending follow-up can be requested after $followUpThresholdHours hours without resolution" }
                    }
                    TicketStatus.OPEN, TicketStatus.RESOLVED -> error("Unsupported status for end-user")
                }

                if (requestedAction in setOf("closed", "close")) {
                    require(existing.status == TicketStatus.RESOLVED) { "Only resolved tickets can be closed" }
                }
            }
        }

        return repository.updateStatus(id, status.name, actor)?.also { updated ->
            audit.log(userId, "Changed ticket #$id to ${status.name}", "tickets")
            if (admin) {
                val displayStatus = status.name.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }
                notifications.pushOncePerTicketEvent(
                    userId = updated.userId,
                    eventType = "ticket_status_updated_${status.name.lowercase()}",
                    ticketId = updated.id,
                    title = "Ticket Status Updated",
                    message = "Ticket #${updated.id} updated to $displayStatus"
                )
            }
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

    private fun notifyAdminsOncePerTicket(
        eventType: String,
        ticketId: Int,
        title: String,
        message: String
    ) {
        val adminIds = users.listUsers()
            .filter { it.role == UserRole.ADMIN || it.role == UserRole.SUPERADMIN }
            .map { it.id }

        adminIds.forEach {
            notifications.pushOncePerTicketEvent(
                userId = it,
                eventType = eventType,
                ticketId = ticketId,
                title = title,
                message = message
            )
        }
    }
}
