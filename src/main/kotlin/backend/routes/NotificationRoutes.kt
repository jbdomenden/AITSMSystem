package backend.routes

import backend.security.userId
import backend.services.NotificationService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
private data class MarkReadRequest(val notificationId: Int)

@Serializable
private data class NotificationReadResponse(val updated: Boolean, val unreadCount: Long)

@Serializable
private data class NotificationBatchReadResponse(val updated: Int, val unreadCount: Long)

fun Route.notificationRoutes(service: NotificationService) {
    route("/api/notifications") {
        get {
            val userId = call.userId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            call.respond(service.list(userId))
        }

        get("/unread-count") {
            val userId = call.userId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            call.respond(mapOf("unreadCount" to service.unreadCount(userId)))
        }

        patch("/direct-messages/{senderId}/read") {
            val userId = call.userId() ?: return@patch call.respond(HttpStatusCode.Unauthorized)
            val senderId = call.parameters["senderId"]?.toIntOrNull()
                ?: return@patch call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid sender id"))
            val updated = service.markDirectMessagesFromAsRead(userId, senderId)
            call.respond(NotificationBatchReadResponse(updated, service.unreadCount(userId)))
        }

        patch("/{id}/read") {
            val userId = call.userId() ?: return@patch call.respond(HttpStatusCode.Unauthorized)
            val id = call.parameters["id"]?.toIntOrNull() ?: return@patch call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid notification id"))
            val updated = service.markAsRead(userId, id)
            call.respond(NotificationReadResponse(updated, service.unreadCount(userId)))
        }

        post("/read") {
            val userId = call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val body = call.receive<MarkReadRequest>()
            val updated = service.markAsRead(userId, body.notificationId)
            call.respond(NotificationReadResponse(updated, service.unreadCount(userId)))
        }

        patch("/read-all") {
            val userId = call.userId() ?: return@patch call.respond(HttpStatusCode.Unauthorized)
            val updated = service.markAllAsRead(userId)
            call.respond(NotificationBatchReadResponse(updated, service.unreadCount(userId)))
        }
    }
}
