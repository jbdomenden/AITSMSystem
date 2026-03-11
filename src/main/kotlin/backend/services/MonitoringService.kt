package backend.services

import backend.models.Device
import backend.repository.DeviceRepository

class MonitoringService(private val deviceRepository: DeviceRepository) {
    fun devices(): List<Device> = deviceRepository.list().filter { it.ipAddress.startsWith("10.") || it.ipAddress.startsWith("192.168.") || it.ipAddress.startsWith("172.") }
    fun cpu() = devices().map { mapOf("device" to it.deviceName, "cpu" to it.cpuUsage.toString()) }
    fun alerts() = devices().filter { it.cpuUsage > 85 || it.memoryUsage > 90 || it.status.equals("critical", true) }
        .map { mapOf("device" to it.deviceName, "message" to "High resource usage detected") }
}
