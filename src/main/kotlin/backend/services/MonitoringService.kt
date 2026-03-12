package backend.services

import backend.models.HostTelemetryDto
import backend.models.LanDeviceDto
import backend.models.MonitoringSummaryDto
import backend.repository.DeviceRepository
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
        val now = LocalDateTime.now().toString()
        val host = hostTelemetry()
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

        val deviceRows = devices().map {
            val hasTelemetry = it.cpuUsage > 0 || it.memoryUsage > 0
            LanDeviceDto(
                id = "device-${it.id}",
                hostname = it.deviceName,
                ipAddress = it.ipAddress,
                reachable = true,
                telemetryAvailable = hasTelemetry,
                telemetrySourceType = if (hasTelemetry) "AGENT" else "UNAVAILABLE",
                cpuUsagePercent = if (hasTelemetry) it.cpuUsage.toDouble() else null,
                memoryUsagePercent = if (hasTelemetry) it.memoryUsage.toDouble() else null,
                lastSeen = it.lastSeen
            )
        }

        return listOf(hostDevice) + deviceRows
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
        // Honest LAN discovery placeholder: list local active interfaces for now.
        val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { ni -> ni.inetAddresses.toList().map { "${ni.displayName}:${it.hostAddress}" } }
            .filter { addr -> isLanIp(addr.substringAfterLast(':')) }

        return mapOf("message" to "Discovery refresh completed", "interfaces" to interfaces.joinToString(" | "))
    }

    private fun isLanIp(ip: String): Boolean {
        if (ip.startsWith("10.") || ip.startsWith("192.168.")) return true
        val octets = ip.split(".")
        if (octets.size != 4) return false
        val second = octets.getOrNull(1)?.toIntOrNull() ?: return false
        return octets[0] == "172" && second in 16..31
    }
}
