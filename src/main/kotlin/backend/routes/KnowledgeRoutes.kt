package backend.routes

import backend.models.KnowledgeRequest
import backend.models.PaginatedResponse
import backend.models.PaginationMeta
import backend.models.UserRole
import backend.security.requireRole
import backend.security.userId
import backend.services.KnowledgeService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.knowledgeRoutes(service: KnowledgeService) {
    route("/api/knowledge") {
        get {
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
            val offset = (call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L).coerceAtLeast(0)
            val page = service.list(limit, offset)
            call.respond(PaginatedResponse(page.items, PaginationMeta(page.total, limit, offset, (offset / limit).toInt() + 1)))
        }
        post {
            if (!call.requireRole(UserRole.ADMIN)) return@post
            val created = service.create(call.receive<KnowledgeRequest>(), call.userId())
            call.respond(HttpStatusCode.Created, created)
        }
        put("/{id}") {
            if (!call.requireRole(UserRole.ADMIN)) return@put
            val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
            val updated = service.update(id, call.receive<KnowledgeRequest>(), call.userId()) ?: return@put call.respond(HttpStatusCode.NotFound)
            call.respond(updated)
        }
        delete("/{id}") {
            if (!call.requireRole(UserRole.ADMIN)) return@delete
            val id = call.parameters["id"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)
            if (!service.delete(id, call.userId())) return@delete call.respond(HttpStatusCode.NotFound)
            call.respond(mapOf("message" to "Article deleted"))
        }
    }
}
