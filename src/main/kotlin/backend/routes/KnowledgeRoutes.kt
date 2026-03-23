package backend.routes

import backend.models.KnowledgeRequest
import backend.security.userId
import backend.services.KnowledgeService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.knowledgeRoutes(service: KnowledgeService) {
    route("/api/knowledge") {
        get { call.respond(service.list()) }
        post {
            val created = service.create(call.receive<KnowledgeRequest>(), call.userId())
            call.respond(HttpStatusCode.Created, created)
        }
        put("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
            val updated = service.update(id, call.receive<KnowledgeRequest>(), call.userId()) ?: return@put call.respond(HttpStatusCode.NotFound)
            call.respond(updated)
        }
        delete("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)
            if (!service.delete(id, call.userId())) return@delete call.respond(HttpStatusCode.NotFound)
            call.respond(mapOf("message" to "Article deleted"))
        }
    }
}
