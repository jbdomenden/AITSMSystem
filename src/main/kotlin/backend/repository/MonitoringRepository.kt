package backend.repository

import backend.config.DiscoveryRequestsTable
import backend.config.CollectorEnrollmentCodesTable
import backend.config.MonitoringCollectorsTable
import backend.config.MonitoringDevicesTable
import backend.models.CollectorDeviceReport
import backend.models.CollectorEnrollmentRequest
import backend.models.CollectorEnrollmentResponse
import backend.models.CollectorPackageResponse
import backend.models.CollectorRedeemResponse
import backend.models.CollectorReportRequest
import backend.models.CollectorView
import backend.models.DiscoveryCommand
import backend.models.LanDeviceDto
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID

object CollectorToken {
    private val random = SecureRandom()
    fun issue(id: UUID): String {
        val secret = ByteArray(32).also(random::nextBytes)
        return "$id.${Base64.getUrlEncoder().withoutPadding().encodeToString(secret)}"
    }
    fun hash(raw: String): String = MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun collectorId(raw: String): UUID? = runCatching { UUID.fromString(raw.substringBefore('.')) }.getOrNull()
    fun matches(raw: String, expectedHash: String): Boolean = MessageDigest.isEqual(
        hash(raw).toByteArray(Charsets.US_ASCII), expectedHash.toByteArray(Charsets.US_ASCII)
    )
}

data class CollectorIdentity(val id: UUID, val scope: String, val ownerUserId: Int?)

class MonitoringRepository(private val clock: Clock = Clock.systemUTC()) {
    private fun now(): LocalDateTime = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
    private fun iso(value: LocalDateTime?): String? = value?.toInstant(ZoneOffset.UTC)?.toString()

    fun enroll(request: CollectorEnrollmentRequest): CollectorEnrollmentResponse = transaction {
        val id = UUID.randomUUID()
        val token = CollectorToken.issue(id)
        val enrolledAt = now()
        MonitoringCollectorsTable.insert {
            it[MonitoringCollectorsTable.id] = id
            it[siteId] = request.siteId
            it[name] = request.name
            it[scope] = "LAN"
            it[ownerUserId] = null
            it[tokenHash] = CollectorToken.hash(token)
            it[MonitoringCollectorsTable.enrolledAt] = enrolledAt
            it[lastSeenAt] = null
            it[revokedAt] = null
        }
        CollectorEnrollmentResponse(id.toString(), request.siteId, request.name, token, iso(enrolledAt)!!)
    }

    fun createEnrollmentCode(userId: Int, scopeValue: String, site: String, collectorName: String): CollectorPackageResponse = transaction {
        val id = UUID.randomUUID()
        val code = CollectorToken.issue(id)
        val created = now()
        val expires = created.plusMinutes(10)
        CollectorEnrollmentCodesTable.insert {
            it[CollectorEnrollmentCodesTable.id] = id
            it[codeHash] = CollectorToken.hash(code)
            it[requestedBy] = userId
            it[scope] = scopeValue
            it[siteId] = site
            it[name] = collectorName
            it[createdAt] = created
            it[expiresAt] = expires
            it[redeemedAt] = null
            it[collectorId] = null
        }
        CollectorPackageResponse(code, iso(expires)!!, scopeValue)
    }

