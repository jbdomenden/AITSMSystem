package backend.services.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlinx.serialization.Serializable

class AIHelpersTest {
    @Serializable
    private data class ExamplePayload(val title: String, val priority: String)

    @Test
    fun `redacts common credentials before provider requests`() {
        val sanitized = AIContentSanitizer.redact(
            "password=hunter2 api_key: abc123 Bearer token.value secret=my-secret"
        )

        assertFalse(sanitized.contains("hunter2"))
        assertFalse(sanitized.contains("abc123"))
        assertFalse(sanitized.contains("token.value"))
        assertFalse(sanitized.contains("my-secret"))
    }

    @Test
    fun `decodes structured JSON wrapped in a markdown fence`() {
        val parsed = AIJsonSupport.decodeObject<ExamplePayload>(
            """```json
            {"title":"Wi-Fi issue","priority":"Medium"}
            ```"""
        )

        assertNotNull(parsed)
        assertEquals("Wi-Fi issue", parsed.title)
        assertEquals("Medium", parsed.priority)
    }
}
