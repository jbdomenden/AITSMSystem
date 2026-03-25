package backend.services.ai

import backend.config.Env
import backend.models.ai.AIConfigResponse
import backend.models.ai.AIConfigUpdateRequest
import io.ktor.server.config.ApplicationConfig
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

private data class AIConfigState(
    val provider: String,
    val baseUrl: String,
    val model: String,
    val timeoutMillis: Long
)

class AIConfigService(config: ApplicationConfig) {
    private val state = AtomicReference(
        AIConfigState(
            provider = envOrConfig("AI_PROVIDER", config, "ai.provider") ?: "ollama",
            baseUrl = envOrConfig("AI_OLLAMA_BASE_URL", config, "ai.ollama.baseUrl") ?: error("AI_OLLAMA_BASE_URL is required"),
            model = envOrConfig("AI_OLLAMA_MODEL", config, "ai.ollama.model") ?: error("AI_OLLAMA_MODEL is required"),
            timeoutMillis = (envOrConfig("AI_TIMEOUT_MILLIS", config, "ai.timeoutMillis")?.toLongOrNull() ?: 60_000L)
                .coerceIn(5_000L, 120_000L)
        )
    )

    fun snapshot(): AIConfigResponse {
        val current = state.get()
        return AIConfigResponse(current.provider, current.baseUrl, current.model, current.timeoutMillis)
    }

    fun update(request: AIConfigUpdateRequest): AIConfigResponse {
        val baseUrl = normalizeAndValidateBaseUrl(request.baseUrl)
        val model = request.model.trim()
        require(model.isNotBlank()) { "Model selection is required" }

        val updated = state.updateAndGet { it.copy(baseUrl = baseUrl, model = model) }
        return AIConfigResponse(updated.provider, updated.baseUrl, updated.model, updated.timeoutMillis)
    }

    fun normalizeAndValidateBaseUrl(input: String): String {
        val url = input.trim().ifBlank { throw IllegalArgumentException("Base URL is required") }.removeSuffix("/")
        val uri = runCatching { URI(url) }.getOrNull() ?: throw IllegalArgumentException("Base URL is not a valid URL")
        require(uri.scheme == "http" || uri.scheme == "https") { "Base URL must start with http:// or https://" }
        require(!uri.host.isNullOrBlank()) { "Base URL must contain a host" }
        return url
    }

    private fun envOrConfig(envName: String, config: ApplicationConfig, path: String): String? {
        val env = Env.get(envName)?.trim()
        if (!env.isNullOrBlank()) return env
        return config.propertyOrNull(path)?.getString()?.trim()?.takeIf { it.isNotBlank() }
    }
}
