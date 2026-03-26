package backend.services

import backend.models.HostTelemetryDto
import backend.models.LanDeviceDto
import backend.models.MonitoringSummaryDto
import backend.repository.DeviceRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.net.NetworkInterface
import java.time.LocalDateTime

class MonitoringService(private val deviceRepository: DeviceRepository) {
    fun devices() = deviceRepository.list().filter { isLanIp(it.ipAddress) }
    fun cpu() = devices().map { mapOf("device" to it.deviceName, "cpu" to it.cpuUsage.toString()) }
    fun alerts() = devices().filter { it.cpuUsage > 85 || it.memoryUsage > 90 || it.status.equals("critical", true) }
        .map { mapOf("device" to it.deviceName, "message" to "High resource usage detected") }

    fun hostTelemetry(): HostTelemetryDto {
        val osBean = ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean::class.java)
        val cpu = (osBean.systemCpuLoad.takeIf { it >= 0 } ?: 0.0) * 100
        val totalMem = osBean.totalPhysicalMemorySize.coerceAtLeast(1)
        val freeMem = osBean.freePhysicalMemorySize.coerceAtLeast(0)
        val usedMem = (totalMem - freeMem).coerceAtLeast(0)
        val memPct = usedMem.toDouble() / totalMem * 100

        val host = InetAddress.getLocalHost()
        return HostTelemetryDto(
            cpuUsagePercent = (cpu * 10).toInt() / 10.0,
            memoryUsagePercent = (memPct * 10).toInt() / 10.0,
            totalMemoryBytes = totalMem,
            usedMemoryBytes = usedMem,
            hostname = host.hostName,
            ipAddress = host.hostAddress,
            timestamp = LocalDateTime.now().toString()
        )
    }

    fun lanDevices(): List<LanDeviceDto> {
        val host = hostTelemetry()
        val reachabilityCache = mutableMapOf<String, Boolean>()
        fun isReachable(ip: String): Boolean = reachabilityCache.getOrPut(ip) { isDeviceReachable(ip) }

        val hostDevice = LanDeviceDto(
            id = "host-${host.ipAddress}",
            hostname = host.hostname,
            ipAddress = host.ipAddress,
            reachable = true,
            telemetryAvailable = true,
            telemetrySourceType = "HOST",
            cpuUsagePercent = host.cpuUsagePercent,
            memoryUsagePercent = host.memoryUsagePercent,
            lastSeen = host.timestamp
        )

        val fromAgents = devices().map {
            val hasTelemetry = it.cpuUsage > 0 || it.memoryUsage > 0
            val reachable = isReachable(it.ipAddress)
            LanDeviceDto(
                id = "device-${it.id}",
                hostname = it.deviceName,
                ipAddress = it.ipAddress,
                reachable = reachable,
                telemetryAvailable = hasTelemetry,
                telemetrySourceType = if (hasTelemetry) "AGENT" else "UNAVAILABLE",
                cpuUsagePercent = if (hasTelemetry) it.cpuUsage.toDouble() else null,
                memoryUsagePercent = if (hasTelemetry) it.memoryUsage.toDouble() else null,
                lastSeen = it.lastSeen
            )
        }

        val discovered = discoverLanPeers().map { peer ->
            val reachable = isReachable(peer.ipAddress)
            LanDeviceDto(
                id = "peer-${peer.ipAddress}",
                hostname = peer.hostname,
                ipAddress = peer.ipAddress,
                reachable = reachable,
                telemetryAvailable = false,
                telemetrySourceType = "DISCOVERED",
                cpuUsagePercent = null,
                memoryUsagePercent = null,
                lastSeen = LocalDateTime.now().toString()
            )
        }

        return (listOf(hostDevice) + fromAgents + discovered)
            .distinctBy { it.ipAddress }
            .sortedBy { it.ipAddress }
    }

    fun summary(): MonitoringSummaryDto {
        val devices = lanDevices()
        val host = hostTelemetry()
        return MonitoringSummaryDto(
            totalDiscovered = devices.size,
            monitoredDevices = devices.count { it.reachable },
            telemetryAvailableDevices = devices.count { it.telemetryAvailable },
            hostTelemetry = host,
            timestamp = LocalDateTime.now().toString()
        )
    }

    fun refreshDiscovery(): Map<String, String> {
        val peers = discoverLanPeers().joinToString(" | ") { "${it.hostname}:${it.ipAddress}" }
        return mapOf("message" to "Discovery refresh completed", "interfaces" to peers)
    }

    private data class Peer(val hostname: String, val ipAddress: String)

    private fun discoverLanPeers(): List<Peer> {
        val ifacePeers = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { ni -> ni.inetAddresses.toList().mapNotNull { addr ->
                val ip = addr.hostAddress.substringBefore('%')
                if (!isLanIp(ip)) null else Peer(ni.displayName.ifBlank { "LAN Host" }, ip)
            }}

        val ipNeighPeers = runCatching {
            val process = ProcessBuilder("sh", "-c", "ip neigh").start()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readLines().mapNotNull { line ->
                    val ip = line.substringBefore(' ').trim()
                    if (!isLanIp(ip)) return@mapNotNull null
                    val state = line.substringAfterLast(' ', "")
                    if (state.equals("FAILED", true)) return@mapNotNull null
                    Peer("LAN Peer", ip)
                }
            }
        }.getOrElse { emptyList() }

        return (ifacePeers + ipNeighPeers).distinctBy { it.ipAddress }
    }

    private fun isLanIp(ip: String): Boolean {
        if (ip.startsWith("10.") || ip.startsWith("192.168.")) return true
        val octets = ip.split(".")
        if (octets.size != 4) return false
        val second = octets.getOrNull(1)?.toIntOrNull() ?: return false
        return octets[0] == "172" && second in 16..31
    }

    private fun isDeviceReachable(ip: String): Boolean = runCatching {
        if (!isLanIp(ip)) return false
        InetAddress.getByName(ip).isReachable(1200)
    }.getOrDefault(false)
}
