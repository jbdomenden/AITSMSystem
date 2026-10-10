package backend.repository

import backend.config.InvitationsTable
import backend.models.InvitationView
import backend.models.UserRole
import backend.services.SessionService
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID

data class ValidInvitation(val id: UUID, val email: String, val role: UserRole)

class InvitationRepository {
    fun create(email: String, role: UserRole, inviter: Int, ttlHours: Long = 24): Pair<InvitationView, String> = transaction {
        val normalized = email.trim().lowercase()
        val now = LocalDateTime.now()
        InvitationsTable.update({ (InvitationsTable.email eq normalized) and InvitationsTable.acceptedAt.isNull() and InvitationsTable.revokedAt.isNull() }) {
            it[revokedAt] = now
        }
        val raw = ByteArray(32).also(SecureRandom()::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val id = UUID.randomUUID()
        InvitationsTable.insert {
            it[InvitationsTable.id] = id; it[InvitationsTable.email] = normalized; it[intendedRole] = role.name
            it[tokenHash] = SessionService.sha256(raw); it[inviterUserId] = inviter
            it[createdAt] = now; it[expiresAt] = now.plusHours(ttlHours); it[acceptedAt] = null; it[revokedAt] = null
        }
        InvitationsTable.selectAll().where { InvitationsTable.id eq id }.single().toView() to raw
    }

    fun validate(raw: String): ValidInvitation? = transaction {
        val now = LocalDateTime.now()
        val row = InvitationsTable.selectAll().where { InvitationsTable.tokenHash eq SessionService.sha256(raw) }.singleOrNull()
            ?: return@transaction null
        if (row[InvitationsTable.acceptedAt] != null || row[InvitationsTable.revokedAt] != null || !row[InvitationsTable.expiresAt].isAfter(now)) return@transaction null
        ValidInvitation(row[InvitationsTable.id], row[InvitationsTable.email], UserRole.from(row[InvitationsTable.intendedRole]))
    }

    fun accept(id: UUID): Boolean = transaction {
        InvitationsTable.update({ InvitationsTable.id eq id }) { it[acceptedAt] = LocalDateTime.now() } == 1
    }
    fun revoke(id: UUID): Boolean = transaction {
        InvitationsTable.update({ InvitationsTable.id eq id }) { it[revokedAt] = LocalDateTime.now() } == 1
    }
    fun list(): List<InvitationView> = transaction { InvitationsTable.selectAll().orderBy(InvitationsTable.createdAt).map { it.toView() } }
}

private fun org.jetbrains.exposed.sql.ResultRow.toView() = InvitationView(
    this[InvitationsTable.id].toString(), this[InvitationsTable.email], UserRole.from(this[InvitationsTable.intendedRole]),
    this[InvitationsTable.createdAt].toString(), this[InvitationsTable.expiresAt].toString(),
    this[InvitationsTable.acceptedAt]?.toString(), this[InvitationsTable.revokedAt]?.toString()
)
