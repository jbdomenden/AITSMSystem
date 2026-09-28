package backend.services.ai

import backend.config.Env
import backend.models.ai.AIConfigResponse
import backend.models.ai.AIConfigUpdateRequest
import io.ktor.server.config.ApplicationConfig
import java.util.concurrent.atomic.AtomicReference

private data class AIConfigState(
    val provider: String,
    val model: String,
    val timeoutMillis: Long
)

class AIConfigService(config: ApplicationConfig) {
    private val state = AtomicReference(
        AIConfigState(
            provider = "gemini",
            model = envOrConfig("GEMINI_MODEL", config, "ai.gemini.model") ?: "gemini-3.8-flash",
            timeoutMillis = (envOrConfig("AI_TIMEOUT_MILLIS", config, "ai.timeoutMillis")?.toLongOrNull() ?: 60_000L)
                .coerceIn(5_000L, 120_000L)
        )
    )

    fun snapshot(): AIConfigResponse {
        val current = state.get()
        return AIConfigResponse(current.provider, current.model, current.timeoutMillis)
    }

    fun update(request: AIConfigUpdateRequest): AIConfigResponse {
        val model = request.model.trim()
        require(model.isNotBlank()) { "Model selection is required" }

        val updated = state.updateAndGet { it.copy(model = model) }
        return AIConfigResponse(updated.provider, updated.model, updated.timeoutMillis)
    }

    private fun envOrConfig(envName: String, config: ApplicationConfig, path: String): String? {
        val env = Env.get(envName)?.trim()
        if (!env.isNullOrBlank()) return env
        return config.propertyOrNull(path)?.getString()?.trim()?.takeIf { it.isNotBlank() }
    }
}
