package backend.routes

import backend.models.PaginatedResponse
import backend.models.PaginationMeta
import backend.models.TicketRequest
import backend.models.TicketStatusUpdate
import backend.models.UserRole
import backend.security.requireRole
import backend.security.userId
import backend.security.userRole
import backend.services.TicketService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private const val DEFAULT_LIMIT = 20
private const val MAX_LIMIT = 100

fun Route.ticketRoutes(ticketService: TicketService) {
    route("/api/tickets") {
        post {
            if (!call.requireRole(UserRole.END_USER)) return@post
            val userId = call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            call.respond(HttpStatusCode.Created, ticketService.create(userId, call.receive<TicketRequest>()))
        }
        get {
            val uid = call.userId()
            val admin = setOf(UserRole.ADMIN, UserRole.SUPERADMIN).contains(call.userRole())
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
            val offset = (call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L).coerceAtLeast(0L)
            val page = ticketService.list(uid, admin, limit, offset)
            call.respond(
                PaginatedResponse(
                    data = page.items,
                    meta = PaginationMeta(
                        totalCount = page.total,
                        pageSize = limit,
                        offset = offset,
                        currentPage = (offset / limit).toInt() + 1
                    )
                )
            )
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
            val ticket = ticketService.updateStatus(id, req.status, actor, call.userId(), call.userRole()) ?: return@put call.respond(HttpStatusCode.NotFound)
            call.respond(ticket)
        }
    }
}
