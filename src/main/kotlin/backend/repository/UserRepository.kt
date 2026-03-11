package backend.repository

import backend.config.EulaAcceptanceTable
import backend.config.UsersTable
import backend.models.RegisterRequest
import backend.models.User
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class UserRepository {
    fun create(request: RegisterRequest, passwordHash: String, role: String = "end-user"): User = transaction {
        val now = LocalDateTime.now()
        val id = UsersTable.insert {
            it[fullName] = request.fullName
            it[email] = request.email
            it[company] = request.company
            it[department] = request.department
            it[UsersTable.passwordHash] = passwordHash
            it[UsersTable.role] = role
            it[createdAt] = now
        }[UsersTable.id]

        EulaAcceptanceTable.insert {
            it[userId] = id
            it[eulaVersion] = request.eulaVersion
            it[acceptedAt] = now
        }
        findById(id)!!
    }

    fun findByEmail(email: String): Pair<User, String>? = transaction {
        UsersTable.selectAll().where { UsersTable.email eq email }.singleOrNull()?.let {
            toUser(it) to it[UsersTable.passwordHash]
        }
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
        createdAt = row[UsersTable.createdAt].toString()
    )
}
