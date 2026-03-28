package backend.queries

import backend.config.InventoryAssetSnapshotsTable
import backend.config.InventoryAssetsTable
import backend.config.DevicesTable
import backend.models.InventoryFilters
import backend.models.InventoryUpsertDetectedRequest
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

class InventoryQueries {
    data class InventoryAssetRecord(
        val id: Int,
        val assetTag: String?,
        val deviceName: String,
        val hostname: String?,
        val fullDeviceName: String?,
        val manufacturer: String?,
        val model: String?,
        val processorName: String?,
        val processorSpeed: String?,
        val installedRam: String?,
        val usableRam: String?,
        val ramSpeed: String?,
        val gpuName: String?,
        val gpuMemory: String?,
        val storageTotal: String?,
        val storageUsed: String?,
        val storageFree: String?,
        val storageBreakdownJson: String?,
        val systemType: String?,
        val osName: String?,
        val osEdition: String?,
        val osVersion: String?,
        val osBuild: String?,
        val installedOn: String?,
        val domainOrWorkgroup: String?,
        val deviceUuid: String?,
        val productId: String?,
        val penTouchSupport: String?,
        val ipAddress: String?,
        val macAddress: String?,
        val connectionSource: String?,
        val status: String,
        val assignedDepartment: String?,
        val assignedUser: String?,
        val notes: String?,
        val lastSeenAt: LocalDateTime?,
        val createdAt: LocalDateTime,
        val updatedAt: LocalDateTime
    )

    data class InventoryPagedResult(val items: List<InventoryAssetRecord>, val total: Long)
    data class InventoryStatsRaw(val total: Long, val online: Long, val offline: Long, val stale: Long, val bySource: Map<String, Long>)

    private data class RegisteredDeviceRecord(
        val id: Int,
        val deviceName: String,
        val ipAddress: String,
        val department: String,
        val assignedUser: String,
        val status: String,
        val lastSeen: LocalDateTime
    )

    private fun rowToRecord(row: ResultRow): InventoryAssetRecord = InventoryAssetRecord(
        id = row[InventoryAssetsTable.id],
        assetTag = row[InventoryAssetsTable.assetTag],
        deviceName = row[InventoryAssetsTable.fullDeviceName] ?: row[InventoryAssetsTable.hostname] ?: row[InventoryAssetsTable.ipAddress] ?: "Unknown device",
        hostname = row[InventoryAssetsTable.hostname],
        fullDeviceName = row[InventoryAssetsTable.fullDeviceName],
        manufacturer = row[InventoryAssetsTable.manufacturer],
        model = row[InventoryAssetsTable.model],
        processorName = row[InventoryAssetsTable.processorName],
        processorSpeed = row[InventoryAssetsTable.processorSpeed],
        installedRam = row[InventoryAssetsTable.installedRam],
        usableRam = row[InventoryAssetsTable.usableRam],
        ramSpeed = row[InventoryAssetsTable.ramSpeed],
        gpuName = row[InventoryAssetsTable.gpuName],
        gpuMemory = row[InventoryAssetsTable.gpuMemory],
        storageTotal = row[InventoryAssetsTable.storageTotal],
        storageUsed = row[InventoryAssetsTable.storageUsed],
        storageFree = row[InventoryAssetsTable.storageFree],
        storageBreakdownJson = row[InventoryAssetsTable.storageBreakdownJson],
        systemType = row[InventoryAssetsTable.systemType],
        osName = row[InventoryAssetsTable.osName],
        osEdition = row[InventoryAssetsTable.osEdition],
        osVersion = row[InventoryAssetsTable.osVersion],
        osBuild = row[InventoryAssetsTable.osBuild],
        installedOn = row[InventoryAssetsTable.installedOn],
        domainOrWorkgroup = row[InventoryAssetsTable.domainOrWorkgroup],
        deviceUuid = row[InventoryAssetsTable.deviceUuid],
        productId = row[InventoryAssetsTable.productId],
        penTouchSupport = row[InventoryAssetsTable.penTouchSupport],
        ipAddress = row[InventoryAssetsTable.ipAddress],
        macAddress = row[InventoryAssetsTable.macAddress],
        connectionSource = row[InventoryAssetsTable.connectionSource],
        status = row[InventoryAssetsTable.status],
        assignedDepartment = row[InventoryAssetsTable.assignedDepartment],
        assignedUser = row[InventoryAssetsTable.assignedUser],
        notes = row[InventoryAssetsTable.notes],
        lastSeenAt = row[InventoryAssetsTable.lastSeenAt],
        createdAt = row[InventoryAssetsTable.createdAt],
        updatedAt = row[InventoryAssetsTable.updatedAt]
    )

