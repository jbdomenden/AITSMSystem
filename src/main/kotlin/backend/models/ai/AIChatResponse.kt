package backend.models.ai

import kotlinx.serialization.Serializable

@Serializable
data class AIChatResponse(
    val replyText: String,
    val structured: AIStructuredReply,
    val conversationSize: Int,
    val provider: String,
    val model: String,
    val error: String? = null
)
