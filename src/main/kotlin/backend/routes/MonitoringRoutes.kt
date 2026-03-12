package backend.routes

import backend.security.requireRole
import backend.services.MonitoringService
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.monitoringRoutes(service: MonitoringService) {
    route("/api/monitoring") {
        get("/devices") {
            if (!call.requireRole("admin")) return@get
            call.respond(service.devices())
        }
        get("/cpu") {
            if (!call.requireRole("admin")) return@get
            call.respond(service.cpu())
        }
        get("/alerts") {
            if (!call.requireRole("admin")) return@get
            call.respond(service.alerts())
        }
    }
}
