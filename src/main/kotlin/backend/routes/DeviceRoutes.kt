package backend.routes

import backend.models.DeviceRequest
import backend.repository.DeviceRepository
import backend.repository.UserRepository
import backend.security.requireRole
import backend.services.MonitoringService
import io.ktor.http.*
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

private fun isLanIp(ip: String): Boolean {
    val normalizedIp = ip.trim()
    return normalizedIp.startsWith("10.")
        || normalizedIp.startsWith("192.168.")
        || normalizedIp.startsWith("172.16.")
        || normalizedIp.startsWith("172.17.")
        || normalizedIp.startsWith("172.18.")
        || normalizedIp.startsWith("172.19.")
        || normalizedIp.startsWith("172.20.")
        || normalizedIp.startsWith("172.21.")
        || normalizedIp.startsWith("172.22.")
        || normalizedIp.startsWith("172.23.")
        || normalizedIp.startsWith("172.24.")
        || normalizedIp.startsWith("172.25.")
        || normalizedIp.startsWith("172.26.")
        || normalizedIp.startsWith("172.27.")
        || normalizedIp.startsWith("172.28.")
        || normalizedIp.startsWith("172.29.")
        || normalizedIp.startsWith("172.30.")
        || normalizedIp.startsWith("172.31.")
}

private fun detectDeviceStatus(ip: String): String {
    val normalizedIp = ip.trim()
    if (normalizedIp.isBlank()) return "Not Reachable"
    return if (isLanIp(normalizedIp)) "Online" else "Not Reachable"
}

fun Route.deviceRoutes(deviceRepository: DeviceRepository, userRepository: UserRepository, monitoringService: MonitoringService) {
    route("/api/devices") {
        post {
            if (!call.requireRole("admin")) return@post
            val created = deviceRepository.create(call.receive<DeviceRequest>())
            call.respond(HttpStatusCode.Created, created)
        }
        get {
            if (!call.requireRole("admin")) return@get
            val liveStatuses = monitoringService.lanDevices()
                .filter { it.telemetrySourceType != "HOST" }
                .associateBy { it.ipAddress }
            call.respond(deviceRepository.listWithLiveStatus(liveStatuses))
        }

        get("/ip-lookup") {
            if (!call.requireRole("admin")) return@get
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
                    "suggestedStatus" to (existing?.status ?: detectDeviceStatus(ip))
                )
            )
        }


        post("/sync-from-monitoring") {
            if (!call.requireRole("admin")) return@post
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
            call.respond(DeviceSyncResponse(message = "Device registry synchronized", devices = synced.size))
        }

        put("/{id}") {
            if (!call.requireRole("admin")) return@put
            val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
            val updated = deviceRepository.update(id, call.receive<DeviceRequest>()) ?: return@put call.respond(HttpStatusCode.NotFound)
            call.respond(updated)
        }

        delete("/{id}") {
            if (!call.requireRole("admin")) return@delete
            val id = call.parameters["id"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)
            if (!deviceRepository.delete(id)) return@delete call.respond(HttpStatusCode.NotFound)
            call.respond(mapOf("message" to "Device deleted"))
        }
    }
}
