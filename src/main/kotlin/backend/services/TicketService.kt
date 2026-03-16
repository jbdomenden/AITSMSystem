package backend.services

import backend.models.Ticket
import backend.models.TicketRequest
import backend.repository.AuditRepository
import backend.repository.TicketRepository
import java.time.Duration
import java.time.LocalDateTime

class TicketService(private val repository: TicketRepository, private val audit: AuditRepository) {
    private val followUpThresholdHours = 24L

    fun create(userId: Int, req: TicketRequest): Ticket = repository.create(userId, req).also { audit.log(userId, "Created ticket #${it.id}", "tickets") }
    fun list(userId: Int?, admin: Boolean): List<Ticket> = repository.list(userId, admin)
    fun get(id: Int): Ticket? = repository.get(id)

    fun update(id: Int, req: TicketRequest, userId: Int?) = repository.update(id, req).also { audit.log(userId, "Updated ticket #$id", "tickets") }

    fun updateStatus(id: Int, status: String, actor: String, userId: Int?, role: String): Ticket? {
        val existing = repository.get(id) ?: return null
        val admin = role in setOf("admin", "superadmin")

        when {
            admin -> require(status in setOf("Open", "In Progress", "Resolved", "Cancelled")) { "Unsupported status for admin" }
            else -> {
                require(existing.userId == (userId ?: -1)) { "You can only modify your own tickets" }
                when (status) {
                    "Closed" -> require(existing.status == "Resolved") { "Only resolved tickets can be closed" }
                    "Cancelled" -> require(existing.status in setOf("Open", "In Progress", "Follow-up Requested")) { "Ticket cannot be cancelled in current status" }
                    "Follow-up Requested" -> {
                        require(existing.status !in setOf("Resolved", "Closed", "Cancelled")) { "Resolved/closed/cancelled tickets cannot be followed up" }
                        val updatedAt = runCatching { LocalDateTime.parse(existing.updatedAt) }.getOrNull() ?: LocalDateTime.now()
                        val ageHours = Duration.between(updatedAt, LocalDateTime.now()).toHours()
                        require(ageHours >= followUpThresholdHours) { "Follow-up can be requested after $followUpThresholdHours hours without resolution" }
                    }
                    else -> error("Unsupported status for end-user")
                }
            }
        }

        return repository.updateStatus(id, status, actor).also { audit.log(userId, "Changed ticket #$id to $status", "tickets") }
    }
}
