package backend.routes

import backend.models.TicketRequest
import backend.models.TicketStatusUpdate
import backend.security.userId
import backend.security.userRole
import backend.services.TicketService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.ticketRoutes(ticketService: TicketService) {
    route("/api/tickets") {
        post {
            val userId = call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            call.respond(HttpStatusCode.Created, ticketService.create(userId, call.receive<TicketRequest>()))
        }
        get {
            val uid = call.userId()
            val admin = call.userRole() == "admin"
            call.respond(ticketService.list(uid, admin))
        }
        get("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
            val ticket = ticketService.get(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(ticket)
        }
        put("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
            val updated = ticketService.update(id, call.receive<TicketRequest>(), call.userId()) ?: return@put call.respond(HttpStatusCode.NotFound)
            call.respond(updated)
        }
        put("/{id}/status") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
            val req = call.receive<TicketStatusUpdate>()
            val actor = "${call.userRole()}-${call.userId() ?: 0}"
            val ticket = ticketService.updateStatus(id, req.status, actor, call.userId()) ?: return@put call.respond(HttpStatusCode.NotFound)
            call.respond(ticket)
        }
    }
}
