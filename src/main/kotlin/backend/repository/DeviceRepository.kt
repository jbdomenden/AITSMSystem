package backend.repository

import backend.config.DevicesTable
import backend.models.ClientMetricsRequest
import backend.models.Device
import backend.models.DeviceRequest
import backend.models.LanDeviceDto
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.net.InetAddress
import java.time.LocalDateTime

class DeviceRepository {
    data class DiscoveredPeer(val ipAddress: String, val hostname: String, val reachable: Boolean)
    private val privateNetworks = listOf("10.", "192.168.", "172.16.", "172.17.", "172.18.", "172.19.", "172.20.", "172.21.", "172.22.", "172.23.", "172.24.", "172.25.", "172.26.", "172.27.", "172.28.", "172.29.", "172.30.", "172.31.")

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
        require(privateNetworks.any { req.ipAddress.startsWith(it) }) { "Only LAN devices are monitorable." }
        val derivedStatus = deriveStatus()
        val id = DevicesTable.insert {
            it[deviceName] = req.deviceName
            it[ipAddress] = req.ipAddress
            it[department] = req.department
            it[assignedUser] = req.assignedUser
            it[cpuUsage] = (15..70).random()
            it[memoryUsage] = (20..80).random()
            it[status] = derivedStatus
            it[lastSeen] = LocalDateTime.now()
        }[DevicesTable.id]
        list().first { it.id == id }
    }

    fun list(): List<Device> = transaction { DevicesTable.selectAll().map(::toDevice) }

    fun listWithLiveStatus(liveDevices: Map<String, LanDeviceDto>): List<Device> = transaction {
        DevicesTable.selectAll().map { row ->
            val base = toDevice(row)
            val live = liveDevices[base.ipAddress]
            if (live == null) {
                base
            } else {
                base.copy(
                    cpuUsage = live.cpuUsagePercent?.toInt()?.coerceIn(0, 100) ?: base.cpuUsage,
                    memoryUsage = live.memoryUsagePercent?.toInt()?.coerceIn(0, 100) ?: base.memoryUsage,
                    status = deriveLiveStatus(live),
                    lastSeen = live.lastSeen.ifBlank { base.lastSeen }
                )
            }
        }
    }

    fun findByIp(ip: String): Device? = transaction {
        DevicesTable.selectAll().where { DevicesTable.ipAddress eq ip.trim() }.singleOrNull()?.let(::toDevice)
    }


    fun upsertClientMetrics(req: ClientMetricsRequest): Device = transaction {
        require(privateNetworks.any { req.ipAddress.startsWith(it) }) { "Only LAN devices are monitorable." }
        val derivedStatus = deriveStatus(cpuUsage = req.cpuUsage, memoryUsage = req.memoryUsage)

        val existing = DevicesTable.selectAll().where { DevicesTable.ipAddress eq req.ipAddress }.singleOrNull()
        if (existing == null) {
            val id = DevicesTable.insert {
                it[deviceName] = req.deviceName
                it[ipAddress] = req.ipAddress
                it[department] = req.department
                it[assignedUser] = req.assignedUser
                it[cpuUsage] = req.cpuUsage.coerceIn(0, 100)
                it[memoryUsage] = req.memoryUsage.coerceIn(0, 100)
                it[status] = derivedStatus
                it[lastSeen] = LocalDateTime.now()
            }[DevicesTable.id]
            return@transaction list().first { it.id == id }
        }

        DevicesTable.update({ DevicesTable.id eq existing[DevicesTable.id] }) {
            it[deviceName] = req.deviceName
            it[department] = req.department
            it[assignedUser] = req.assignedUser
            it[cpuUsage] = req.cpuUsage.coerceIn(0, 100)
            it[memoryUsage] = req.memoryUsage.coerceIn(0, 100)
            it[status] = derivedStatus
            it[lastSeen] = LocalDateTime.now()
        }

        DevicesTable.selectAll().where { DevicesTable.id eq existing[DevicesTable.id] }.single().let(::toDevice)
    }




    fun syncDiscoveredDevices(discovered: List<DiscoveredPeer>): List<Device> = transaction {
        val now = LocalDateTime.now()
        discovered.forEach { peer ->
            if (!privateNetworks.any { peer.ipAddress.startsWith(it) }) return@forEach
            val row = DevicesTable.selectAll().where { DevicesTable.ipAddress eq peer.ipAddress }.singleOrNull() ?: return@forEach
            DevicesTable.update({ DevicesTable.id eq row[DevicesTable.id] }) {
                it[deviceName] = peer.hostname.ifBlank { row[DevicesTable.deviceName] }
                it[status] = if (peer.reachable) deriveStatus(cpuUsage = row[DevicesTable.cpuUsage], memoryUsage = row[DevicesTable.memoryUsage]) else "Not Reachable"
                it[lastSeen] = now
            }
        }
        list()
    }

    fun update(id: Int, req: DeviceRequest): Device? = transaction {
        require(privateNetworks.any { req.ipAddress.startsWith(it) }) { "Only LAN devices are monitorable." }
        val existing = DevicesTable.selectAll().where { DevicesTable.id eq id }.singleOrNull()
        val derivedStatus = deriveStatus(
            cpuUsage = existing?.get(DevicesTable.cpuUsage),
            memoryUsage = existing?.get(DevicesTable.memoryUsage)
        )
        DevicesTable.update({ DevicesTable.id eq id }) {
            it[deviceName] = req.deviceName
            it[ipAddress] = req.ipAddress
            it[department] = req.department
            it[assignedUser] = req.assignedUser
            it[status] = derivedStatus
            it[lastSeen] = LocalDateTime.now()
        }
        DevicesTable.selectAll().where { DevicesTable.id eq id }.singleOrNull()?.let(::toDevice)
    }

    fun delete(id: Int): Boolean = transaction {
        DevicesTable.deleteWhere { DevicesTable.id eq id } > 0
    }


    private fun deriveLiveStatus(live: LanDeviceDto): String {
        if (!live.reachable) return "Not Reachable"

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
