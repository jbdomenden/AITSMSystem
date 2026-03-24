package backend.models.ai

import kotlinx.serialization.Serializable

@Serializable
data class AIStructuredReply(
    val replyText: String,
    val issueSummary: String,
    val likelyCauses: List<String>,
    val troubleshootingSteps: List<String>,
    val escalationCriteria: List<String>,
    val suggestedPriority: String,
    val ticketTitle: String,
    val ticketDescription: String
)
