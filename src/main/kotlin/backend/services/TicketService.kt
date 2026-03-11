package backend.services

import backend.models.Ticket
import backend.models.TicketRequest
import backend.repository.AuditRepository
import backend.repository.TicketRepository

class TicketService(private val repository: TicketRepository, private val audit: AuditRepository) {
    fun create(userId: Int, req: TicketRequest): Ticket = repository.create(userId, req).also { audit.log(userId, "Created ticket #${it.id}", "tickets") }
    fun list(userId: Int?, admin: Boolean): List<Ticket> = repository.list(userId, admin)
    fun get(id: Int): Ticket? = repository.get(id)
    fun update(id: Int, req: TicketRequest, userId: Int?) = repository.update(id, req).also { audit.log(userId, "Updated ticket #$id", "tickets") }
    fun updateStatus(id: Int, status: String, actor: String, userId: Int?) = repository.updateStatus(id, status, actor).also { audit.log(userId, "Changed ticket #$id to $status", "tickets") }
}
