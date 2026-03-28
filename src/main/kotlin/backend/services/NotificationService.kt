package backend.services

import backend.config.NotificationsTable
import backend.models.Notification
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

class NotificationService {
    fun list(userId: Int): List<Notification> = transaction {
        NotificationsTable.selectAll()
            .where { NotificationsTable.userId eq userId }
            .orderBy(NotificationsTable.createdAt, org.jetbrains.exposed.sql.SortOrder.DESC)
            .map {
                Notification(
                    id = it[NotificationsTable.id],
                    userId = it[NotificationsTable.userId],
                    title = it[NotificationsTable.title],
                    message = it[NotificationsTable.message],
                    type = it[NotificationsTable.type],
                    relatedTicketId = it[NotificationsTable.relatedTicketId],
                    isRead = it[NotificationsTable.isRead],
                    createdAt = it[NotificationsTable.createdAt].toString(),
                    readAt = it[NotificationsTable.readAt]?.toString()
                )
            }
    }

    fun unreadCount(userId: Int): Long = transaction {
        NotificationsTable.selectAll()
            .where { (NotificationsTable.userId eq userId) and (NotificationsTable.isRead eq false) }
            .count()
    }

    fun markAsRead(userId: Int, notificationId: Int): Boolean = transaction {
        NotificationsTable.update({
            (NotificationsTable.id eq notificationId) and
                (NotificationsTable.userId eq userId) and
                (NotificationsTable.isRead eq false)
        }) {
            it[isRead] = true
            it[readAt] = LocalDateTime.now()
        } > 0
    }

    fun markAllAsRead(userId: Int): Int = transaction {
        NotificationsTable.update({
            (NotificationsTable.userId eq userId) and
                (NotificationsTable.isRead eq false)
        }) {
            it[isRead] = true
            it[readAt] = LocalDateTime.now()
        }
    }

    fun push(
        userId: Int,
        title: String,
        message: String,
        type: String = "info",
        relatedTicketId: Int? = null
    ) = transaction {
        NotificationsTable.insert {
            it[NotificationsTable.userId] = userId
            it[NotificationsTable.title] = title
            it[NotificationsTable.message] = message
            it[NotificationsTable.type] = type
            it[NotificationsTable.relatedTicketId] = relatedTicketId
            it[NotificationsTable.isRead] = false
            it[NotificationsTable.createdAt] = LocalDateTime.now()
            it[NotificationsTable.readAt] = null
        }
    }

    fun pushOncePerTicketEvent(
        userId: Int,
        eventType: String,
        ticketId: Int,
        title: String,
        message: String
    ) = transaction {
        val exists = NotificationsTable.selectAll().where {
            (NotificationsTable.userId eq userId) and
                (NotificationsTable.type eq eventType) and
                (NotificationsTable.relatedTicketId eq ticketId)
        }.any()

        if (!exists) {
            NotificationsTable.insert {
                it[NotificationsTable.userId] = userId
                it[NotificationsTable.title] = title
                it[NotificationsTable.message] = message
                it[NotificationsTable.type] = eventType
                it[NotificationsTable.relatedTicketId] = ticketId
                it[NotificationsTable.isRead] = false
                it[NotificationsTable.createdAt] = LocalDateTime.now()
                it[NotificationsTable.readAt] = null
            }
        }
    }
}
