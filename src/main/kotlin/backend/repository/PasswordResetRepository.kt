package backend.repository

import backend.config.PasswordResetTokensTable
import backend.services.SessionService
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID

class PasswordResetRepository {
    fun create(userId: Int): String = transaction {
        val now = LocalDateTime.now()
        PasswordResetTokensTable.update({ (PasswordResetTokensTable.userId eq userId) and PasswordResetTokensTable.usedAt.isNull() }) { it[usedAt] = now }
        val raw = ByteArray(32).also(SecureRandom()::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        PasswordResetTokensTable.insert {
            it[id] = UUID.randomUUID(); it[PasswordResetTokensTable.userId] = userId; it[tokenHash] = SessionService.sha256(raw)
            it[createdAt] = now; it[expiresAt] = now.plusMinutes(30); it[usedAt] = null
        }
        raw
    }

    fun consume(raw: String): Int? = transaction {
        val row = PasswordResetTokensTable.selectAll().where { PasswordResetTokensTable.tokenHash eq SessionService.sha256(raw) }.singleOrNull()
            ?: return@transaction null
        if (row[PasswordResetTokensTable.usedAt] != null || !row[PasswordResetTokensTable.expiresAt].isAfter(LocalDateTime.now())) return@transaction null
        PasswordResetTokensTable.update({ PasswordResetTokensTable.id eq row[PasswordResetTokensTable.id] }) { it[usedAt] = LocalDateTime.now() }
        row[PasswordResetTokensTable.userId]
    }
}
