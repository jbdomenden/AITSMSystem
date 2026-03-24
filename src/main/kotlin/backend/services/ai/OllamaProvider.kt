package backend.services.ai

import backend.models.ai.AIMessage
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class OllamaProvider : AIProvider {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build()

    override fun chat(baseUrl: String, model: String, timeoutMillis: Long, messages: List<AIMessage>): AIProviderResult {
        val payload = buildJsonObject {
            put("model", model)
            put("stream", false)
            putJsonArray("messages") {
                messages.forEach { msg ->
                    add(buildJsonObject {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            }
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create("${normalizeBaseUrl(baseUrl)}/api/chat"))
            .timeout(Duration.ofMillis(timeoutMillis))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build()

        return executeJsonRequest(request) { body ->
            val root = json.parseToJsonElement(body).jsonObject
            root["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
                ?: root["response"]?.jsonPrimitive?.content
        }
    }

    override fun listModels(baseUrl: String, timeoutMillis: Long): AIProviderResult {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("${normalizeBaseUrl(baseUrl)}/api/tags"))
            .timeout(Duration.ofMillis(timeoutMillis))
            .header("Content-Type", "application/json")
            .GET()
            .build()

        return executeJsonRequest(request) { body ->
            val root = json.parseToJsonElement(body).jsonObject
            val modelsNode = root["models"] ?: return@executeJsonRequest "[]"
            val models = modelsNode.jsonArray.mapNotNull { model ->
                runCatching { model.jsonObject["name"]?.jsonPrimitive?.content }.getOrNull()
            }
            buildJsonArray {
                models.forEach { add(JsonPrimitive(it)) }
            }.toString()
        }
    }

    override fun testConnection(baseUrl: String, model: String, timeoutMillis: Long): AIProviderResult {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("${normalizeBaseUrl(baseUrl)}/api/chat"))
            .timeout(Duration.ofMillis(timeoutMillis))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    buildJsonObject {
                        put("model", model)
                        put("stream", false)
                        putJsonArray("messages") {
                            add(buildJsonObject {
                                put("role", "user")
                                put("content", "Reply exactly with: OK")
                            })
                        }
                    }.toString()
                )
            )
            .build()

        return executeJsonRequest(request) { body ->
            val root = json.parseToJsonElement(body).jsonObject
            root["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
        }
    }

    private fun executeJsonRequest(request: HttpRequest, extractor: (String) -> String?): AIProviderResult {
        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                AIProviderResult(ok = false, content = "", errorMessage = "Provider request failed (${response.statusCode()})")
            } else {
                val content = runCatching { extractor(response.body()) }.getOrNull()?.trim().orEmpty()
                if (content.isBlank()) {
                    AIProviderResult(ok = false, content = "", errorMessage = "Malformed provider response")
                } else {
                    AIProviderResult(ok = true, content = content)
                }
            }
        } catch (_: ConnectException) {
            AIProviderResult(ok = false, content = "", errorMessage = "Unable to connect to Ollama")
        } catch (_: java.net.http.HttpTimeoutException) {
            AIProviderResult(ok = false, content = "", errorMessage = "Ollama request timed out")
        } catch (ex: Exception) {
            AIProviderResult(ok = false, content = "", errorMessage = ex.message ?: "Unknown provider error")
        }
    }

    private fun normalizeBaseUrl(baseUrl: String): String = baseUrl.trim().removeSuffix("/")
}
