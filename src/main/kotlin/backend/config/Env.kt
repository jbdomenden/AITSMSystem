package backend.config

import java.io.File

object Env {
    private val dotenv: Map<String, String> by lazy { loadDotEnv() }

    fun get(name: String): String? {
        val environmentValue = System.getenv(name)?.trim()
        if (!environmentValue.isNullOrBlank()) return environmentValue
        return dotenv[name]?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun loadDotEnv(): Map<String, String> {
        val file = File(".env")
        if (!file.exists()) return emptyMap()

        return file.readLines()
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val key = line.substring(0, separator).trim()
                if (key.isBlank()) return@mapNotNull null
                val rawValue = line.substring(separator + 1).trim()
                val value = rawValue
                    .removeSurrounding("\"")
                    .removeSurrounding("'")
                key to value
            }
            .toMap()
    }
}
