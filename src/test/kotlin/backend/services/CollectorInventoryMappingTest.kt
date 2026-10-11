package backend.services

import backend.models.CollectorDeviceReport
import backend.models.CollectorInventoryReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CollectorInventoryMappingTest {
    @Test
    fun `agent inventory is mapped to a rich inventory asset`() {
        val payload = CollectorDeviceReport(
            deviceKey = "host:elle",
            hostname = "ELLE",
            ipAddress = "192.168.1.34",
            macAddress = "30-D0-42-11-6C-C9",
            reachable = true,
            telemetryAvailable = true,
            measurementAt = "2026-10-12T04:00:00Z",
            inventory = CollectorInventoryReport(
                fullDeviceName = "ELLE.example.local",
                manufacturer = "Example Manufacturer",
                model = "Example Model",
                processorName = "Example CPU",
                installedRam = "16 GB",
                deviceUuid = "11111111-2222-4333-8444-555555555555"
            )
        ).toInventoryUpsert()

        assertEquals("agent", payload.connectionSource)
        assertEquals("online", payload.status)
        assertEquals("Example Manufacturer", payload.manufacturer)
        assertEquals("16 GB", payload.installedRam)
        assertEquals("11111111-2222-4333-8444-555555555555", payload.deviceUuid)
    }

    @Test
    fun `LAN peer creates only a basic inventory asset`() {
        val payload = CollectorDeviceReport(
            deviceKey = "peer:192.168.1.51",
            hostname = "Haruto.local",
            ipAddress = "192.168.1.51",
            reachable = true
        ).toInventoryUpsert()

        assertEquals("lan-detected", payload.connectionSource)
        assertEquals("online", payload.status)
        assertEquals("Haruto.local", payload.hostname)
        assertNull(payload.manufacturer)
        assertNull(payload.deviceUuid)
    }
}
