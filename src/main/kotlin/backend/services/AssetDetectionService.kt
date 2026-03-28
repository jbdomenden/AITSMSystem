package backend.services

import backend.queries.SystemSettingsQueries
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AssetDetectionService(private val settingsQueries: SystemSettingsQueries) {
    companion object {
        const val ASSET_IP_PREFIXES_KEY = "asset_ip_prefixes"
        private val PREFIX_REGEX = Regex("""^\d{1,3}(?:\.\d{1,3}){0,3}\.?$""")
        private val DEFAULT_PREFIXES = listOf("10.", "192.168.") + (16..31).map { "172.$it." }
    }

    fun getPrefixes(): List<String> {
        val raw = settingsQueries.getByKey(ASSET_IP_PREFIXES_KEY) ?: return DEFAULT_PREFIXES
        val parsed = runCatching { Json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
        return normalizePrefixes(parsed).ifEmpty { DEFAULT_PREFIXES }
    }

    fun savePrefixes(prefixes: List<String>) {
        val normalized = normalizePrefixes(prefixes)
        require(normalized.isNotEmpty()) { "At least one IP prefix is required" }
        settingsQueries.upsert(ASSET_IP_PREFIXES_KEY, Json.encodeToString(normalized))
    }

    fun matches(ip: String): Boolean {
        val normalizedIp = ip.trim()
        if (normalizedIp.isBlank()) return false
        return getPrefixes().any { normalizedIp.startsWith(it) }
    }

    private fun normalizePrefixes(prefixes: List<String>): List<String> {
        val normalized = prefixes
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { raw ->
                val dotCount = raw.count { it == '.' }
                when {
                    raw.endsWith('.') -> raw
                    dotCount >= 3 -> raw
                    else -> "$raw."
                }
            }
            .distinct()

        normalized.forEach { prefix ->
            require(PREFIX_REGEX.matches(prefix)) { "Invalid prefix format: $prefix" }
            val octets = prefix.removeSuffix(".").split(".")
            require(octets.all { (it.toIntOrNull() ?: -1) in 0..255 }) { "Invalid prefix octets: $prefix" }
        }

        return normalized
    }
}
