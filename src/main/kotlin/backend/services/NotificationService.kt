package backend.services

import backend.config.NotificationsTable
import backend.config.UsersTable
import backend.models.Notification
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

class NotificationService {
    fun list(userId: Int): List<Notification> = transaction {
        NotificationsTable.join(
            UsersTable,
            JoinType.LEFT,
            additionalConstraint = { NotificationsTable.relatedUserId eq UsersTable.id }
        ).selectAll()
            .where { (NotificationsTable.userId eq userId) and (NotificationsTable.type neq "direct_message") }
            .orderBy(NotificationsTable.createdAt, org.jetbrains.exposed.sql.SortOrder.DESC)
            .map {
                Notification(
                    id = it[NotificationsTable.id],
                    userId = it[NotificationsTable.userId],
                    title = it[NotificationsTable.title],
                    message = it[NotificationsTable.message],
                    type = it[NotificationsTable.type],
                    relatedTicketId = it[NotificationsTable.relatedTicketId],
                    relatedUserId = it[NotificationsTable.relatedUserId],
                    relatedUserDisplayName = it.getOrNull(UsersTable.fullName),
                    relatedUserProfileImageUrl = it.getOrNull(UsersTable.profilePhotoUrl),
                    destination = destinationFor(
                        it[NotificationsTable.type],
                        it[NotificationsTable.relatedTicketId],
                        it[NotificationsTable.relatedUserId]
                    ),
                    isRead = it[NotificationsTable.isRead],
                    createdAt = it[NotificationsTable.createdAt].toString(),
                    readAt = it[NotificationsTable.readAt]?.toString()
                )
            }
    }

    fun unreadCount(userId: Int): Long = transaction {
        NotificationsTable.selectAll()
            .where { (NotificationsTable.userId eq userId) and (NotificationsTable.isRead eq false) and (NotificationsTable.type neq "direct_message") }
            .count()
    }

    fun markAsRead(userId: Int, notificationId: Int): Boolean = transaction {
        NotificationsTable.update({
                (NotificationsTable.id eq notificationId) and
                (NotificationsTable.userId eq userId) and
                (NotificationsTable.type neq "direct_message") and
                (NotificationsTable.isRead eq false)
        }) {
            it[isRead] = true
            it[readAt] = LocalDateTime.now()
        } > 0
    }

    fun markAllAsRead(userId: Int): Int = transaction {
        NotificationsTable.update({
            (NotificationsTable.userId eq userId) and
                (NotificationsTable.type neq "direct_message") and
                (NotificationsTable.isRead eq false)
        }) {
            it[isRead] = true
            it[readAt] = LocalDateTime.now()
        }
    }

    fun belongsToUser(userId: Int, notificationId: Int): Boolean = transaction {
        NotificationsTable.selectAll().where {
            (NotificationsTable.id eq notificationId) and
                (NotificationsTable.userId eq userId) and
                (NotificationsTable.type neq "direct_message")
        }.any()
    }

    fun push(
        userId: Int,
        title: String,
        message: String,
        type: String = "info",
        relatedTicketId: Int? = null,
        relatedUserId: Int? = null,
        eventKey: String? = null
    ) = transaction {
        NotificationsTable.insertIgnore {
            it[NotificationsTable.userId] = userId
            it[NotificationsTable.title] = title
            it[NotificationsTable.message] = message
            it[NotificationsTable.type] = type
            it[NotificationsTable.relatedTicketId] = relatedTicketId
            it[NotificationsTable.relatedUserId] = relatedUserId
            it[NotificationsTable.eventKey] = eventKey?.trim()?.takeIf(String::isNotBlank)?.take(180)
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
        message: String,
        occurrenceKey: String = eventType
    ) = transaction {
        NotificationsTable.insertIgnore {
            it[NotificationsTable.userId] = userId
            it[NotificationsTable.title] = title
            it[NotificationsTable.message] = message
            it[NotificationsTable.type] = eventType
            it[NotificationsTable.relatedTicketId] = ticketId
            it[NotificationsTable.relatedUserId] = null
            it[NotificationsTable.eventKey] = "ticket:$ticketId:$eventType:$occurrenceKey:$userId".take(180)
            it[NotificationsTable.isRead] = false
            it[NotificationsTable.createdAt] = LocalDateTime.now()
            it[NotificationsTable.readAt] = null
        }
    }


    private fun destinationFor(type: String, ticketId: Int?, relatedUserId: Int?): String? = when {
        type in setOf("colleague_added", "colleague_accepted", "colleague_request") && relatedUserId != null ->
            "/profile?id=$relatedUserId"
        type.startsWith("profile_photo_") -> "/profile#photoApprovalCard"
        type in setOf("account_approved", "account_rejected", "account_created", "role_changed", "account_state_changed") ->
            "/profile"
        type.startsWith("security_") -> "/security"
        type.startsWith("ticket_") && ticketId != null -> null // Portal-specific route is selected by the client.
        type.startsWith("knowledge_") -> null // Portal-specific route is selected by the client.
        else -> null
    }
}
