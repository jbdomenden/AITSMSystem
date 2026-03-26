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
import java.util.Locale
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

    fun lanPeerIps(): List<String> = discoverLanPeers()
        .map { it.ipAddress.trim() }
        .plus(devices().map { it.ipAddress.trim() })
        .filter { isLanIp(it) }
        .distinct()
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })

    private data class Peer(val hostname: String, val ipAddress: String)

    private fun discoverLanPeers(): List<Peer> {
        val ifacePeers = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { ni -> ni.inetAddresses.toList().mapNotNull { addr ->
                val ip = addr.hostAddress.substringBefore('%')
                if (!isLanIp(ip)) null else Peer(ni.displayName.ifBlank { "LAN Host" }, ip)
            }}

        val arpPeers = discoverArpPeers()

        return (ifacePeers + arpPeers).distinctBy { it.ipAddress }
    }

    private fun discoverArpPeers(): List<Peer> {
        val osName = System.getProperty("os.name", "").lowercase(Locale.getDefault())
        val commands = if ("windows" in osName) {
            listOf(listOf("cmd", "/c", "arp -a"))
        } else {
            listOf(
                listOf("sh", "-c", "ip neigh"),
                listOf("sh", "-c", "arp -an")
            )
        }

        return commands.asSequence()
            .flatMap { command -> executeCommand(command).asSequence() }
            .mapNotNull { parsePeerIp(it) }
            .distinct()
            .map { Peer("LAN Peer", it) }
            .toList()
    }

    private fun executeCommand(command: List<String>): List<String> = runCatching {
        val process = ProcessBuilder(command).start()
        BufferedReader(InputStreamReader(process.inputStream)).use { it.readLines() }
    }.getOrElse { emptyList() }

    private fun parsePeerIp(line: String): String? {
        val directToken = line.substringBefore(' ').trim().removePrefix("(").removeSuffix(")")
        if (isLanIp(directToken) && !line.contains("FAILED", ignoreCase = true)) {
            return directToken
        }

        val candidate = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
            .find(line)
            ?.value
            ?.trim()
            ?: return null

        return candidate.takeIf { isLanIp(it) }
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
