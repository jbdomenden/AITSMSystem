package backend.services

import backend.config.NotificationsTable
import backend.models.Notification
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class NotificationService {
    fun list(userId: Int): List<Notification> = transaction {
        NotificationsTable.selectAll().where { NotificationsTable.userId eq userId }.map {
            Notification(it[NotificationsTable.id], it[NotificationsTable.userId], it[NotificationsTable.message], it[NotificationsTable.type], it[NotificationsTable.createdAt].toString())
        }
    }

    fun push(userId: Int, message: String, type: String = "info") = transaction {
        NotificationsTable.insert {
            it[NotificationsTable.userId] = userId
            it[NotificationsTable.message] = message
            it[NotificationsTable.type] = type
            it[NotificationsTable.createdAt] = LocalDateTime.now()
        }
    }
}
