package backend.routes

import backend.services.MonitoringService
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.monitoringRoutes(service: MonitoringService) {
    route("/api/monitoring") {
        get("/devices") { call.respond(service.devices()) }
        get("/cpu") { call.respond(service.cpu()) }
        get("/alerts") { call.respond(service.alerts()) }
    }
}
