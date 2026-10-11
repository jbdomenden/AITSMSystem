package backend.repository

import backend.models.LanDeviceDto
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceStatusTest {
    private fun device(
        reachable: Boolean,
        telemetry: Boolean = false,
        cpu: Double? = null,
        memory: Double? = null
    ) = LanDeviceDto(
        id = "collector-device-1",
        hostname = "laptop",
        ipAddress = "192.168.1.51",
        reachable = reachable,
        telemetryAvailable = telemetry,
        telemetrySourceType = if (telemetry) "COLLECTOR_AGENT" else "LAN_OBSERVED",
        cpuUsagePercent = cpu,
        memoryUsagePercent = memory,
        lastSeen = "2026-10-12T04:00:00Z"
    )

    @Test fun `reachable LAN peer is online even without detailed telemetry`() {
        assertEquals("Online", deriveLiveDeviceStatus(device(reachable = true)))
    }

    @Test fun `unreachable LAN peer remains unreachable`() {
        assertEquals("Unreachable", deriveLiveDeviceStatus(device(reachable = false)))
    }

    @Test fun `instrumented device with critical telemetry is critical`() {
        assertEquals("Critical", deriveLiveDeviceStatus(device(reachable = true, telemetry = true, cpu = 95.0)))
    }
}
