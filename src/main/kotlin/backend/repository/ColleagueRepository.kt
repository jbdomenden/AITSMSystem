package backend.repository

import backend.config.ColleagueRequestsTable
import backend.config.DirectMessagesTable
import backend.config.UsersTable
import backend.models.Colleague
import backend.models.ColleagueRequest
import backend.models.ColleagueRequestStatus
import backend.models.DirectMessage
import backend.models.UserRole
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

class ColleagueRepository {
    fun searchableUsers(actorId: Int, query: String): List<Colleague> = transaction {
        UsersTable.selectAll().where(Op.build { (UsersTable.id neq actorId) and (UsersTable.emailVerified eq true) })
            .map(::toColleague).filter { user ->
                query.isBlank() || listOf(user.fullName, user.email, user.department).any { it.contains(query, true) }
            }.take(30)
    }

    fun request(senderId: Int, recipientId: Int): ColleagueRequest = transaction {
        val id = ColleagueRequestsTable.insert {
            it[ColleagueRequestsTable.senderId] = senderId; it[ColleagueRequestsTable.recipientId] = recipientId
            it[status] = ColleagueRequestStatus.PENDING.name; it[createdAt] = LocalDateTime.now(); it[respondedAt] = null
        }[ColleagueRequestsTable.id]
        ColleagueRequestsTable.selectAll().where { ColleagueRequestsTable.id eq id }.single().let(::toRequest)
    }

    fun requestBetween(firstId: Int, secondId: Int): ColleagueRequest? = transaction {
        ColleagueRequestsTable.selectAll().where(Op.build {
            ((ColleagueRequestsTable.senderId eq firstId) and (ColleagueRequestsTable.recipientId eq secondId)) or
                ((ColleagueRequestsTable.senderId eq secondId) and (ColleagueRequestsTable.recipientId eq firstId))
        }).singleOrNull()?.let(::toRequest)
    }

    fun respond(id: Int, recipientId: Int, status: ColleagueRequestStatus): ColleagueRequest? = transaction {
        val row = ColleagueRequestsTable.selectAll().where { ColleagueRequestsTable.id eq id }.singleOrNull() ?: return@transaction null
        if (row[ColleagueRequestsTable.recipientId] != recipientId || row[ColleagueRequestsTable.status] != ColleagueRequestStatus.PENDING.name) return@transaction null
        ColleagueRequestsTable.update({ ColleagueRequestsTable.id eq id }) { it[ColleagueRequestsTable.status] = status.name; it[respondedAt] = LocalDateTime.now() }
        ColleagueRequestsTable.selectAll().where { ColleagueRequestsTable.id eq id }.single().let(::toRequest)
    }

    fun cancel(id: Int, senderId: Int): Boolean = transaction {
        ColleagueRequestsTable.update({ (ColleagueRequestsTable.id eq id) and (ColleagueRequestsTable.senderId eq senderId) and (ColleagueRequestsTable.status eq ColleagueRequestStatus.PENDING.name) }) {
            it[status] = ColleagueRequestStatus.CANCELLED.name; it[respondedAt] = LocalDateTime.now()
        } > 0
    }

    fun remove(firstId: Int, secondId: Int): Boolean = transaction {
        ColleagueRequestsTable.deleteWhere { Op.build { ((ColleagueRequestsTable.senderId eq firstId) and (ColleagueRequestsTable.recipientId eq secondId)) or ((ColleagueRequestsTable.senderId eq secondId) and (ColleagueRequestsTable.recipientId eq firstId)) } } > 0
    }

    fun colleagues(userId: Int): List<Colleague> = transaction {
        ColleagueRequestsTable.selectAll().where(Op.build {
            ((ColleagueRequestsTable.senderId eq userId) or (ColleagueRequestsTable.recipientId eq userId)) and (ColleagueRequestsTable.status eq ColleagueRequestStatus.ACCEPTED.name)
        }).map { row -> if (row[ColleagueRequestsTable.senderId] == userId) row[ColleagueRequestsTable.recipientId] else row[ColleagueRequestsTable.senderId] }
            .mapNotNull { id -> UsersTable.selectAll().where { UsersTable.id eq id }.singleOrNull()?.let(::toColleague) }
    }

    fun requestsFor(userId: Int): List<ColleagueRequest> = transaction {
        ColleagueRequestsTable.selectAll().where(Op.build { (ColleagueRequestsTable.senderId eq userId) or (ColleagueRequestsTable.recipientId eq userId) }).map(::toRequest)
    }

    fun sendMessage(senderId: Int, recipientId: Int, body: String): DirectMessage = transaction {
        val id = DirectMessagesTable.insert { it[DirectMessagesTable.senderId] = senderId; it[DirectMessagesTable.recipientId] = recipientId; it[DirectMessagesTable.body] = body; it[sentAt] = LocalDateTime.now(); it[readAt] = null }[DirectMessagesTable.id]
        DirectMessagesTable.selectAll().where { DirectMessagesTable.id eq id }.single().let(::toMessage)
    }

    fun messages(firstId: Int, secondId: Int): List<DirectMessage> = transaction {
        DirectMessagesTable.selectAll().where(Op.build { ((DirectMessagesTable.senderId eq firstId) and (DirectMessagesTable.recipientId eq secondId)) or ((DirectMessagesTable.senderId eq secondId) and (DirectMessagesTable.recipientId eq firstId)) }).orderBy(DirectMessagesTable.sentAt).map(::toMessage)
    }

    fun markRead(recipientId: Int, senderId: Int): Int = transaction {
        DirectMessagesTable.update({ (DirectMessagesTable.recipientId eq recipientId) and (DirectMessagesTable.senderId eq senderId) and DirectMessagesTable.readAt.isNull() }) { it[readAt] = LocalDateTime.now() }
    }

    private fun toRequest(row: ResultRow) = ColleagueRequest(row[ColleagueRequestsTable.id], row[ColleagueRequestsTable.senderId], row[ColleagueRequestsTable.recipientId], ColleagueRequestStatus.valueOf(row[ColleagueRequestsTable.status]), row[ColleagueRequestsTable.createdAt].toString(), row[ColleagueRequestsTable.respondedAt]?.toString())
    private fun toMessage(row: ResultRow) = DirectMessage(row[DirectMessagesTable.id], row[DirectMessagesTable.senderId], row[DirectMessagesTable.recipientId], row[DirectMessagesTable.body], row[DirectMessagesTable.sentAt].toString(), row[DirectMessagesTable.readAt]?.toString())
    private fun toColleague(row: ResultRow) = Colleague(row[UsersTable.id], row[UsersTable.fullName], row[UsersTable.email], row[UsersTable.department], UserRole.from(row[UsersTable.role]), row[UsersTable.profilePhotoUrl])
}
