package backend.repository

import backend.config.EulaAcceptanceTable
import backend.config.NotificationsTable
import backend.config.TicketHistoryTable
import backend.config.TicketsTable
import backend.config.UsersTable
import backend.models.RegisterRequest
import backend.models.User
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

class UserRepository {
    fun create(
        request: RegisterRequest,
        passwordHash: String,
        role: String = "end-user",
        emailVerified: Boolean = false,
        verificationCode: String? = null,
        verificationExpiry: LocalDateTime? = null
    ): User = transaction {
        val now = LocalDateTime.now()
        val id = UsersTable.insert {
            it[fullName] = request.fullName
            it[email] = request.email.lowercase()
            it[company] = request.company
            it[department] = request.department
            it[UsersTable.passwordHash] = passwordHash
            it[UsersTable.role] = role
            it[UsersTable.emailVerified] = emailVerified
            it[UsersTable.verificationCode] = verificationCode
            it[UsersTable.verificationExpiresAt] = verificationExpiry
            it[createdAt] = now
        }[UsersTable.id]

        EulaAcceptanceTable.insert {
            it[userId] = id
            it[eulaVersion] = request.eulaVersion
            it[acceptedAt] = now
        }

        UsersTable.selectAll().where { UsersTable.id eq id }.single().let(::toUser)
    }

    fun ensureSuperAdmin(
        email: String,
        passwordHash: String,
        fullName: String = "System Super Admin",
        company: String = "AITSM",
        department: String = "Platform"
    ): User = transaction {
        val normalized = email.lowercase()
        val existing = UsersTable.selectAll().where { UsersTable.email eq normalized }.singleOrNull()
        if (existing == null) {
            val now = LocalDateTime.now()
            val id = UsersTable.insert {
                it[UsersTable.fullName] = fullName
                it[UsersTable.email] = normalized
                it[UsersTable.company] = company
                it[UsersTable.department] = department
                it[UsersTable.passwordHash] = passwordHash
                it[UsersTable.role] = "superadmin"
                it[UsersTable.emailVerified] = true
                it[UsersTable.verificationCode] = null
                it[UsersTable.verificationExpiresAt] = null
                it[UsersTable.createdAt] = now
            }[UsersTable.id]
            return@transaction UsersTable.selectAll().where { UsersTable.id eq id }.single().let(::toUser)
        }

        val userId = existing[UsersTable.id]
        UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.passwordHash] = passwordHash
            it[UsersTable.role] = "superadmin"
            it[UsersTable.emailVerified] = true
            it[UsersTable.verificationCode] = null
            it[UsersTable.verificationExpiresAt] = null
        }
        UsersTable.selectAll().where { UsersTable.id eq userId }.single().let(::toUser)
    }

    fun findByEmail(email: String): Pair<User, String>? = transaction {
        UsersTable.selectAll().where { UsersTable.email eq email.lowercase() }.singleOrNull()?.let {
            toUser(it) to it[UsersTable.passwordHash]
        }
    }

    fun findHashById(userId: Int): String? = transaction {
        UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull()?.get(UsersTable.passwordHash)
    }

    fun findByEmailOnly(email: String): User? = transaction {
        UsersTable.selectAll().where { UsersTable.email eq email.lowercase() }.singleOrNull()?.let(::toUser)
    }

    fun verifyEmail(email: String, code: String): User? = transaction {
        val row = UsersTable.selectAll().where { UsersTable.email eq email.lowercase() }.singleOrNull() ?: return@transaction null
        val expectedCode = row[UsersTable.verificationCode]
        val expiry = row[UsersTable.verificationExpiresAt]
        if (expectedCode != code || expiry == null || expiry.isBefore(LocalDateTime.now())) return@transaction null

        UsersTable.update({ UsersTable.id eq row[UsersTable.id] }) {
            it[emailVerified] = true
            it[verificationCode] = null
            it[verificationExpiresAt] = null
        }
        UsersTable.selectAll().where { UsersTable.id eq row[UsersTable.id] }.single().let(::toUser)
    }

    fun regenerateVerificationCode(email: String, code: String, expiresAt: LocalDateTime): Boolean = transaction {
        val row = UsersTable.selectAll().where { UsersTable.email eq email.lowercase() }.singleOrNull() ?: return@transaction false
        if (row[UsersTable.emailVerified]) return@transaction false

        UsersTable.update({ UsersTable.id eq row[UsersTable.id] }) {
            it[verificationCode] = code
            it[verificationExpiresAt] = expiresAt
        }
        true
    }

    fun listUsers(): List<User> = transaction {
        UsersTable.selectAll().orderBy(UsersTable.createdAt).map(::toUser)
    }

    fun updateRoleByEmail(email: String, role: String): User? = transaction {
        val row = UsersTable.selectAll().where { UsersTable.email eq email.lowercase() }.singleOrNull() ?: return@transaction null
        if (row[UsersTable.role] == "superadmin") return@transaction null

        UsersTable.update({ UsersTable.id eq row[UsersTable.id] }) { it[UsersTable.role] = role }
        UsersTable.selectAll().where { UsersTable.id eq row[UsersTable.id] }.singleOrNull()?.let(::toUser)
    }

    fun updateRole(userId: Int, role: String): User? = transaction {
        val row = UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull() ?: return@transaction null
        if (row[UsersTable.role] == "superadmin") return@transaction null

        UsersTable.update({ UsersTable.id eq userId }) { it[UsersTable.role] = role }
        UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull()?.let(::toUser)
    }

    fun updateProfile(userId: Int, fullName: String, company: String, department: String): User? = transaction {
        val row = UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull() ?: return@transaction null
        UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.fullName] = fullName
            it[UsersTable.company] = company
            it[UsersTable.department] = department
        }
        UsersTable.selectAll().where { UsersTable.id eq row[UsersTable.id] }.singleOrNull()?.let(::toUser)
    }

    fun updatePassword(userId: Int, passwordHash: String): User? = transaction {
        val row = UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull() ?: return@transaction null
        if (row[UsersTable.role] == "superadmin") return@transaction null

        UsersTable.update({ UsersTable.id eq userId }) { it[UsersTable.passwordHash] = passwordHash }
        UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull()?.let(::toUser)
    }

    fun deleteUser(userId: Int): User? = transaction {
        val row = UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull() ?: return@transaction null
        if (row[UsersTable.role] == "superadmin") return@transaction null

        val ticketIds = TicketsTable.selectAll().where { TicketsTable.userId eq userId }.map { it[TicketsTable.id] }
        if (ticketIds.isNotEmpty()) {
            TicketHistoryTable.deleteWhere { TicketHistoryTable.ticketId inList ticketIds }
            TicketsTable.deleteWhere { TicketsTable.id inList ticketIds }
        }

        NotificationsTable.deleteWhere { NotificationsTable.userId eq userId }
        EulaAcceptanceTable.deleteWhere { EulaAcceptanceTable.userId eq userId }
        UsersTable.deleteWhere { UsersTable.id eq userId }
        row.let(::toUser)
    }

    fun findById(id: Int): User? = transaction {
        UsersTable.selectAll().where { UsersTable.id eq id }.singleOrNull()?.let(::toUser)
    }

    private fun toUser(row: ResultRow) = User(
        id = row[UsersTable.id],
        fullName = row[UsersTable.fullName],
        email = row[UsersTable.email],
        company = row[UsersTable.company],
        department = row[UsersTable.department],
        role = row[UsersTable.role],
        emailVerified = row[UsersTable.emailVerified],
        createdAt = row[UsersTable.createdAt].toString()
    )
}
