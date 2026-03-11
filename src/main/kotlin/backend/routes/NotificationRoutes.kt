package backend.routes

import backend.security.userId
import backend.services.NotificationService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.notificationRoutes(service: NotificationService) {
    get("/api/notifications") {
        val userId = call.userId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        call.respond(service.list(userId))
    }
}
