package backend.repository

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
}
