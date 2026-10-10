package backend.routes

import backend.models.UserRole
import backend.security.requireRole
import backend.services.SLAService
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.slaRoutes(service: SLAService) {
    get("/api/sla") {
        if (!call.requireRole(UserRole.ADMIN)) return@get
        call.respond(service.policies())
    }
}