    fun redeemEnrollmentCode(rawCode: String, privateCidrs: List<String>): CollectorRedeemResponse? = transaction {
        val codeId = CollectorToken.collectorId(rawCode) ?: return@transaction null
        val row = CollectorEnrollmentCodesTable.selectAll().where { CollectorEnrollmentCodesTable.id eq codeId }.singleOrNull()
            ?: return@transaction null
        if (row[CollectorEnrollmentCodesTable.redeemedAt] != null || row[CollectorEnrollmentCodesTable.expiresAt].isBefore(now()) ||
            !CollectorToken.matches(rawCode, row[CollectorEnrollmentCodesTable.codeHash])) return@transaction null

        val collectorId = UUID.randomUUID()
        val token = CollectorToken.issue(collectorId)
        val redeemed = now()
        val claimed = CollectorEnrollmentCodesTable.update({
            (CollectorEnrollmentCodesTable.id eq codeId) and CollectorEnrollmentCodesTable.redeemedAt.isNull()
        }) {
            it[redeemedAt] = redeemed
        }
        if (claimed != 1) return@transaction null
        MonitoringCollectorsTable.insert {
            it[id] = collectorId
            it[siteId] = row[CollectorEnrollmentCodesTable.siteId]
            it[name] = row[CollectorEnrollmentCodesTable.name]
            it[scope] = row[CollectorEnrollmentCodesTable.scope]
            it[ownerUserId] = row[CollectorEnrollmentCodesTable.requestedBy]
            it[tokenHash] = CollectorToken.hash(token)
            it[enrolledAt] = redeemed
            it[lastSeenAt] = null
            it[revokedAt] = null
        }
        CollectorEnrollmentCodesTable.update({ CollectorEnrollmentCodesTable.id eq codeId }) {
            it[CollectorEnrollmentCodesTable.collectorId] = collectorId
        }
        CollectorRedeemResponse(collectorId.toString(), token, row[CollectorEnrollmentCodesTable.scope], privateCidrs)
    }

    fun enrollmentCodeBelongsTo(rawCode: String, userId: Int): Boolean = transaction {
        val codeId = CollectorToken.collectorId(rawCode) ?: return@transaction false
        val row = CollectorEnrollmentCodesTable.selectAll().where {
            (CollectorEnrollmentCodesTable.id eq codeId) and (CollectorEnrollmentCodesTable.requestedBy eq userId)
        }.singleOrNull() ?: return@transaction false
        row[CollectorEnrollmentCodesTable.redeemedAt] == null && row[CollectorEnrollmentCodesTable.expiresAt].isAfter(now()) &&
            CollectorToken.matches(rawCode, row[CollectorEnrollmentCodesTable.codeHash])
    }

    fun authenticate(rawToken: String): CollectorIdentity? = transaction {
        val id = CollectorToken.collectorId(rawToken) ?: return@transaction null
        val row = MonitoringCollectorsTable.selectAll().where { MonitoringCollectorsTable.id eq id }.singleOrNull()
            ?: return@transaction null
        if (row[MonitoringCollectorsTable.revokedAt] != null || !CollectorToken.matches(rawToken, row[MonitoringCollectorsTable.tokenHash])) null
        else CollectorIdentity(id, row[MonitoringCollectorsTable.scope], row[MonitoringCollectorsTable.ownerUserId])
    }

    fun revoke(id: UUID): Boolean = transaction {
        MonitoringCollectorsTable.update({ (MonitoringCollectorsTable.id eq id) and MonitoringCollectorsTable.revokedAt.isNull() }) {
            it[revokedAt] = now()
        } > 0
    }

    fun listCollectors(ownerId: Int? = null): List<CollectorView> = transaction {
        val instantNow = clock.instant()
        val query = MonitoringCollectorsTable.selectAll().let { base ->
            if (ownerId == null) base else base.where { MonitoringCollectorsTable.ownerUserId eq ownerId }
        }
        query.orderBy(MonitoringCollectorsTable.enrolledAt, SortOrder.DESC).map { row ->
            val lastSeen = row[MonitoringCollectorsTable.lastSeenAt]
            val state = when {
                row[MonitoringCollectorsTable.revokedAt] != null -> "REVOKED"
                lastSeen == null -> "NEVER_CONNECTED"
                lastSeen.toInstant(ZoneOffset.UTC).isAfter(instantNow.minusSeconds(120)) -> "ONLINE"
                lastSeen.toInstant(ZoneOffset.UTC).isAfter(instantNow.minusSeconds(600)) -> "STALE"
                else -> "OFFLINE"
            }
            CollectorView(row[MonitoringCollectorsTable.id].toString(), row[MonitoringCollectorsTable.siteId], row[MonitoringCollectorsTable.name], state,
                iso(row[MonitoringCollectorsTable.enrolledAt])!!, iso(lastSeen), iso(row[MonitoringCollectorsTable.revokedAt]),
                row[MonitoringCollectorsTable.scope], row[MonitoringCollectorsTable.ownerUserId])
        }
    }

