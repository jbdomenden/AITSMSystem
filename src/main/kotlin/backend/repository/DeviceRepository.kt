package backend.repository

import backend.config.DevicesTable
import backend.models.ClientMetricsRequest
import backend.models.Device
import backend.models.DeviceRequest
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

class DeviceRepository {
    private val privateNetworks = listOf("10.", "192.168.", "172.16.", "172.17.", "172.18.", "172.19.", "172.20.", "172.21.", "172.22.", "172.23.", "172.24.", "172.25.", "172.26.", "172.27.", "172.28.", "172.29.", "172.30.", "172.31.")

    fun create(req: DeviceRequest): Device = transaction {
        require(privateNetworks.any { req.ipAddress.startsWith(it) }) { "Only LAN devices are monitorable." }
        val id = DevicesTable.insert {
            it[deviceName] = req.deviceName
            it[ipAddress] = req.ipAddress
            it[department] = req.department
            it[assignedUser] = req.assignedUser
            it[cpuUsage] = (15..70).random()
            it[memoryUsage] = (20..80).random()
            it[status] = req.status
            it[lastSeen] = LocalDateTime.now()
        }[DevicesTable.id]
        list().first { it.id == id }
    }

    fun list(): List<Device> = transaction { DevicesTable.selectAll().map(::toDevice) }

    fun findByIp(ip: String): Device? = transaction {
        DevicesTable.selectAll().where { DevicesTable.ipAddress eq ip.trim() }.singleOrNull()?.let(::toDevice)
    }


    fun upsertClientMetrics(req: ClientMetricsRequest): Device = transaction {
        require(privateNetworks.any { req.ipAddress.startsWith(it) }) { "Only LAN devices are monitorable." }

        val existing = DevicesTable.selectAll().where { DevicesTable.ipAddress eq req.ipAddress }.singleOrNull()
        if (existing == null) {
            val id = DevicesTable.insert {
                it[deviceName] = req.deviceName
                it[ipAddress] = req.ipAddress
                it[department] = req.department
                it[assignedUser] = req.assignedUser
                it[cpuUsage] = req.cpuUsage.coerceIn(0, 100)
                it[memoryUsage] = req.memoryUsage.coerceIn(0, 100)
                it[status] = req.status
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
            it[status] = req.status
            it[lastSeen] = LocalDateTime.now()
        }

        DevicesTable.selectAll().where { DevicesTable.id eq existing[DevicesTable.id] }.single().let(::toDevice)
    }


    fun update(id: Int, req: DeviceRequest): Device? = transaction {
        require(privateNetworks.any { req.ipAddress.startsWith(it) }) { "Only LAN devices are monitorable." }
        DevicesTable.update({ DevicesTable.id eq id }) {
            it[deviceName] = req.deviceName
            it[ipAddress] = req.ipAddress
            it[department] = req.department
            it[assignedUser] = req.assignedUser
            it[status] = req.status
            it[lastSeen] = LocalDateTime.now()
        }
        DevicesTable.selectAll().where { DevicesTable.id eq id }.singleOrNull()?.let(::toDevice)
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
