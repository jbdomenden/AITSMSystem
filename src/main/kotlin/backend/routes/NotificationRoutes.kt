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

        patch("/{id}/read") {
            val userId = call.userId() ?: return@patch call.respond(HttpStatusCode.Unauthorized)
            val id = call.parameters["id"]?.toIntOrNull() ?: return@patch call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid notification id"))
            val updated = service.markAsRead(userId, id)
            call.respond(mapOf("updated" to updated, "unreadCount" to service.unreadCount(userId)))
        }

        post("/read") {
            val userId = call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val body = call.receive<MarkReadRequest>()
            val updated = service.markAsRead(userId, body.notificationId)
            call.respond(mapOf("updated" to updated, "unreadCount" to service.unreadCount(userId)))
        }

        patch("/read-all") {
            val userId = call.userId() ?: return@patch call.respond(HttpStatusCode.Unauthorized)
            val updated = service.markAllAsRead(userId)
            call.respond(mapOf("updated" to updated, "unreadCount" to service.unreadCount(userId)))
        }
    }
}
