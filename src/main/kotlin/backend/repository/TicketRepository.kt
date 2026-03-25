package backend.repository

import backend.config.SLAPoliciesTable
import backend.config.TicketHistoryTable
import backend.config.TicketsTable
import backend.models.Ticket
import backend.models.TicketRequest
import backend.models.TicketStatus
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Duration
import java.time.LocalDateTime

class TicketRepository {
    fun create(userId: Int, req: TicketRequest): Ticket = transaction {
        val now = LocalDateTime.now()
        val id = TicketsTable.insert {
            it[TicketsTable.userId] = userId
            it[title] = req.title
            it[description] = req.description
            it[priority] = req.priority
            it[category] = req.category
            it[status] = TicketStatus.OPEN.name
            it[deviceId] = req.deviceId
            it[createdAt] = now
            it[updatedAt] = now
        }[TicketsTable.id]

        TicketHistoryTable.insert {
            it[ticketId] = id
            it[status] = TicketStatus.OPEN.name
            it[updatedBy] = "user-$userId"
            it[timestamp] = now
        }
        get(id)!!
    }

    fun list(userId: Int?, admin: Boolean, limit: Int, offset: Long): PagedResult<Ticket> = transaction {
        val base = if (admin) TicketsTable.selectAll() else TicketsTable.selectAll().where { TicketsTable.userId eq userId!! }
        val total = base.count()
        val items = base.limit(limit, offset).map(::toTicket)
        PagedResult(items, total)
    }

    fun get(id: Int): Ticket? = transaction { TicketsTable.selectAll().where { TicketsTable.id eq id }.singleOrNull()?.let(::toTicket) }

    fun update(id: Int, req: TicketRequest): Ticket? = transaction {
        TicketsTable.update({ TicketsTable.id eq id }) {
            it[title] = req.title
            it[description] = req.description
            it[priority] = req.priority
            it[category] = req.category
            it[deviceId] = req.deviceId
            it[updatedAt] = LocalDateTime.now()
        }
        get(id)
    }

    fun updateStatus(id: Int, newStatus: String, updatedBy: String): Ticket? = transaction {
        val now = LocalDateTime.now()
        TicketsTable.update({ TicketsTable.id eq id }) {
            it[status] = newStatus
            it[updatedAt] = now
        }
        TicketHistoryTable.insert {
            it[ticketId] = id
            it[status] = newStatus
            it[TicketHistoryTable.updatedBy] = updatedBy
            it[timestamp] = now
        }
        get(id)
    }

    private fun toTicket(row: ResultRow): Ticket {
        val priorityValue = row[TicketsTable.priority]
        val slaMinutes = SLAPoliciesTable.selectAll().where { SLAPoliciesTable.priority eq priorityValue }
            .singleOrNull()?.get(SLAPoliciesTable.resolutionTime) ?: 480
        val deadline = row[TicketsTable.createdAt].plusMinutes(slaMinutes.toLong())
        val remaining = Duration.between(LocalDateTime.now(), deadline).toMinutes()
        return Ticket(
            id = row[TicketsTable.id],
            userId = row[TicketsTable.userId],
            title = row[TicketsTable.title],
            description = row[TicketsTable.description],
            priority = priorityValue,
            category = row[TicketsTable.category],
            status = runCatching { TicketStatus.valueOf(row[TicketsTable.status]) }.getOrDefault(TicketStatus.OPEN),
            assignedTo = row[TicketsTable.assignedTo],
            deviceId = row[TicketsTable.deviceId],
            createdAt = row[TicketsTable.createdAt].toString(),
            updatedAt = row[TicketsTable.updatedAt].toString(),
            slaRemainingMinutes = remaining,
            overdue = remaining < 0 && runCatching { TicketStatus.valueOf(row[TicketsTable.status]) }.getOrDefault(TicketStatus.OPEN) != TicketStatus.RESOLVED
        )
    }
}
