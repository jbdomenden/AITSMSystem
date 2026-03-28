package backend.routes

import backend.models.DeviceRequest
import backend.models.InventoryUpsertDetectedRequest
import backend.models.PaginatedResponse
import backend.models.PaginationMeta
import backend.models.UserRole
import backend.repository.DeviceRepository
import backend.repository.UserRepository
import backend.security.requireRole
import backend.services.AssetDetectionService
import backend.services.InventoryService
import backend.services.MonitoringService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.net.InetAddress

@Serializable
private data class DeviceSyncResponse(
    val message: String,
    val devices: Int
)

private fun detectDeviceStatus(ip: String, assetDetectionService: AssetDetectionService): String {
    val normalizedIp = ip.trim()
    if (normalizedIp.isBlank()) return "Unreachable"
    if (!assetDetectionService.matches(normalizedIp)) return "Unreachable"

    return runCatching {
        val address = InetAddress.getByName(normalizedIp)
        if (address.isReachable(1500)) "Online" else "Offline"
    }.getOrElse { "Unreachable" }
}

fun Route.deviceRoutes(
    deviceRepository: DeviceRepository,
    userRepository: UserRepository,
    monitoringService: MonitoringService,
    assetDetectionService: AssetDetectionService,
    inventoryService: InventoryService
) {
    route("/api/devices") {
        post {
            if (!call.requireRole(UserRole.ADMIN)) return@post
            val request = call.receive<DeviceRequest>()
            val created = deviceRepository.create(request)
            runCatching {
                inventoryService.upsertDetectedAsset(
                    InventoryUpsertDetectedRequest(
                        deviceName = request.deviceName,
                        hostname = request.deviceName,
                        ipAddress = request.ipAddress,
                        assignedDepartment = request.department,
                        assignedUser = request.assignedUser,
                        status = request.status,
                        connectionSource = "manual"
                    )
                )
            }
            call.respond(HttpStatusCode.Created, created)
        }
        get {
            if (!call.requireRole(UserRole.ADMIN)) return@get
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
            val offset = (call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L).coerceAtLeast(0)
            val liveStatuses = monitoringService.lanDevices()
                .filter { it.telemetrySourceType != "HOST" }
                .associateBy { it.ipAddress }
            val page = deviceRepository.listWithLiveStatus(liveStatuses, limit, offset)
            call.respond(PaginatedResponse(page.items, PaginationMeta(page.total, limit, offset, (offset / limit).toInt() + 1)))
        }

        get("/ip-lookup") {
            if (!call.requireRole(UserRole.ADMIN)) return@get
            val ip = call.request.queryParameters["ip"]?.trim().orEmpty()
            if (ip.isBlank()) return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ip query param is required"))

            val hostname = runCatching { InetAddress.getByName(ip).hostName }
                .getOrElse { ip }
                .ifBlank { ip }
            val existing = deviceRepository.findByIp(ip)

            val actorUserId = call.request.headers["X-User-Id"]?.toIntOrNull()
            val actorUser = actorUserId?.let { userRepository.findById(it) }
            val actorRemoteIp = call.request.local.remoteHost

            val signedInUser = if (actorRemoteIp == ip) {
                actorUser?.fullName ?: actorUser?.email
            } else null

            call.respond(
                mapOf(
                    "ipAddress" to ip,
                    "deviceName" to (existing?.deviceName ?: hostname),
                    "assignedUser" to (existing?.assignedUser ?: signedInUser ?: ""),
                    "source" to when {
                        existing != null -> "device-registry"
                        signedInUser != null -> "signed-in-user-ip"
                        else -> "dns"
                    },
                    "suggestedStatus" to (existing?.status ?: detectDeviceStatus(ip, assetDetectionService))
                )
            )
        }


        post("/sync-from-monitoring") {
            if (!call.requireRole(UserRole.ADMIN)) return@post
            val peers = monitoringService.lanDevices()
                .filter { it.telemetrySourceType != "HOST" }
                .map {
                    DeviceRepository.DiscoveredPeer(
                        ipAddress = it.ipAddress,
                        hostname = it.hostname,
                        reachable = it.reachable
                    )
                }
            val synced = deviceRepository.syncDiscoveredDevices(peers)
            peers.forEach { peer ->
                runCatching {
                    inventoryService.upsertDetectedAsset(
                        InventoryUpsertDetectedRequest(
                            hostname = peer.hostname,
                            deviceName = peer.hostname,
                            ipAddress = peer.ipAddress,
                            status = if (peer.reachable) "online" else "offline",
                            connectionSource = "lan-detected"
                        )
                    )
                }
            }
            call.respond(DeviceSyncResponse(message = "Device registry synchronized", devices = synced.size))
        }

        put("/{id}") {
            if (!call.requireRole(UserRole.ADMIN)) return@put
            val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
            val request = call.receive<DeviceRequest>()
            val updated = deviceRepository.update(id, request) ?: return@put call.respond(HttpStatusCode.NotFound)
            runCatching {
                inventoryService.upsertDetectedAsset(
                    InventoryUpsertDetectedRequest(
                        deviceName = request.deviceName,
                        hostname = request.deviceName,
                        ipAddress = request.ipAddress,
                        assignedDepartment = request.department,
                        assignedUser = request.assignedUser,
                        status = request.status,
                        connectionSource = "manual"
                    )
                )
            }
            call.respond(updated)
        }

        delete("/{id}") {
            if (!call.requireRole(UserRole.ADMIN)) return@delete
            val id = call.parameters["id"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)
            if (!deviceRepository.delete(id)) return@delete call.respond(HttpStatusCode.NotFound)
            call.respond(mapOf("message" to "Device deleted"))
        }
    }
}
