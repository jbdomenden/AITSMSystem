package backend.models

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiErrorResponseTest {
    @Test
    fun `validation responses expose a consistent browser error field`() {
        val response = ApiErrorResponse(400, "Current password is incorrect")
        val payload = Json.encodeToString(ApiErrorResponse.serializer(), response)

        assertEquals("Current password is incorrect", response.error)
        assertTrue(payload.contains("\"message\":\"Current password is incorrect\""))
        assertTrue(payload.contains("\"error\":\"Current password is incorrect\""))
    }
}
