package backend.services

import backend.queries.SystemSettingsStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AssetDetectionService(private val settingsQueries: SystemSettingsStore) {
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
        if (normalized.isEmpty()) return
        settingsQueries.upsert(ASSET_IP_PREFIXES_KEY, Json.encodeToString(normalized))
    }

    fun matches(ip: String): Boolean {
        val normalizedIp = ip.trim()
        if (normalizedIp.isBlank()) return false
        return getPrefixes().any { normalizedIp.startsWith(it) }
    }

    fun matchesConfigured(ip: String): Boolean {
        val raw = settingsQueries.getByKey(ASSET_IP_PREFIXES_KEY) ?: return false
        val prefixes = runCatching { Json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
        return normalizePrefixes(prefixes).any { ip.trim().startsWith(it) }
    }

    fun getPrivateCidrs(): List<String> = getPrefixes().mapNotNull { prefix ->
        val value = prefix.removeSuffix(".")
        val octets = value.split('.')
        when (octets.size) {
            1 -> "${octets[0]}.0.0.0/8"
            2 -> "${octets[0]}.${octets[1]}.0.0/16"
            3 -> "${octets[0]}.${octets[1]}.${octets[2]}.0/24"
            4 -> "$value/32"
            else -> null
        }
    }

    fun getConfiguredPrivateCidrs(): List<String> {
        val raw = settingsQueries.getByKey(ASSET_IP_PREFIXES_KEY) ?: return emptyList()
        val prefixes = runCatching { Json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
        return normalizePrefixes(prefixes).mapNotNull { prefix ->
            val value = prefix.removeSuffix(".")
            val octets = value.split('.')
            when (octets.size) {
                1 -> "${octets[0]}.0.0.0/8"
                2 -> "${octets[0]}.${octets[1]}.0.0/16"
                3 -> "${octets[0]}.${octets[1]}.${octets[2]}.0/24"
                4 -> "$value/32"
                else -> null
            }
        }
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

        return normalized.filter { prefix ->
            if (!PREFIX_REGEX.matches(prefix)) return@filter false
            val octets = prefix.removeSuffix(".").split(".")
            octets.all { (it.toIntOrNull() ?: -1) in 0..255 }
        }
    }
}
