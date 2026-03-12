package backend.routes

import backend.models.ClientMetricsRequest
import backend.repository.DeviceRepository
import backend.security.requireRole
import backend.services.MonitoringService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.monitoringRoutes(service: MonitoringService, deviceRepository: DeviceRepository) {
    route("/api/monitoring") {
        // Client agents on the same LAN can post their current metrics.
        post("/client-metrics") {
            val remoteHost = call.request.local.remoteHost
            if (!isLanIp(remoteHost)) {
                return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Client must be on the same LAN"))
            }

            val req = call.receive<ClientMetricsRequest>()
            if (!isLanIp(req.ipAddress)) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Only LAN IP addresses are accepted"))
            }

            val saved = deviceRepository.upsertClientMetrics(req)
            call.respond(HttpStatusCode.Accepted, saved)
        }

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

private fun isLanIp(ip: String): Boolean {
    if (ip.startsWith("10.") || ip.startsWith("192.168.")) return true
    val octets = ip.split(".")
    if (octets.size != 4) return false
    val second = octets.getOrNull(1)?.toIntOrNull() ?: return false
    return octets[0] == "172" && second in 16..31
}
