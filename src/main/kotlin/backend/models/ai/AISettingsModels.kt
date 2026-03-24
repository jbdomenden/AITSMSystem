package backend.models.ai

import kotlinx.serialization.Serializable

@Serializable
data class AIConfigResponse(
    val provider: String,
    val baseUrl: String,
    val model: String,
    val timeoutMillis: Long
)

@Serializable
data class AIConfigUpdateRequest(
    val baseUrl: String,
    val model: String
)

@Serializable
data class AIModelsResponse(
    val models: List<String>,
    val currentModel: String,
    val baseUrl: String
)

@Serializable
data class AIConnectionTestRequest(
    val baseUrl: String? = null,
    val model: String? = null
)

@Serializable
data class AIConnectionTestResponse(
    val success: Boolean,
    val message: String
)

@Serializable
data class AITicketDraftRequest(
    val issueSummary: String,
    val ticketDescription: String,
    val suggestedPriority: String,
    val originalUserMessage: String? = null,
    val ticketTitle: String? = null
)

@Serializable
data class AITicketDraftResponse(
    val title: String,
    val description: String,
    val priority: String
)
