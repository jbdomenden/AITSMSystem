package backend.routes

import backend.models.SendMessageRequest
import backend.security.requireAuthenticated
import backend.security.userId
import backend.services.ColleagueService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.colleagueRoutes(service: ColleagueService) {
    route("/api/colleagues") {
        get("/search") { if (!call.requireAuthenticated()) return@get; call.respond(service.search(call.userId()!!, call.request.queryParameters["q"] ?: "")) }
        get { if (!call.requireAuthenticated()) return@get; call.respond(service.colleagues(call.userId()!!)) }
        get("/requests") { if (!call.requireAuthenticated()) return@get; call.respond(service.requests(call.userId()!!)) }
        post("/{id}/request") { if (!call.requireAuthenticated()) return@post; val id = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest); call.respond(HttpStatusCode.Created, service.sendRequest(call.userId()!!, id)) }
        post("/requests/{id}/accept") { if (!call.requireAuthenticated()) return@post; val id = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest); call.respond(service.respond(call.userId()!!, id, true)) }
        post("/requests/{id}/decline") { if (!call.requireAuthenticated()) return@post; val id = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest); call.respond(service.respond(call.userId()!!, id, false)) }
        delete("/requests/{id}") { if (!call.requireAuthenticated()) return@delete; val id = call.parameters["id"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest); service.cancel(call.userId()!!, id); call.respond(mapOf("message" to "Request cancelled")) }
        delete("/{id}") { if (!call.requireAuthenticated()) return@delete; val id = call.parameters["id"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest); service.remove(call.userId()!!, id); call.respond(mapOf("message" to "Colleague removed")) }
        get("/{id}/messages") { if (!call.requireAuthenticated()) return@get; val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest); call.respond(service.messages(call.userId()!!, id)) }
        post("/{id}/messages") { if (!call.requireAuthenticated()) return@post; val id = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest); call.respond(HttpStatusCode.Created, service.sendMessage(call.userId()!!, id, call.receive<SendMessageRequest>().body)) }
        get("/messages/conversations") { if (!call.requireAuthenticated()) return@get; call.respond(service.conversations(call.userId()!!)) }
    }
}
