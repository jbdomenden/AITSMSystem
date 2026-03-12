package backend.services

import backend.models.Device
import backend.repository.DeviceRepository

class MonitoringService(private val deviceRepository: DeviceRepository) {
    fun devices(): List<Device> = deviceRepository.list().filter { isLanIp(it.ipAddress) }
    fun cpu() = devices().map { mapOf("device" to it.deviceName, "cpu" to it.cpuUsage.toString()) }
    fun alerts() = devices().filter { it.cpuUsage > 85 || it.memoryUsage > 90 || it.status.equals("critical", true) }
        .map { mapOf("device" to it.deviceName, "message" to "High resource usage detected") }

    private fun isLanIp(ip: String): Boolean {
        if (ip.startsWith("10.") || ip.startsWith("192.168.")) return true
        val octets = ip.split(".")
        if (octets.size != 4) return false
        val second = octets.getOrNull(1)?.toIntOrNull() ?: return false
        return octets[0] == "172" && second in 16..31
    }
}