    private fun rowToRegisteredDevice(row: ResultRow): RegisteredDeviceRecord = RegisteredDeviceRecord(
        id = row[DevicesTable.id],
        deviceName = row[DevicesTable.deviceName],
        ipAddress = row[DevicesTable.ipAddress],
        department = row[DevicesTable.department],
        assignedUser = row[DevicesTable.assignedUser],
        status = row[DevicesTable.status],
        lastSeen = row[DevicesTable.lastSeen]
    )

    private fun normalizeKey(value: String?): String? = value?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
    private fun getRawInventoryAssetById(id: Int): InventoryAssetRecord? =
        InventoryAssetsTable.selectAll().where { InventoryAssetsTable.id eq id }.singleOrNull()?.let(::rowToRecord)

    private fun composeRegisteredInventoryRecord(
        device: RegisteredDeviceRecord,
        byIp: Map<String, InventoryAssetRecord>,
        byHostname: Map<String, InventoryAssetRecord>
    ): InventoryAssetRecord {
        val enrichment = byIp[normalizeKey(device.ipAddress)] ?: byHostname[normalizeKey(device.deviceName)]
        return InventoryAssetRecord(
            id = device.id,
            assetTag = enrichment?.assetTag,
            deviceName = device.deviceName,
            hostname = enrichment?.hostname ?: device.deviceName,
            fullDeviceName = enrichment?.fullDeviceName ?: device.deviceName,
            manufacturer = enrichment?.manufacturer,
            model = enrichment?.model,
            processorName = enrichment?.processorName,
            processorSpeed = enrichment?.processorSpeed,
            installedRam = enrichment?.installedRam,
            usableRam = enrichment?.usableRam,
            ramSpeed = enrichment?.ramSpeed,
            gpuName = enrichment?.gpuName,
            gpuMemory = enrichment?.gpuMemory,
            storageTotal = enrichment?.storageTotal,
            storageUsed = enrichment?.storageUsed,
            storageFree = enrichment?.storageFree,
            storageBreakdownJson = enrichment?.storageBreakdownJson,
            systemType = enrichment?.systemType,
            osName = enrichment?.osName,
            osEdition = enrichment?.osEdition,
            osVersion = enrichment?.osVersion,
            osBuild = enrichment?.osBuild,
            installedOn = enrichment?.installedOn,
            domainOrWorkgroup = enrichment?.domainOrWorkgroup,
            deviceUuid = enrichment?.deviceUuid,
            productId = enrichment?.productId,
            penTouchSupport = enrichment?.penTouchSupport,
            ipAddress = device.ipAddress,
            macAddress = enrichment?.macAddress,
            connectionSource = enrichment?.connectionSource,
            status = device.status,
            assignedDepartment = device.department,
            assignedUser = device.assignedUser,
            notes = enrichment?.notes,
            lastSeenAt = device.lastSeen,
            createdAt = enrichment?.createdAt ?: device.lastSeen,
            updatedAt = enrichment?.updatedAt ?: device.lastSeen
        )
    }

    private fun getRegisteredInventoryRecords(): List<InventoryAssetRecord> {
        val devices = DevicesTable.selectAll().map(::rowToRegisteredDevice)
        val enrichment = InventoryAssetsTable.selectAll().map(::rowToRecord)
        val byIp = enrichment.mapNotNull { record -> normalizeKey(record.ipAddress)?.let { it to record } }.toMap()
        val byHostname = enrichment.mapNotNull { record ->
            normalizeKey(record.hostname ?: record.fullDeviceName)?.let { it to record }
        }.toMap()
        return devices.map { device -> composeRegisteredInventoryRecord(device, byIp, byHostname) }
    }

