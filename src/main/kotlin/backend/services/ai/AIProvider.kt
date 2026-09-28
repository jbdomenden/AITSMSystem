package backend.services.ai

import backend.models.ai.AIMessage

data class AIProviderResult(
    val ok: Boolean,
    val content: String,
    val errorMessage: String? = null
)

interface AIProvider {
    fun chat(model: String, timeoutMillis: Long, messages: List<AIMessage>): AIProviderResult
    fun testConnection(model: String, timeoutMillis: Long): AIProviderResult
}
