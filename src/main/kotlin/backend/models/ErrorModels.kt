package backend.models

import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorResponse(
    val status: Int,
    val message: String,
    val docs: String = "/docs"
)
