package backend.models

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ApiErrorResponse(
    val status: Int,
    val message: String,
    @EncodeDefault
    val error: String = message,
    val docs: String = "/docs"
)
