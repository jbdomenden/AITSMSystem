package backend.services

import backend.config.Env
import backend.config.SessionsTable
import backend.config.UsersTable
import backend.models.AccountState
import backend.models.User
import backend.models.UserRole
import backend.models.SessionView
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID

data class SessionPrincipal(val sessionId: UUID, val user: User, val mfaComplete: Boolean)
data class IssuedSession(val token: String, val csrfToken: String, val expiresAt: LocalDateTime)

class SessionService {
    private val random = SecureRandom()
    private val absoluteHours = (Env.get("SESSION_ABSOLUTE_HOURS")?.toLongOrNull() ?: 12L).coerceIn(1, 720)
    private val inactivityMinutes = (Env.get("SESSION_INACTIVITY_MINUTES")?.toLongOrNull() ?: 30L).coerceIn(5, 1440)

    fun issue(userId: Int, ip: String?, userAgent: String?, deviceLabel: String? = null, mfaComplete: Boolean = true): IssuedSession {
        val token = randomToken(32)
        val csrf = randomToken(32)
        val now = LocalDateTime.now()
        val expires = now.plusHours(absoluteHours)
        transaction {
            SessionsTable.insert {
                it[id] = UUID.randomUUID()
                it[tokenHash] = sha256(token)
                it[csrfHash] = sha256(csrf)
                it[SessionsTable.userId] = userId
                it[createdAt] = now
                it[lastSeenAt] = now
                it[expiresAt] = expires
                it[revokedAt] = null
                it[ipAddress] = ip?.take(45)
                it[SessionsTable.userAgent] = userAgent?.take(500)
                it[SessionsTable.deviceLabel] = deviceLabel?.take(120)
                it[SessionsTable.mfaComplete] = mfaComplete
            }
        }
        return IssuedSession(token, csrf, expires)
    }

    fun authenticate(rawToken: String?): SessionPrincipal? {
        if (rawToken.isNullOrBlank()) return null
        val now = LocalDateTime.now()
        return transaction {
            val row = SessionsTable.innerJoin(UsersTable).selectAll()
                .where { SessionsTable.tokenHash eq sha256(rawToken) }.singleOrNull() ?: return@transaction null
            if (row[SessionsTable.revokedAt] != null || !row[SessionsTable.expiresAt].isAfter(now) ||
                !row[SessionsTable.lastSeenAt].plusMinutes(inactivityMinutes).isAfter(now) ||
                AccountState.valueOf(row[UsersTable.accountState]) != AccountState.ACTIVE) return@transaction null
            // Page startup sends several requests in parallel. Updating the same session row for
            // every CSS, JavaScript, and API request caused PostgreSQL serialization failures and
            // made otherwise valid frontend resources randomly return HTTP 500. A one-minute
            // write throttle keeps inactivity tracking accurate without turning authentication
            // into a hot-row write on every request.
            SessionsTable.update({
                (SessionsTable.id eq row[SessionsTable.id]) and
                    (SessionsTable.lastSeenAt less now.minusMinutes(1))
            }) { it[lastSeenAt] = now }
            SessionPrincipal(row[SessionsTable.id], row.toUser(), row[SessionsTable.mfaComplete])
        }
    }

    fun validateCsrf(sessionId: UUID, rawCsrf: String?): Boolean {
        if (rawCsrf.isNullOrBlank()) return false
        return transaction {
            SessionsTable.selectAll().where { SessionsTable.id eq sessionId }.singleOrNull()
                ?.let { constantTimeEquals(it[SessionsTable.csrfHash], sha256(rawCsrf)) } == true
        }
    }

    fun rotateCsrf(sessionId: UUID): String {
        val csrf = randomToken(32)
        val updated = transaction {
            SessionsTable.update({
                (SessionsTable.id eq sessionId) and SessionsTable.revokedAt.isNull()
            }) { it[csrfHash] = sha256(csrf) }
        }
        check(updated == 1) { "Active session was not found." }
        return csrf
    }

    fun revoke(sessionId: UUID) = transaction {
        SessionsTable.update({ SessionsTable.id eq sessionId }) { it[revokedAt] = LocalDateTime.now() }
    }

    fun revokeAll(userId: Int, except: UUID? = null) = transaction {
        SessionsTable.update({
            val activeForUser = (SessionsTable.userId eq userId) and SessionsTable.revokedAt.isNull()
            if (except == null) activeForUser else activeForUser and (SessionsTable.id neq except)
        }) { it[revokedAt] = LocalDateTime.now() }
    }

    fun rotate(sessionId: UUID, userId: Int, ip: String?, userAgent: String?): IssuedSession {
        revoke(sessionId)
        return issue(userId, ip, userAgent)
    }

    fun list(userId: Int, current: UUID): List<SessionView> = transaction {
        SessionsTable.selectAll().where { (SessionsTable.userId eq userId) and SessionsTable.revokedAt.isNull() }
            .filter { it[SessionsTable.expiresAt].isAfter(LocalDateTime.now()) }
            .map { row -> SessionView(
                row[SessionsTable.id].toString(), row[SessionsTable.id] == current,
                row[SessionsTable.createdAt].toString(), row[SessionsTable.lastSeenAt].toString(), row[SessionsTable.expiresAt].toString(),
                row[SessionsTable.deviceLabel] ?: row[SessionsTable.userAgent]?.take(100), maskIp(row[SessionsTable.ipAddress])
            ) }
    }

    fun revokeOwned(userId: Int, sessionId: UUID): Boolean = transaction {
        SessionsTable.update({ (SessionsTable.id eq sessionId) and (SessionsTable.userId eq userId) }) { it[revokedAt] = LocalDateTime.now() } == 1
    }

    companion object {
        const val COOKIE_NAME = "aitsm_session"
        const val CSRF_COOKIE_NAME = "aitsm_csrf"
        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        private fun constantTimeEquals(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
        private fun randomToken(bytes: Int): String = ByteArray(bytes).also(SecureRandom()::nextBytes)
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        private fun maskIp(ip: String?): String? = ip?.let { value ->
            if (value.contains(':')) value.substringBeforeLast(':', value) + ":…" else value.split('.').let { if (it.size == 4) "${it[0]}.${it[1]}.${it[2]}.x" else "masked" }
        }
    }
}

private fun ResultRow.toUser() = User(
    id = this[UsersTable.id], fullName = this[UsersTable.fullName], email = this[UsersTable.email],
    company = this[UsersTable.company], department = this[UsersTable.department],
    profilePhotoUrl = this[UsersTable.profilePhotoUrl], role = UserRole.from(this[UsersTable.role]),
    emailVerified = this[UsersTable.emailVerified], accountState = AccountState.valueOf(this[UsersTable.accountState]),
    createdAt = this[UsersTable.createdAt].toString()
)
