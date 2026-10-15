package backend.services

import backend.models.LanDeviceDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveDeviceSelectionTest {
    private fun reading(source: String, telemetry: Boolean, stale: Boolean, cpu: Double? = null) = LanDeviceDto(
        id = "$source-$stale", hostname = "Haruto", ipAddress = "192.168.1.51",
        reachable = !stale, telemetryAvailable = telemetry, telemetrySourceType = source,
        cpuUsagePercent = cpu, memoryUsagePercent = cpu, lastSeen = "2026-10-12T12:00:00Z", stale = stale
    )

    @Test fun `fresh agent telemetry wins over lan observation for the same ip`() {
        val observed = reading("LAN_OBSERVED", telemetry = false, stale = false)
        val agent = reading("COLLECTOR_AGENT", telemetry = true, stale = false, cpu = 42.0)
        val selected = preferredLiveDevicesByIp(listOf(observed, agent))["192.168.1.51"]!!
        assertTrue(selected.telemetryAvailable)
        assertEquals(42.0, selected.cpuUsagePercent)
    }

    @Test fun `fresh reachability wins when agent telemetry is stale`() {
        val staleAgent = reading("COLLECTOR_AGENT", telemetry = true, stale = true, cpu = 42.0)
        val observed = reading("LAN_OBSERVED", telemetry = false, stale = false)
        assertEquals(observed, preferredLiveDevicesByIp(listOf(staleAgent, observed))["192.168.1.51"])
    }

    @Test fun `duplicate readings for one ip collapse to the best live device`() {
        val observed = reading("LAN_OBSERVED", telemetry = false, stale = false)
        val agent = reading("COLLECTOR_AGENT", telemetry = true, stale = false, cpu = 31.0)
        val devices = deduplicatedLiveDevices(listOf(observed, agent))

        assertEquals(1, devices.size)
        assertEquals(agent, devices.single())
    }

    @Test fun `stable mac collapses a device after its ip changes`() {
        val oldAddress = reading("LAN_OBSERVED", telemetry = false, stale = true).copy(
            ipAddress = "192.168.1.20", macAddress = "30-d0-42-11-6c-c9"
        )
        val newAddress = reading("COLLECTOR_AGENT", telemetry = true, stale = false, cpu = 18.0).copy(
            ipAddress = "192.168.1.51", macAddress = "30-D0-42-11-6C-C9"
        )

        assertEquals(listOf(newAddress), deduplicatedLiveDevices(listOf(oldAddress, newAddress)))
    }

    @Test fun `only collector agent telemetry is visible across device views`() {
        val observed = reading("LAN_OBSERVED", telemetry = false, stale = false)
        val agent = reading("COLLECTOR_AGENT", telemetry = true, stale = false, cpu = 18.0)

        assertEquals(listOf(agent), visibleCollectorDevices(listOf(observed, agent)))
    }
}
