package backend.repository

import backend.models.LanDeviceDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceIdentityTest {
    @Test fun `uuid and mac identities normalize consistently`() {
        assertEquals("11111111-2222-4333-8444-555555555555", normalizedDeviceUuid(" 11111111-2222-4333-8444-555555555555 "))
        assertEquals("30D042116CC9", normalizedMac("30-d0-42-11-6c-c9"))
        assertEquals("30D042116CC9", normalizedMac("30:D0:42:11:6C:C9"))
    }

    @Test fun `placeholder hardware identities are rejected`() {
        assertNull(normalizedDeviceUuid("00000000-0000-0000-0000-000000000000"))
        assertNull(normalizedMac("00-00-00-00-00-00"))
        assertNull(normalizedMac("not-a-mac"))
    }

    @Test fun `legacy agent rows collapse while preserving dynamic IP history`() {
        val older = device(id = "1", ip = "192.168.137.1", receivedAt = "2026-10-14T10:00:00Z")
        val newer = device(id = "2", ip = "192.168.1.34", receivedAt = "2026-10-14T11:00:00Z")

        val collapsed = collapseLegacyDeviceRows(listOf(older, newer))

        assertEquals(1, collapsed.size)
        assertEquals("192.168.1.34", collapsed.single().ipAddress)
        assertEquals(setOf("192.168.137.1", "192.168.1.34"), collapsed.single().observedIpAddresses.toSet())
    }

    @Test fun `conflicting stable identities with the same hostname remain separate`() {
        val first = device("1", "192.168.1.10", "2026-10-14T10:00:00Z", deviceUuid = "11111111-2222-4333-8444-555555555555")
        val second = device("2", "192.168.1.11", "2026-10-14T11:00:00Z", deviceUuid = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee")
        assertEquals(2, collapseLegacyDeviceRows(listOf(first, second)).size)
    }

    @Test fun `legacy address attaches to the only stable identity for a hostname`() {
        val legacy = device("1", "192.168.137.1", "2026-10-14T10:00:00Z")
        val stable = device("2", "192.168.1.34", "2026-10-14T11:00:00Z", deviceUuid = "11111111-2222-4333-8444-555555555555")
        val collapsed = collapseLegacyDeviceRows(listOf(legacy, stable))
        assertEquals(1, collapsed.size)
        assertEquals(setOf("192.168.137.1", "192.168.1.34"), collapsed.single().observedIpAddresses.toSet())
    }

    @Test fun `stale mac identity attaches to the sole uuid after an adapter change`() {
        val oldAdapter = device("1", "192.168.137.1", "2026-10-14T10:00:00Z").copy(
            identityKey = "mac:001122334455",
            macAddress = "00-11-22-33-44-55"
        )
        val current = device("2", "192.168.1.34", "2026-10-14T11:00:00Z", deviceUuid = "11111111-2222-4333-8444-555555555555")
        val collapsed = collapseLegacyDeviceRows(listOf(oldAdapter, current))
        assertEquals(1, collapsed.size)
        assertEquals(setOf("192.168.137.1", "192.168.1.34"), collapsed.single().observedIpAddresses.toSet())
    }

    @Test fun `stale mac identity remains separate when hostname has conflicting uuids`() {
        val oldAdapter = device("1", "192.168.137.1", "2026-10-14T10:00:00Z").copy(
            identityKey = "mac:001122334455",
            macAddress = "00-11-22-33-44-55"
        )
        val first = device("2", "192.168.1.34", "2026-10-14T11:00:00Z", deviceUuid = "11111111-2222-4333-8444-555555555555")
        val second = device("3", "192.168.1.35", "2026-10-14T12:00:00Z", deviceUuid = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee")
        assertEquals(3, collapseLegacyDeviceRows(listOf(oldAdapter, first, second)).size)
    }

    private fun device(id: String, ip: String, receivedAt: String, deviceUuid: String? = null) = LanDeviceDto(
        id = id,
        hostname = "ELLE",
        ipAddress = ip,
        reachable = false,
        telemetryAvailable = true,
        telemetrySourceType = "COLLECTOR_AGENT",
        lastSeen = receivedAt,
        receivedAt = receivedAt,
        stale = true,
        collectorId = id,
        identityKey = if (deviceUuid == null) "legacy:$id" else "uuid:$deviceUuid",
        deviceUuid = deviceUuid,
        observedIpAddresses = listOf(ip)
    )
}
