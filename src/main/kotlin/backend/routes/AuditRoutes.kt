package backend.routes

import backend.repository.AuditRepository
import backend.security.Permission
import backend.security.requirePermission
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.auditRoutes(repository: AuditRepository) {
    get("/api/audit-events") {
        if (!call.requirePermission(Permission.AUDIT_READ)) return@get
        val action = call.request.queryParameters["action"]?.trim()?.lowercase()
        val result = call.request.queryParameters["result"]?.trim()?.uppercase()
        call.respond(repository.all().asSequence()
            .filter { action.isNullOrBlank() || it.action.lowercase().contains(action) }
            .filter { result.isNullOrBlank() || it.result == result }
            .take(500).toList())
    }
}
