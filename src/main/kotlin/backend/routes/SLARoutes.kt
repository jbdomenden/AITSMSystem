package backend.routes

import backend.models.SLAUpdateRequest
import backend.models.UserRole
import backend.security.Permission
import backend.security.requirePermission
import backend.security.requireReauthentication
import backend.security.requireRole
import backend.security.userId
import backend.services.ReauthScope
import backend.services.ReauthenticationService
import backend.services.SLAService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.slaRoutes(service: SLAService, reauthentication: ReauthenticationService) {
    get("/api/sla") {
        if (!call.requireRole(UserRole.ADMIN)) return@get
        call.respond(service.policies())
    }
    put("/api/sla/{priority}") {
        if (!call.requirePermission(Permission.SECURITY_MANAGE)) return@put
        if (!call.requireReauthentication(reauthentication, ReauthScope.SECURITY_CONFIG)) return@put
        val actorId = call.userId() ?: return@put call.respond(HttpStatusCode.Unauthorized)
        val priority = call.parameters["priority"] ?: return@put call.respond(HttpStatusCode.BadRequest)
        val request = call.receive<SLAUpdateRequest>()
        call.respond(service.update(priority, request.responseTime, request.resolutionTime, actorId))
    }
}