    private fun filterInMemory(items: List<InventoryAssetRecord>, filters: InventoryFilters): List<InventoryAssetRecord> {
        return items.filter { row ->
            val search = filters.search?.trim()?.lowercase().orEmpty()
            val status = filters.status?.trim()?.lowercase().orEmpty()
            val department = filters.department?.trim()?.lowercase().orEmpty()
            val assignedUser = filters.assignedUser?.trim()?.lowercase().orEmpty()
            val source = filters.connectionSource?.trim()?.lowercase().orEmpty()

            val matchesSearch = search.isBlank() || listOfNotNull(
                row.deviceName,
                row.hostname,
                row.ipAddress,
                row.manufacturer,
                row.model,
                row.processorName,
                row.osName
            ).any { it.lowercase().contains(search) }

            val matchesStatus = status.isBlank() || row.status.lowercase() == status
            val matchesDepartment = department.isBlank() || (row.assignedDepartment ?: "").lowercase() == department
            val matchesAssignedUser = assignedUser.isBlank() || (row.assignedUser ?: "").lowercase() == assignedUser
            val matchesSource = source.isBlank() || (row.connectionSource ?: "").lowercase() == source

            matchesSearch && matchesStatus && matchesDepartment && matchesAssignedUser && matchesSource
        }
    }

    private fun sortInMemory(items: List<InventoryAssetRecord>, filters: InventoryFilters): List<InventoryAssetRecord> {
        val sorted = when (filters.sortBy?.trim()?.lowercase()) {
            "devicename", "hostname" -> items.sortedBy { (it.hostname ?: it.deviceName).lowercase() }
            "status" -> items.sortedBy { it.status.lowercase() }
            "department" -> items.sortedBy { (it.assignedDepartment ?: "").lowercase() }
            "source" -> items.sortedBy { (it.connectionSource ?: "").lowercase() }
            else -> items.sortedBy { it.lastSeenAt ?: it.updatedAt }
        }

        return if (filters.sortOrder?.equals("asc", ignoreCase = true) == true) sorted else sorted.reversed()
    }

    fun getAll(filters: InventoryFilters): InventoryPagedResult = transaction {
        val all = getRegisteredInventoryRecords()
        val filtered = sortInMemory(filterInMemory(all, filters), filters)
        val page = filters.page.coerceAtLeast(1)
        val pageSize = filters.pageSize.coerceIn(1, 100)
        val from = ((page - 1) * pageSize).coerceAtMost(filtered.size)
        val to = (from + pageSize).coerceAtMost(filtered.size)
        InventoryPagedResult(filtered.subList(from, to), filtered.size.toLong())
    }

    fun getById(id: Int): InventoryAssetRecord? = transaction {
        getRegisteredInventoryRecords().firstOrNull { it.id == id }
    }

    fun getByHostname(hostname: String): InventoryAssetRecord? = transaction {
        InventoryAssetsTable.selectAll().where { InventoryAssetsTable.hostname eq hostname.trim() }.singleOrNull()?.let(::rowToRecord)
    }

    fun getByIp(ip: String): InventoryAssetRecord? = transaction {
        InventoryAssetsTable.selectAll().where { InventoryAssetsTable.ipAddress eq ip.trim() }.singleOrNull()?.let(::rowToRecord)
    }

    fun getByDeviceUuid(deviceUuid: String): InventoryAssetRecord? = transaction {
        InventoryAssetsTable.selectAll().where { InventoryAssetsTable.deviceUuid eq deviceUuid.trim() }.singleOrNull()?.let(::rowToRecord)
    }

