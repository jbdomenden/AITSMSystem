package backend.models.ai

import kotlinx.serialization.Serializable

@Serializable
data class AIChatRequest(
    val message: String
)
