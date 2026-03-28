package backend.services

import backend.queries.SystemSettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssetDetectionServiceTest {
    private class InMemorySettingsStore : SystemSettingsStore {
        private val store = mutableMapOf<String, String>()
        override fun getByKey(key: String): String? = store[key]
        override fun upsert(key: String, value: String) { store[key] = value }
    }

    @Test
    fun `uses defaults when no prefixes are saved`() {
        val service = AssetDetectionService(InMemorySettingsStore())

        assertTrue(service.matches("192.168.1.5"))
        assertTrue(service.matches("10.0.10.9"))
        assertTrue(service.matches("172.16.0.1"))
        assertFalse(service.matches("8.8.8.8"))
    }

    @Test
    fun `saves and matches normalized prefixes`() {
        val service = AssetDetectionService(InMemorySettingsStore())
        service.savePrefixes(listOf(" 169.120 ", "10.0.", "not-a-prefix", "169.120"))

        val prefixes = service.getPrefixes()
        assertEquals(listOf("169.120.", "10.0."), prefixes)
        assertTrue(service.matches("169.120.45.22"))
        assertTrue(service.matches("10.0.7.3"))
        assertFalse(service.matches("192.168.1.2"))
    }

    @Test
    fun `does not overwrite settings when submitted prefixes are all invalid`() {
        val service = AssetDetectionService(InMemorySettingsStore())
        service.savePrefixes(listOf("192.168."))
        service.savePrefixes(listOf("invalid", "1.2.3.999"))

        assertEquals(listOf("192.168."), service.getPrefixes())
    }
}