    fun revokeOwned(id: UUID, ownerId: Int): Boolean = transaction {
        MonitoringCollectorsTable.update({
            (MonitoringCollectorsTable.id eq id) and (MonitoringCollectorsTable.ownerUserId eq ownerId) and MonitoringCollectorsTable.revokedAt.isNull()
        }) { it[revokedAt] = now() } > 0
    }

    fun saveReport(collectorId: UUID, report: CollectorReportRequest, measurement: Instant): Pair<Instant, Int> = transaction {
        val received = now()
        MonitoringCollectorsTable.update({ (MonitoringCollectorsTable.id eq collectorId) and MonitoringCollectorsTable.revokedAt.isNull() }) {
            it[lastSeenAt] = received
        }
        report.devices.forEach { device -> upsertDevice(collectorId, device, measurement, received) }
        report.discoveryRequestId?.let { rawId ->
            val requestId = runCatching { UUID.fromString(rawId) }.getOrNull()
            if (requestId != null) {
                DiscoveryRequestsTable.update({ (DiscoveryRequestsTable.id eq requestId) and (DiscoveryRequestsTable.collectorId eq collectorId) }) {
                    it[status] = if (report.discoveryError.isNullOrBlank()) "COMPLETED" else "FAILED"
                    it[completedAt] = received
                    it[errorMessage] = report.discoveryError?.take(500)
                }
            }
        }
        received.toInstant(ZoneOffset.UTC) to report.devices.size
    }

    private fun org.jetbrains.exposed.sql.Transaction.upsertDevice(collectorId: UUID, device: CollectorDeviceReport, fallbackMeasurement: Instant, received: LocalDateTime) {
        val existing = MonitoringDevicesTable.selectAll().where {
            (MonitoringDevicesTable.collectorId eq collectorId) and (MonitoringDevicesTable.deviceKey eq device.deviceKey)
        }.singleOrNull()
        val measured = runCatching { Instant.parse(device.measurementAt ?: fallbackMeasurement.toString()) }.getOrDefault(fallbackMeasurement)
        val values: MonitoringDevicesTable.(org.jetbrains.exposed.sql.statements.UpdateBuilder<*>) -> Unit = { statement ->
            statement[hostname] = device.hostname
            statement[ipAddress] = device.ipAddress
            statement[macAddress] = device.macAddress
            statement[osDetails] = device.osDetails
            statement[reachable] = device.reachable
            statement[telemetryAvailable] = device.telemetryAvailable
            statement[cpuUsagePercent] = device.cpuUsagePercent?.toBigDecimal()
            statement[memoryUsagePercent] = device.memoryUsagePercent?.toBigDecimal()
            statement[storageTotalBytes] = device.storageTotalBytes
            statement[storageUsedBytes] = device.storageUsedBytes
            statement[measurementAt] = LocalDateTime.ofInstant(measured, ZoneOffset.UTC)
            statement[receivedAt] = received
            statement[lastSeenAt] = received
        }
        if (existing == null) {
            MonitoringDevicesTable.insert {
                it[MonitoringDevicesTable.collectorId] = collectorId
                it[deviceKey] = device.deviceKey
                values(MonitoringDevicesTable, it)
            }
        } else {
            MonitoringDevicesTable.update({ MonitoringDevicesTable.id eq existing[MonitoringDevicesTable.id] }) { values(MonitoringDevicesTable, it) }
        }
    }

