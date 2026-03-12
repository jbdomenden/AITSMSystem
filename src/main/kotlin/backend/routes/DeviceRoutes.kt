package backend.routes

import backend.models.DeviceRequest
import backend.repository.DeviceRepository
import backend.security.requireRole
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.deviceRoutes(deviceRepository: DeviceRepository) {
    route("/api/devices") {
        post {
            if (!call.requireRole("admin")) return@post
            val created = deviceRepository.create(call.receive<DeviceRequest>())
            call.respond(HttpStatusCode.Created, created)
        }
        get {
            if (!call.requireRole("admin")) return@get
            call.respond(deviceRepository.list())
        }
        put("/{id}") {
            if (!call.requireRole("admin")) return@put
            val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
            val updated = deviceRepository.update(id, call.receive<DeviceRequest>()) ?: return@put call.respond(HttpStatusCode.NotFound)
            call.respond(updated)
        }
    }
}