    fun upsertAsset(payload: InventoryUpsertDetectedRequest, status: String, normalizedStorageFree: String?): Pair<InventoryAssetRecord, Boolean> = transaction {
        val now = LocalDateTime.now()
        val existing = payload.deviceUuid?.takeIf { it.isNotBlank() }?.let { getByDeviceUuid(it) }
            ?: payload.ipAddress?.takeIf { it.isNotBlank() }?.let { getByIp(it) }
            ?: payload.hostname?.takeIf { it.isNotBlank() }?.let { getByHostname(it) }

        if (existing == null) {
            val insertResult = InventoryAssetsTable.insert {
                it[InventoryAssetsTable.assetTag] = payload.assetTag
                it[InventoryAssetsTable.hostname] = payload.hostname
                it[InventoryAssetsTable.fullDeviceName] = payload.fullDeviceName ?: payload.deviceName
                it[InventoryAssetsTable.manufacturer] = payload.manufacturer
                it[InventoryAssetsTable.model] = payload.model
                it[InventoryAssetsTable.processorName] = payload.processorName
                it[InventoryAssetsTable.processorSpeed] = payload.processorSpeed
                it[InventoryAssetsTable.installedRam] = payload.installedRam
                it[InventoryAssetsTable.usableRam] = payload.usableRam
                it[InventoryAssetsTable.ramSpeed] = payload.ramSpeed
                it[InventoryAssetsTable.gpuName] = payload.gpuName
                it[InventoryAssetsTable.gpuMemory] = payload.gpuMemory
                it[InventoryAssetsTable.storageTotal] = payload.storageTotal
                it[InventoryAssetsTable.storageUsed] = payload.storageUsed
                it[InventoryAssetsTable.storageFree] = normalizedStorageFree
                it[InventoryAssetsTable.storageBreakdownJson] = payload.storageBreakdownJson
                it[InventoryAssetsTable.systemType] = payload.systemType
                it[InventoryAssetsTable.osName] = payload.osName
                it[InventoryAssetsTable.osEdition] = payload.osEdition
                it[InventoryAssetsTable.osVersion] = payload.osVersion
                it[InventoryAssetsTable.osBuild] = payload.osBuild
                it[InventoryAssetsTable.installedOn] = payload.installedOn
                it[InventoryAssetsTable.domainOrWorkgroup] = payload.domainOrWorkgroup
                it[InventoryAssetsTable.deviceUuid] = payload.deviceUuid
                it[InventoryAssetsTable.productId] = payload.productId
                it[InventoryAssetsTable.penTouchSupport] = payload.penTouchSupport
                it[InventoryAssetsTable.ipAddress] = payload.ipAddress
                it[InventoryAssetsTable.macAddress] = payload.macAddress
                it[InventoryAssetsTable.connectionSource] = payload.connectionSource
                it[InventoryAssetsTable.status] = status
                it[InventoryAssetsTable.assignedDepartment] = payload.assignedDepartment
                it[InventoryAssetsTable.assignedUser] = payload.assignedUser
                it[InventoryAssetsTable.notes] = payload.notes
                it[InventoryAssetsTable.lastSeenAt] = now
                it[InventoryAssetsTable.createdAt] = now
                it[InventoryAssetsTable.updatedAt] = now
            }
            val id = insertResult[InventoryAssetsTable.id]
            return@transaction checkNotNull(getRawInventoryAssetById(id)) to true
        }

        InventoryAssetsTable.update({ InventoryAssetsTable.id eq existing.id }) {
            it[InventoryAssetsTable.assetTag] = payload.assetTag ?: existing.assetTag
            it[InventoryAssetsTable.hostname] = payload.hostname ?: existing.hostname
            it[InventoryAssetsTable.fullDeviceName] = payload.fullDeviceName ?: payload.deviceName ?: existing.fullDeviceName
            it[InventoryAssetsTable.manufacturer] = payload.manufacturer ?: existing.manufacturer
            it[InventoryAssetsTable.model] = payload.model ?: existing.model
            it[InventoryAssetsTable.processorName] = payload.processorName ?: existing.processorName
            it[InventoryAssetsTable.processorSpeed] = payload.processorSpeed ?: existing.processorSpeed
            it[InventoryAssetsTable.installedRam] = payload.installedRam ?: existing.installedRam
            it[InventoryAssetsTable.usableRam] = payload.usableRam ?: existing.usableRam
            it[InventoryAssetsTable.ramSpeed] = payload.ramSpeed ?: existing.ramSpeed
            it[InventoryAssetsTable.gpuName] = payload.gpuName ?: existing.gpuName
            it[InventoryAssetsTable.gpuMemory] = payload.gpuMemory ?: existing.gpuMemory
            it[InventoryAssetsTable.storageTotal] = payload.storageTotal ?: existing.storageTotal
            it[InventoryAssetsTable.storageUsed] = payload.storageUsed ?: existing.storageUsed
            it[InventoryAssetsTable.storageFree] = normalizedStorageFree ?: existing.storageFree
            it[InventoryAssetsTable.storageBreakdownJson] = payload.storageBreakdownJson ?: existing.storageBreakdownJson
            it[InventoryAssetsTable.systemType] = payload.systemType ?: existing.systemType
            it[InventoryAssetsTable.osName] = payload.osName ?: existing.osName
            it[InventoryAssetsTable.osEdition] = payload.osEdition ?: existing.osEdition
            it[InventoryAssetsTable.osVersion] = payload.osVersion ?: existing.osVersion
            it[InventoryAssetsTable.osBuild] = payload.osBuild ?: existing.osBuild
            it[InventoryAssetsTable.installedOn] = payload.installedOn ?: existing.installedOn
            it[InventoryAssetsTable.domainOrWorkgroup] = payload.domainOrWorkgroup ?: existing.domainOrWorkgroup
            it[InventoryAssetsTable.deviceUuid] = payload.deviceUuid ?: existing.deviceUuid
            it[InventoryAssetsTable.productId] = payload.productId ?: existing.productId
            it[InventoryAssetsTable.penTouchSupport] = payload.penTouchSupport ?: existing.penTouchSupport
            it[InventoryAssetsTable.ipAddress] = payload.ipAddress ?: existing.ipAddress
            it[InventoryAssetsTable.macAddress] = payload.macAddress ?: existing.macAddress
            it[InventoryAssetsTable.connectionSource] = payload.connectionSource ?: existing.connectionSource
            it[InventoryAssetsTable.status] = status
            it[InventoryAssetsTable.assignedDepartment] = payload.assignedDepartment ?: existing.assignedDepartment
            it[InventoryAssetsTable.assignedUser] = payload.assignedUser ?: existing.assignedUser
            it[InventoryAssetsTable.notes] = payload.notes ?: existing.notes
            it[InventoryAssetsTable.lastSeenAt] = now
            it[InventoryAssetsTable.updatedAt] = now
        }

        checkNotNull(getRawInventoryAssetById(existing.id)) to false
    }

