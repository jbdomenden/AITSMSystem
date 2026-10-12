package backend.models

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CollectorTimeTest {
    private val serverNow = Instant.parse("2026-10-12T12:00:00Z")

    @Test fun `valid collector time is preserved`() {
        val reported = Instant.parse("2026-10-12T11:59:30Z")
        assertEquals(reported, normalizeCollectorMeasurement(reported.toString(), serverNow))
    }

    @Test fun `clock skew safely falls back to server time`() {
        assertEquals(serverNow, normalizeCollectorMeasurement("2026-10-13T12:00:00Z", serverNow))
        assertEquals(serverNow, normalizeCollectorMeasurement("2026-10-10T12:00:00Z", serverNow))
    }

    @Test fun `malformed collector time remains invalid`() {
        assertFailsWith<IllegalArgumentException> { normalizeCollectorMeasurement("not-a-time", serverNow) }
    }
}
