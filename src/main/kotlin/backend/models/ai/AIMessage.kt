package backend.models.ai

import kotlinx.serialization.Serializable

@Serializable
data class AIMessage(
    val role: String,
    val content: String
)