    fun updateLastSeen(id: Int): InventoryAssetRecord? = transaction {
        val now = LocalDateTime.now()
        InventoryAssetsTable.update({ InventoryAssetsTable.id eq id }) {
            it[InventoryAssetsTable.lastSeenAt] = now
            it[InventoryAssetsTable.updatedAt] = now
        }
        getRawInventoryAssetById(id)
    }

    fun updateStatus(id: Int, status: String): InventoryAssetRecord? = transaction {
        val registered = DevicesTable.selectAll().where { DevicesTable.id eq id }.singleOrNull() ?: return@transaction null
        DevicesTable.update({ DevicesTable.id eq id }) {
            it[DevicesTable.status] = status
            it[DevicesTable.lastSeen] = LocalDateTime.now()
        }
        InventoryAssetsTable.selectAll().where { InventoryAssetsTable.ipAddress eq registered[DevicesTable.ipAddress] }.singleOrNull()?.let { row ->
            InventoryAssetsTable.update({ InventoryAssetsTable.id eq row[InventoryAssetsTable.id] }) {
                it[InventoryAssetsTable.status] = status
                it[InventoryAssetsTable.updatedAt] = LocalDateTime.now()
            }
        }
        getById(id)
    }

    fun updateAssignment(id: Int, department: String?, assignedUser: String?): InventoryAssetRecord? = transaction {
        val registered = DevicesTable.selectAll().where { DevicesTable.id eq id }.singleOrNull() ?: return@transaction null
        DevicesTable.update({ DevicesTable.id eq id }) {
            it[DevicesTable.department] = department ?: registered[DevicesTable.department]
            it[DevicesTable.assignedUser] = assignedUser ?: registered[DevicesTable.assignedUser]
            it[DevicesTable.lastSeen] = LocalDateTime.now()
        }
        InventoryAssetsTable.selectAll().where { InventoryAssetsTable.ipAddress eq registered[DevicesTable.ipAddress] }.singleOrNull()?.let { row ->
            InventoryAssetsTable.update({ InventoryAssetsTable.id eq row[InventoryAssetsTable.id] }) {
                it[InventoryAssetsTable.assignedDepartment] = department
                it[InventoryAssetsTable.assignedUser] = assignedUser
                it[InventoryAssetsTable.updatedAt] = LocalDateTime.now()
            }
        }
        getById(id)
    }

