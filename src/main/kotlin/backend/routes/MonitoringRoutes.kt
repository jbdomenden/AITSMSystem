package backend.routes

import backend.models.ApiErrorResponse
import backend.models.CollectorEnrollmentRequest
import backend.models.CollectorReportRequest
import backend.models.InventoryUpsertDetectedRequest
import backend.models.UserRole
import backend.repository.DeviceRepository
import backend.security.requireRole
import backend.services.AssetDetectionService
import backend.services.CollectorRateLimitException
import backend.services.InventoryService
import backend.services.InvalidCollectorTokenException
import backend.services.MonitoringService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.monitoringRoutes(
    service: MonitoringService,
    deviceRepository: DeviceRepository,
    assetDetectionService: AssetDetectionService,
    inventoryService: InventoryService
) {
    route("/api/monitoring") {
        post("/collector/reports") {
            val token = call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.takeIf { it.isNotBlank() }
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(401, "Collector token is required"))
            try {
                call.respond(HttpStatusCode.Accepted, service.ingest(token, call.receive<CollectorReportRequest>()))
            } catch (_: InvalidCollectorTokenException) {
                call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(401, "Collector token is invalid or revoked"))
            } catch (_: CollectorRateLimitException) {
                call.respond(HttpStatusCode.TooManyRequests, ApiErrorResponse(429, "Collector report rate limit exceeded"))
            }
        }
        get("/collector/commands") {
            val token = call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.takeIf { it.isNotBlank() }
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(401, "Collector token is required"))
            val command = try { service.nextCommand(token) } catch (_: InvalidCollectorTokenException) {
                return@get call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(401, "Collector token is invalid or revoked"))
            }
            if (command == null) call.respond(HttpStatusCode.NoContent) else call.respond(command)
        }

        get("/collectors") { if (call.requireRole(UserRole.ADMIN)) call.respond(service.collectors()) }
        post("/collectors") {
            if (!call.requireRole(UserRole.ADMIN)) return@post
            call.respond(HttpStatusCode.Created, service.enroll(call.receive<CollectorEnrollmentRequest>()))
        }
        delete("/collectors/{id}") {
            if (!call.requireRole(UserRole.ADMIN)) return@delete
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(400, "Collector id is required"))
            if (!runCatching { service.revoke(id) }.getOrDefault(false)) return@delete call.respond(HttpStatusCode.NotFound, ApiErrorResponse(404, "Collector not found"))
            call.respond(mapOf("revoked" to true))
        }

        post("/client-metrics") {
            call.respond(HttpStatusCode.Gone, ApiErrorResponse(410, "Legacy client metrics are disabled. Enroll an authenticated collector."))
        }
        get("/host-telemetry") {
            if (!call.requireRole(UserRole.ADMIN)) return@get
            call.respond(HttpStatusCode.Gone, ApiErrorResponse(410, "Cloud service host metrics are not LAN device telemetry."))
        }
        get("/lan-devices") { if (call.requireRole(UserRole.ADMIN)) call.respond(service.lanDevices()) }
        get("/lan-peer-ips") { if (call.requireRole(UserRole.ADMIN)) call.respond(service.lanPeerIps()) }
        get("/summary") { if (call.requireRole(UserRole.ADMIN)) call.respond(service.summary()) }
        post("/refresh-discovery") {
            if (!call.requireRole(UserRole.ADMIN)) return@post
            call.respond(HttpStatusCode.Accepted, service.requestDiscovery())
        }
        get("/devices") { if (call.requireRole(UserRole.ADMIN)) call.respond(service.devices()) }
        get("/cpu") { if (call.requireRole(UserRole.ADMIN)) call.respond(service.cpu()) }
        get("/alerts") { if (call.requireRole(UserRole.ADMIN)) call.respond(service.alerts()) }
    }
}
