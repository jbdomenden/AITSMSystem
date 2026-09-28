package backend.services.ai

import backend.config.Env
import backend.models.ai.AIMessage
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Hosted Gemini integration. The API key is read only on the server. */
class GeminiProvider : AIProvider {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
    private val apiKey get() = Env.get("GEMINI_API_KEY")

    override fun chat(model: String, timeoutMillis: Long, messages: List<AIMessage>): AIProviderResult {
        val key = apiKey?.takeIf { it.isNotBlank() }
            ?: return AIProviderResult(false, "", "Gemini is not configured. Contact an administrator.")
        val system = messages.firstOrNull { it.role == "system" }?.content.orEmpty()
        val contents = messages.filter { it.role != "system" }
        val input = contents.joinToString("\n\n") { message ->
            val speaker = if (message.role == "assistant") "Assistant" else "User"
            "$speaker:\n${message.content}"
        }
        val payload = buildJsonObject {
            put("model", model)
            put("input", input)
            put("store", false)
            if (system.isNotBlank()) put("system_instruction", system)
            putJsonObject("generation_config") {
                put("temperature", 0.35)
                put("thinking_level", "low")
                put("max_output_tokens", 1_000)
            }
        }
        return execute(key, timeoutMillis, payload.toString())
    }

    override fun testConnection(model: String, timeoutMillis: Long): AIProviderResult =
        chat(model, minOf(timeoutMillis, 15_000L), listOf(AIMessage("user", "Reply exactly: OK")))

    private fun execute(key: String, timeoutMillis: Long, payload: String): AIProviderResult = try {
        val request = HttpRequest.newBuilder(URI("https://generativelanguage.googleapis.com/v1beta/interactions"))
            .timeout(Duration.ofMillis(timeoutMillis))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", key)
            .POST(HttpRequest.BodyPublishers.ofString(payload)).build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            val message = when (response.statusCode()) {
                400 -> "Gemini API credentials or request configuration is invalid."
                401, 403 -> "Gemini credentials are invalid or unavailable."
                404 -> "The configured Gemini model is unavailable. Select a supported model and try again."
                429 -> "Gemini quota is temporarily exhausted. Please try again shortly."
                in 500..599 -> "Gemini is temporarily unavailable. Please try again shortly."
                else -> "Gemini request could not be completed."
            }
            AIProviderResult(false, "", message)
        } else {
            val text = runCatching {
                json.parseToJsonElement(response.body())
                    .jsonObject["steps"]
                    ?.jsonArray
                    ?.asSequence()
                    ?.mapNotNull { step ->
                        val stepObject = step.jsonObject
                        if (stepObject["type"]?.jsonPrimitive?.contentOrNull != "model_output") return@mapNotNull null
                        stepObject["content"]?.jsonArray?.joinToString("") { part ->
                            part.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        }
                    }
                    ?.joinToString("")
            }.getOrNull().orEmpty().trim()
            if (text.isBlank()) AIProviderResult(false, "", "Gemini returned an empty response.") else AIProviderResult(true, text)
        }
    } catch (_: java.net.http.HttpTimeoutException) {
        AIProviderResult(false, "", "Gemini did not respond in time. Please try again.")
    } catch (_: Exception) {
        AIProviderResult(false, "", "Gemini is currently unavailable. Please try again later.")
    }
}
