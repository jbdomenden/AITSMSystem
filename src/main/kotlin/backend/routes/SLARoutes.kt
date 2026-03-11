package backend.routes

import backend.services.SLAService
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.slaRoutes(service: SLAService) {
    get("/api/sla") { call.respond(service.policies()) }
}