    fun devices(): List<LanDeviceDto> = transaction {
        val staleBefore = clock.instant().minusSeconds(600)
        MonitoringDevicesTable.selectAll().orderBy(MonitoringDevicesTable.receivedAt, SortOrder.DESC).map { row ->
            val received = row[MonitoringDevicesTable.receivedAt]
            val stale = received.toInstant(ZoneOffset.UTC).isBefore(staleBefore)
            LanDeviceDto(
                id = "collector-device-${row[MonitoringDevicesTable.id]}", hostname = row[MonitoringDevicesTable.hostname],
                ipAddress = row[MonitoringDevicesTable.ipAddress], reachable = row[MonitoringDevicesTable.reachable] && !stale,
                telemetryAvailable = row[MonitoringDevicesTable.telemetryAvailable],
                telemetrySourceType = if (row[MonitoringDevicesTable.telemetryAvailable]) "COLLECTOR_AGENT" else "LAN_OBSERVED",
                cpuUsagePercent = row[MonitoringDevicesTable.cpuUsagePercent]?.toDouble(), memoryUsagePercent = row[MonitoringDevicesTable.memoryUsagePercent]?.toDouble(),
                lastSeen = iso(row[MonitoringDevicesTable.lastSeenAt])!!, measurementAt = iso(row[MonitoringDevicesTable.measurementAt]),
                receivedAt = iso(received), stale = stale, collectorId = row[MonitoringDevicesTable.collectorId].toString(),
                macAddress = row[MonitoringDevicesTable.macAddress], osDetails = row[MonitoringDevicesTable.osDetails],
                storageTotalBytes = row[MonitoringDevicesTable.storageTotalBytes], storageUsedBytes = row[MonitoringDevicesTable.storageUsedBytes]
            )
        }.distinctBy { "${it.collectorId}:${it.ipAddress}" }
    }

    fun queueDiscovery(): Pair<UUID, UUID> = transaction {
        val collector = MonitoringCollectorsTable.selectAll().where { MonitoringCollectorsTable.revokedAt.isNull() }
            .orderBy(MonitoringCollectorsTable.lastSeenAt, SortOrder.DESC_NULLS_LAST).limit(1).singleOrNull()
            ?: error("No collector is enrolled.")
        val requestId = UUID.randomUUID()
        DiscoveryRequestsTable.insert {
            it[id] = requestId
            it[collectorId] = collector[MonitoringCollectorsTable.id]
            it[status] = "PENDING"
            it[requestedAt] = now()
            it[completedAt] = null
            it[errorMessage] = null
        }
        requestId to collector[MonitoringCollectorsTable.id]
    }

    fun nextCommand(collectorId: UUID, cidrs: List<String>): DiscoveryCommand? = transaction {
        MonitoringCollectorsTable.update({ MonitoringCollectorsTable.id eq collectorId }) { it[lastSeenAt] = now() }
        val row = DiscoveryRequestsTable.selectAll().where {
            (DiscoveryRequestsTable.collectorId eq collectorId) and (DiscoveryRequestsTable.status eq "PENDING")
        }.orderBy(DiscoveryRequestsTable.requestedAt).limit(1).singleOrNull() ?: return@transaction null
        DiscoveryRequestsTable.update({ DiscoveryRequestsTable.id eq row[DiscoveryRequestsTable.id] }) { it[status] = "IN_PROGRESS" }
        DiscoveryCommand(row[DiscoveryRequestsTable.id].toString(), cidrs, iso(row[DiscoveryRequestsTable.requestedAt])!!)
    }

    fun latestDiscovery(): Triple<String, String?, String?> = transaction {
        val row = DiscoveryRequestsTable.selectAll().orderBy(DiscoveryRequestsTable.requestedAt, SortOrder.DESC).limit(1).singleOrNull()
            ?: return@transaction Triple("NEVER", null, null)
        Triple(row[DiscoveryRequestsTable.status], iso(row[DiscoveryRequestsTable.completedAt]), row[DiscoveryRequestsTable.errorMessage])
    }
}
