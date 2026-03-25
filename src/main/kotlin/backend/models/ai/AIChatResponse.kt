package backend.models.ai

import kotlinx.serialization.Serializable

@Serializable
data class AIFallbackContent(
    val message: String,
    val issueSummary: String,
    val likelyCauses: List<String>,
    val troubleshootingSteps: List<String>,
    val escalationCriteria: List<String>,
    val suggestedPriority: String,
    val ticketTitle: String,
    val ticketDescription: String
)

@Serializable
data class AIChatResponse(
    val source: String,
    val reachable: Boolean,
    val reply: String? = null,
    val fallback: AIFallbackContent? = null,
    val conversationSize: Int,
    val provider: String,
    val model: String,
    val error: String? = null
)
