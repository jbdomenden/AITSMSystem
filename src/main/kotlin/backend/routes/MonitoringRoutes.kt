package backend.routes

import backend.models.UserRole
import backend.models.ClientMetricsRequest
import backend.models.InventoryUpsertDetectedRequest
import backend.repository.DeviceRepository
import backend.security.requireRole
import backend.services.AssetDetectionService
import backend.services.InventoryService
import backend.services.MonitoringService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.monitoringRoutes(
    service: MonitoringService,
    deviceRepository: DeviceRepository,
    assetDetectionService: AssetDetectionService,
    inventoryService: InventoryService
) {
    route("/api/monitoring") {
        post("/client-metrics") {
            val remoteHost = call.request.local.remoteHost
            if (!assetDetectionService.matches(remoteHost)) {
                return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Client must be on the same LAN"))
            }

            val req = call.receive<ClientMetricsRequest>()
            if (!assetDetectionService.matches(req.ipAddress)) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Only LAN IP addresses are accepted"))
            }

            val saved = deviceRepository.upsertClientMetrics(req)
            runCatching {
                inventoryService.upsertDetectedAsset(
                    InventoryUpsertDetectedRequest(
                        deviceName = req.deviceName,
                        hostname = req.deviceName,
                        ipAddress = req.ipAddress,
                        assignedDepartment = req.department,
                        assignedUser = req.assignedUser,
                        status = if (req.status.isBlank()) "online" else req.status,
                        connectionSource = "agent"
                    )
                )
            }
            call.respond(HttpStatusCode.Accepted, saved)
        }

        get("/host-telemetry") {
            if (!call.requireRole(UserRole.ADMIN)) return@get
            call.respond(service.hostTelemetry())
        }
        get("/lan-devices") {
            if (!call.requireRole(UserRole.ADMIN)) return@get
            call.respond(service.lanDevices())
        }
        get("/lan-peer-ips") {
            if (!call.requireRole(UserRole.ADMIN)) return@get
            call.respond(service.lanPeerIps())
        }
        get("/summary") {
            if (!call.requireRole(UserRole.ADMIN)) return@get
            call.respond(service.summary())
        }
        post("/refresh-discovery") {
            if (!call.requireRole(UserRole.ADMIN)) return@post
            service.lanDevices()
                .filter { it.telemetrySourceType != "HOST" }
                .forEach { device ->
                    runCatching {
                        inventoryService.upsertDetectedAsset(
                            InventoryUpsertDetectedRequest(
                                deviceName = device.hostname,
                                hostname = device.hostname,
                                ipAddress = device.ipAddress,
                                status = if (device.reachable) "online" else "offline",
                                connectionSource = if (device.telemetryAvailable) "agent" else "lan-detected",
                                lastSeenAt = device.lastSeen
                            )
                        )
                    }
                }
            call.respond(service.refreshDiscovery())
        }

        get("/devices") {
            if (!call.requireRole(UserRole.ADMIN)) return@get
            call.respond(service.devices())
        }
        get("/cpu") {
            if (!call.requireRole(UserRole.ADMIN)) return@get
            call.respond(service.cpu())
        }
        get("/alerts") {
            if (!call.requireRole(UserRole.ADMIN)) return@get
            call.respond(service.alerts())
        }
    }
}
