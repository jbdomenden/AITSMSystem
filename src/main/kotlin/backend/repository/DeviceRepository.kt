package backend.repository

import backend.config.DevicesTable
import backend.models.ClientMetricsRequest
import backend.models.Device
import backend.models.DeviceRequest
import backend.models.LanDeviceDto
import backend.services.AssetDetectionService
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.net.InetAddress
import java.time.LocalDateTime

class DeviceRepository(private val assetDetectionService: AssetDetectionService) {
    data class DiscoveredPeer(val ipAddress: String, val hostname: String, val reachable: Boolean)

    private fun validateDeviceRequest(req: DeviceRequest) {
        require(req.deviceName.trim().isNotBlank()) { "Device name is required" }
        require(req.department.trim().isNotBlank()) { "Department is required" }
        require(req.assignedUser.trim().isNotBlank()) { "Assigned user is required" }
        require(isValidIpv4(req.ipAddress.trim())) { "Invalid IP address format" }
    }

    private fun validateClientMetricsRequest(req: ClientMetricsRequest) {
        require(req.deviceName.trim().isNotBlank()) { "Device name is required" }
        require(req.department.trim().isNotBlank()) { "Department is required" }
        require(req.assignedUser.trim().isNotBlank()) { "Assigned user is required" }
        require(isValidIpv4(req.ipAddress.trim())) { "Invalid IP address format" }
        require(req.cpuUsage in 0..100) { "CPU usage must be between 0 and 100" }
        require(req.memoryUsage in 0..100) { "Memory usage must be between 0 and 100" }
    }

    private fun isValidIpv4(ipAddress: String): Boolean {
        val parts = ipAddress.split('.')
        if (parts.size != 4) return false
        return parts.all { token ->
            val number = token.toIntOrNull() ?: return false
            number in 0..255
        }
    }

    private fun detectReachabilityStatus(ipAddress: String): String {
        val normalizedIp = ipAddress.trim()
        if (normalizedIp.isBlank()) return "Unreachable"
        return runCatching {
            val address = InetAddress.getByName(normalizedIp)
            if (address.isReachable(1500)) "Online" else "Offline"
        }.getOrElse { "Unreachable" }
    }

    private fun deriveStatus(fallbackStatus: String = "Online", cpuUsage: Int? = null, memoryUsage: Int? = null): String {
        val cpu = cpuUsage ?: 0
        val memory = memoryUsage ?: 0
        return when {
            cpu >= 85 || memory >= 90 -> "Critical"
            cpu >= 70 || memory >= 80 -> "High Risk"
            else -> fallbackStatus
        }
    }

    fun create(req: DeviceRequest): Device = transaction {
        val normalized = req.copy(
            deviceName = req.deviceName.trim(),
            ipAddress = req.ipAddress.trim(),
            department = req.department.trim(),
            assignedUser = req.assignedUser.trim(),
            status = req.status.trim()
        )
        validateDeviceRequest(normalized)
        require(assetDetectionService.matches(normalized.ipAddress)) { "Only LAN devices are monitorable." }
        val derivedStatus = detectReachabilityStatus(normalized.ipAddress)
        val id = DevicesTable.insert {
            it[deviceName] = normalized.deviceName
            it[ipAddress] = normalized.ipAddress
            it[department] = normalized.department
            it[assignedUser] = normalized.assignedUser
            it[cpuUsage] = (15..70).random()
            it[memoryUsage] = (20..80).random()
            it[status] = derivedStatus
            it[lastSeen] = LocalDateTime.now()
        }[DevicesTable.id]
        list().first { it.id == id }
    }

    fun list(limit: Int = 100, offset: Long = 0): List<Device> = transaction {
        DevicesTable.selectAll().limit(limit, offset).map(::toDevice)
    }

    fun listWithLiveStatus(liveDevices: Map<String, LanDeviceDto>, limit: Int = 100, offset: Long = 0): PagedResult<Device> = transaction {
        val base = DevicesTable.selectAll()
        val items = base.limit(limit, offset).map { row ->
            val current = toDevice(row)
            val live = liveDevices[current.ipAddress]
            if (live == null) {
                current
            } else {
                current.copy(
                    cpuUsage = live.cpuUsagePercent?.toInt()?.coerceIn(0, 100) ?: current.cpuUsage,
                    memoryUsage = live.memoryUsagePercent?.toInt()?.coerceIn(0, 100) ?: current.memoryUsage,
                    status = deriveLiveStatus(live),
                    lastSeen = if (live.reachable) live.lastSeen.ifBlank { current.lastSeen } else current.lastSeen
                )
            }
        }
        PagedResult(items, base.count())
    }

    fun findByIp(ip: String): Device? = transaction {
        DevicesTable.selectAll().where { DevicesTable.ipAddress eq ip.trim() }.singleOrNull()?.let(::toDevice)
    }


