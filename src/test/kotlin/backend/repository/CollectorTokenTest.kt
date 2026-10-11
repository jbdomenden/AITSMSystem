package backend.repository

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CollectorTokenTest {
    @Test fun `collector tokens are scoped opaque hashed and verifiable`() {
        val id = UUID.randomUUID()
        val token = CollectorToken.issue(id)
        val hash = CollectorToken.hash(token)
        assertEquals(id, CollectorToken.collectorId(token))
        assertEquals(64, hash.length)
        assertNotEquals(token, hash)
        assertTrue(CollectorToken.matches(token, hash))
        assertFalse(CollectorToken.matches("$id.wrong", hash))
    }

    @Test fun `malformed collector token does not identify a collector`() {
        assertEquals(null, CollectorToken.collectorId("not-a-token"))
    }
}
