package backend.services.ai

import backend.models.ai.AIMessage

data class AIProviderResult(
    val ok: Boolean,
    val content: String,
    val errorMessage: String? = null
)

interface AIProvider {
    fun chat(baseUrl: String, model: String, timeoutMillis: Long, messages: List<AIMessage>): AIProviderResult
    fun listModels(baseUrl: String, timeoutMillis: Long): AIProviderResult
    fun testConnection(baseUrl: String, model: String, timeoutMillis: Long): AIProviderResult
}