    fun updateNotes(id: Int, notes: String?): InventoryAssetRecord? = transaction {
        val registered = DevicesTable.selectAll().where { DevicesTable.id eq id }.singleOrNull() ?: return@transaction null
        val ip = registered[DevicesTable.ipAddress]
        val hostname = registered[DevicesTable.deviceName]
        val linkedAsset = InventoryAssetsTable.selectAll().where {
            (InventoryAssetsTable.ipAddress eq ip) or (InventoryAssetsTable.hostname eq hostname)
        }.singleOrNull()

        if (linkedAsset == null) {
            InventoryAssetsTable.insert {
                it[assetTag] = null
                it[InventoryAssetsTable.hostname] = hostname
                it[fullDeviceName] = registered[DevicesTable.deviceName]
                it[InventoryAssetsTable.ipAddress] = ip
                it[InventoryAssetsTable.status] = registered[DevicesTable.status]
                it[InventoryAssetsTable.assignedDepartment] = registered[DevicesTable.department]
                it[InventoryAssetsTable.assignedUser] = registered[DevicesTable.assignedUser]
                it[InventoryAssetsTable.notes] = notes
                it[InventoryAssetsTable.connectionSource] = "manual"
                it[InventoryAssetsTable.lastSeenAt] = registered[DevicesTable.lastSeen]
                it[createdAt] = LocalDateTime.now()
                it[updatedAt] = LocalDateTime.now()
            }
        } else {
            InventoryAssetsTable.update({ InventoryAssetsTable.id eq linkedAsset[InventoryAssetsTable.id] }) {
                it[InventoryAssetsTable.notes] = notes
                it[InventoryAssetsTable.updatedAt] = LocalDateTime.now()
            }
        }
        getById(id)
    }

    fun saveSnapshot(assetId: Int, payload: String, source: String) {
        transaction {
            InventoryAssetSnapshotsTable.insert {
                it[InventoryAssetSnapshotsTable.assetId] = assetId
                it[InventoryAssetSnapshotsTable.snapshotSource] = source
                it[InventoryAssetSnapshotsTable.payloadJson] = payload
                it[InventoryAssetSnapshotsTable.createdAt] = LocalDateTime.now()
            }
        }
    }

    fun markAssetsStale(before: LocalDateTime): Int = transaction {
        InventoryAssetsTable.update({ (InventoryAssetsTable.lastSeenAt.isNotNull()) and (InventoryAssetsTable.lastSeenAt less before) }) {
            it[InventoryAssetsTable.status] = "stale"
            it[InventoryAssetsTable.updatedAt] = LocalDateTime.now()
        }
    }

    fun markAssetsOnlineSince(since: LocalDateTime): Int = transaction {
        InventoryAssetsTable.update({ (InventoryAssetsTable.lastSeenAt.isNotNull()) and (InventoryAssetsTable.lastSeenAt greaterEq since) }) {
            it[InventoryAssetsTable.status] = "online"
            it[InventoryAssetsTable.updatedAt] = LocalDateTime.now()
        }
    }

    fun getStats(): InventoryStatsRaw = transaction {
        val rows = getRegisteredInventoryRecords()
        InventoryStatsRaw(
            total = rows.size.toLong(),
            online = rows.count { it.status.equals("online", ignoreCase = true) }.toLong(),
            offline = rows.count { it.status.equals("offline", ignoreCase = true) }.toLong(),
            stale = rows.count { it.status.equals("stale", ignoreCase = true) }.toLong(),
            bySource = rows.groupingBy { it.connectionSource ?: "unknown" }.eachCount().mapValues { it.value.toLong() }
        )
    }

    fun searchAssets(query: String, filters: InventoryFilters): InventoryPagedResult = getAll(filters.copy(search = query))

    fun findAllForExport(filters: InventoryFilters): List<InventoryAssetRecord> = transaction {
        sortInMemory(filterInMemory(getRegisteredInventoryRecords(), filters), filters)
    }
}
