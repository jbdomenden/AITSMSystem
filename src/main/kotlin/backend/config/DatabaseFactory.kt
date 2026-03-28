package backend.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction

object UsersTable : Table("users") {
    val id = integer("id").autoIncrement()
    val fullName = varchar("full_name", 150)
    val email = varchar("email", 200).uniqueIndex()
    val company = varchar("company", 150)
    val department = varchar("department", 120)
    val passwordHash = varchar("password_hash", 255)
    val role = varchar("role", 20).index()
    val emailVerified = bool("email_verified").default(false)
    val verificationCode = varchar("verification_code", 12).nullable()
    val verificationExpiresAt = datetime("verification_expires_at").nullable()
    val createdAt = datetime("created_at").index()
    override val primaryKey = PrimaryKey(id)
}

object EulaAcceptanceTable : Table("eula_acceptance") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE).index()
    val eulaVersion = varchar("eula_version", 20)
    val acceptedAt = datetime("accepted_at")
    override val primaryKey = PrimaryKey(id)
}

object DevicesTable : Table("devices") {
    val id = integer("id").autoIncrement()
    val deviceName = varchar("device_name", 150)
    val ipAddress = varchar("ip_address", 45)
    val department = varchar("department", 120)
    val assignedUser = varchar("assigned_user", 150)
    val cpuUsage = integer("cpu_usage")
    val memoryUsage = integer("memory_usage")
    val status = varchar("status", 50).index()
    val lastSeen = datetime("last_seen")
    override val primaryKey = PrimaryKey(id)
}

object TicketsTable : Table("tickets") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE).index()
    val title = varchar("title", 200)
    val description = text("description")
    val priority = varchar("priority", 20)
    val category = varchar("category", 80)
    val status = varchar("status", 30).index()
    val assignedTo = varchar("assigned_to", 150).nullable()
    val deviceId = integer("device_id").references(DevicesTable.id, onDelete = ReferenceOption.CASCADE).nullable().index()
    val createdAt = datetime("created_at").index()
    val updatedAt = datetime("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object TicketHistoryTable : Table("ticket_history") {
    val id = integer("id").autoIncrement()
    val ticketId = integer("ticket_id").references(TicketsTable.id, onDelete = ReferenceOption.CASCADE).index()
    val status = varchar("status", 30).index()
    val updatedBy = varchar("updated_by", 150)
    val timestamp = datetime("timestamp").index()
    override val primaryKey = PrimaryKey(id)
}

object NotificationsTable : Table("notifications") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE).index()
    val message = text("message")
    val type = varchar("type", 40)
    val createdAt = datetime("created_at").index()
    override val primaryKey = PrimaryKey(id)
}

object SLAPoliciesTable : Table("sla_policies") {
    val id = integer("id").autoIncrement()
    val priority = varchar("priority", 20)
    val responseTime = integer("response_time")
    val resolutionTime = integer("resolution_time")
    override val primaryKey = PrimaryKey(id)
}

object SystemSettingsTable : Table("system_settings") {
    val id = integer("id").autoIncrement()
    val key = varchar("key", 120).uniqueIndex()
    val value = text("value")
    val updatedAt = datetime("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object KnowledgeArticlesTable : Table("knowledge_articles") {
    val id = integer("id").autoIncrement()
    val title = varchar("title", 200)
    val content = text("content")
    val category = varchar("category", 100)
    val createdAt = datetime("created_at").index()
    override val primaryKey = PrimaryKey(id)
}

object AuditLogsTable : Table("audit_logs") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").nullable().index()
    val action = varchar("action", 160)
    val entity = varchar("entity", 80)
    val timestamp = datetime("timestamp").index()
    override val primaryKey = PrimaryKey(id)
}

object AIConversationMessagesTable : Table("ai_conversation_messages") {
    val id = integer("id").autoIncrement()
    val sessionId = varchar("session_id", 128).index()
    val role = varchar("role", 32)
    val content = text("content")
    val createdAt = datetime("created_at").index()
    override val primaryKey = PrimaryKey(id)
}

object DatabaseFactory {
    fun init() {
        val dbUrl = Env.get("DB_URL") ?: error("DB_URL is required")
        val dbUser = Env.get("DB_USER") ?: error("DB_USER is required")
        val dbPassword = Env.get("DB_PASSWORD") ?: error("DB_PASSWORD is required")

        val config = HikariConfig().apply {
            jdbcUrl = dbUrl
            username = dbUser
            password = dbPassword
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        Database.connect(HikariDataSource(config))
        transaction {
            exec("ALTER TABLE IF EXISTS devices DROP CONSTRAINT IF EXISTS devices_assigned_user_fkey")
            exec("ALTER TABLE IF EXISTS ticket_history DROP CONSTRAINT IF EXISTS ticket_history_updated_by_fkey")

            SchemaUtils.createMissingTablesAndColumns(
                UsersTable,
                EulaAcceptanceTable,
                DevicesTable,
                TicketsTable,
                TicketHistoryTable,
                NotificationsTable,
                SLAPoliciesTable,
                SystemSettingsTable,
                KnowledgeArticlesTable,
                AuditLogsTable,
                AIConversationMessagesTable
            )
        }
    }
}
