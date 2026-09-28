package backend.services.ai

import kotlinx.serialization.json.Json

object AIJsonSupport {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    inline fun <reified T> decodeObject(raw: String): T? {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.decodeFromString<T>(cleaned.substring(start, end + 1)) }.getOrNull()
    }
}