    fun upsertClientMetrics(req: ClientMetricsRequest): Device = transaction {
        val normalized = req.copy(
            deviceName = req.deviceName.trim(),
            ipAddress = req.ipAddress.trim(),
            department = req.department.trim(),
            assignedUser = req.assignedUser.trim(),
            status = req.status.trim()
        )
        validateClientMetricsRequest(normalized)
        require(assetDetectionService.matches(normalized.ipAddress)) { "Only LAN devices are monitorable." }
        val derivedStatus = deriveStatus(cpuUsage = normalized.cpuUsage, memoryUsage = normalized.memoryUsage)

        val existing = DevicesTable.selectAll().where { DevicesTable.ipAddress eq normalized.ipAddress }.singleOrNull()
        if (existing == null) {
            val id = DevicesTable.insert {
                it[deviceName] = normalized.deviceName
                it[ipAddress] = normalized.ipAddress
                it[department] = normalized.department
                it[assignedUser] = normalized.assignedUser
                it[cpuUsage] = normalized.cpuUsage.coerceIn(0, 100)
                it[memoryUsage] = normalized.memoryUsage.coerceIn(0, 100)
                it[status] = derivedStatus
                it[lastSeen] = LocalDateTime.now()
            }[DevicesTable.id]
            return@transaction list().first { it.id == id }
        }

        DevicesTable.update({ DevicesTable.id eq existing[DevicesTable.id] }) {
            it[deviceName] = normalized.deviceName
            it[department] = normalized.department
            it[assignedUser] = normalized.assignedUser
            it[cpuUsage] = normalized.cpuUsage.coerceIn(0, 100)
            it[memoryUsage] = normalized.memoryUsage.coerceIn(0, 100)
            it[status] = derivedStatus
            it[lastSeen] = LocalDateTime.now()
        }

        DevicesTable.selectAll().where { DevicesTable.id eq existing[DevicesTable.id] }.single().let(::toDevice)
    }

    fun syncDiscoveredDevices(discovered: List<DiscoveredPeer>): List<Device> = transaction {
        val now = LocalDateTime.now()
        discovered.forEach { peer ->
            if (!assetDetectionService.matches(peer.ipAddress)) return@forEach
            val row = DevicesTable.selectAll().where { DevicesTable.ipAddress eq peer.ipAddress }.singleOrNull() ?: return@forEach
            DevicesTable.update({ DevicesTable.id eq row[DevicesTable.id] }) {
                it[deviceName] = peer.hostname.ifBlank { row[DevicesTable.deviceName] }
                it[status] = if (peer.reachable) deriveStatus(cpuUsage = row[DevicesTable.cpuUsage], memoryUsage = row[DevicesTable.memoryUsage]) else "Unreachable"
                it[lastSeen] = if (peer.reachable) now else row[DevicesTable.lastSeen]
            }
        }
        list()
    }

    fun update(id: Int, req: DeviceRequest): Device? = transaction {
        val normalized = req.copy(
            deviceName = req.deviceName.trim(),
            ipAddress = req.ipAddress.trim(),
            department = req.department.trim(),
            assignedUser = req.assignedUser.trim(),
            status = req.status.trim()
        )
        validateDeviceRequest(normalized)
        require(assetDetectionService.matches(normalized.ipAddress)) { "Only LAN devices are monitorable." }
        val existing = DevicesTable.selectAll().where { DevicesTable.id eq id }.singleOrNull()
        val derivedStatus = deriveStatus(
            fallbackStatus = detectReachabilityStatus(normalized.ipAddress),
            cpuUsage = existing?.get(DevicesTable.cpuUsage),
            memoryUsage = existing?.get(DevicesTable.memoryUsage)
        )
        DevicesTable.update({ DevicesTable.id eq id }) {
            it[deviceName] = normalized.deviceName
            it[ipAddress] = normalized.ipAddress
            it[department] = normalized.department
            it[assignedUser] = normalized.assignedUser
            it[status] = derivedStatus
            it[lastSeen] = LocalDateTime.now()
        }
        DevicesTable.selectAll().where { DevicesTable.id eq id }.singleOrNull()?.let(::toDevice)
    }

    fun delete(id: Int): Boolean = transaction {
        DevicesTable.deleteWhere { DevicesTable.id eq id } > 0
    }


    private fun deriveLiveStatus(live: LanDeviceDto): String {
        if (!live.reachable) return "Unreachable"

        val cpu = live.cpuUsagePercent ?: 0.0
        val memory = live.memoryUsagePercent ?: 0.0
        return when {
            cpu >= 90.0 || memory >= 90.0 -> "Critical"
            live.telemetryAvailable -> "Online"
            else -> "Offline"
        }
    }

    private fun toDevice(row: ResultRow) = Device(
        id = row[DevicesTable.id],
        deviceName = row[DevicesTable.deviceName],
        ipAddress = row[DevicesTable.ipAddress],
        department = row[DevicesTable.department],
        assignedUser = row[DevicesTable.assignedUser],
        cpuUsage = row[DevicesTable.cpuUsage],
        memoryUsage = row[DevicesTable.memoryUsage],
        status = row[DevicesTable.status],
        lastSeen = row[DevicesTable.lastSeen].toString()
    )
}
