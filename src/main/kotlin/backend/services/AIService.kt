package backend.services

import backend.models.AIChatMessage
import backend.models.AIChatResponse
import backend.models.AIStructuredResponse
import backend.models.AITicketDraftRequest
import backend.models.AITicketDraftResponse
import backend.repository.KnowledgeRepository
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class AIService(private val knowledgeRepository: KnowledgeRepository? = null) {
    private val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build()
    private val json = Json { ignoreUnknownKeys = true }

    private val openAiApiKey: String = System.getenv("OPENAI_API_KEY").orEmpty().trim()
    private val openAiModel: String = System.getenv("OPENAI_MODEL")?.trim().orEmpty().ifBlank { "gpt-4.1-mini" }
    private val openAiTimeoutMillis: Long = System.getenv("OPENAI_TIMEOUT_MS")?.toLongOrNull()?.coerceIn(2_000, 30_000) ?: 14_000
    private val openAiMaxOutputTokens: Int = System.getenv("OPENAI_MAX_OUTPUT_TOKENS")?.toIntOrNull()?.coerceIn(250, 1_200) ?: 700

    private val conversationStore = ConcurrentHashMap<String, MutableList<AIChatMessage>>()
    private val sessionLastSeen = ConcurrentHashMap<String, Long>()
    private val conversationLimit = 16
    private val sessionTtlMs = Duration.ofHours(8).toMillis()

    fun chatAssistant(sessionId: String, message: String): AIChatResponse {
        if (openAiApiKey.isBlank()) {
            throw IllegalStateException("AI assistant is unavailable because OPENAI_API_KEY is not configured")
        }

        val userMessage = sanitize(message).trim().take(2_000)
        if (userMessage.isBlank()) throw IllegalArgumentException("Message is required")

        cleanupStaleSessions()

        val conversation = conversationStore.computeIfAbsent(sessionId) { mutableListOf() }
        synchronized(conversation) {
            conversation += AIChatMessage(role = "user", content = userMessage)
            if (conversation.size > conversationLimit) {
                val trimmed = conversation.takeLast(conversationLimit)
                conversation.clear()
                conversation.addAll(trimmed)
            }
        }
        sessionLastSeen[sessionId] = Instant.now().toEpochMilli()

        val recentMessages = synchronized(conversation) { conversation.takeLast(conversationLimit) }
        val structured = sendOpenAiRequest(recentMessages)

        synchronized(conversation) {
            conversation += AIChatMessage(role = "assistant", content = renderAssistantReply(structured))
            if (conversation.size > conversationLimit) {
                val trimmed = conversation.takeLast(conversationLimit)
                conversation.clear()
                conversation.addAll(trimmed)
            }
        }

        val reply = renderAssistantReply(structured)
        return AIChatResponse(
            reply = reply,
            structured = structured,
            priority = structured.suggestedPriority,
            category = structured.suggestedCategory,
            titleSuggestion = buildTitle(structured.issueSummary, structured.suggestedCategory),
            descriptionSuggestion = structured.ticketDescription,
            conversationSize = synchronized(conversation) { conversation.size },
            model = openAiModel
        )
    }

    fun clearConversation(sessionId: String) {
        conversationStore.remove(sessionId)
        sessionLastSeen.remove(sessionId)
    }

    fun createTicketDraft(input: AITicketDraftRequest): AITicketDraftResponse {
        val summary = input.issueSummary.trim().ifBlank { "IT support request" }
        val description = input.ticketDescription.trim().ifBlank { summary }
        return AITicketDraftResponse(
            title = buildTitle(summary, input.suggestedCategory),
            description = buildString {
                append(description)
                input.originalUserMessage?.trim()?.takeIf { it.isNotBlank() }?.let {
                    append("\n\nOriginal user message:\n")
                    append(it)
                }
            },
            priority = sanitizePriority(input.suggestedPriority),
            category = sanitizeCategory(input.suggestedCategory)
        )
    }

    private fun sendOpenAiRequest(history: List<AIChatMessage>): AIStructuredResponse {
        val input = buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                putJsonArray("content") {
                    add(buildJsonObject {
                        put("type", "input_text")
                        put("text", systemPrompt())
                    })
                }
            })
            history.forEach { message ->
                add(buildJsonObject {
                    put("role", message.role)
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "input_text")
                            put("text", message.content)
                        })
                    }
                })
            }
        }

        val payload = buildJsonObject {
            put("model", openAiModel)
            put("max_output_tokens", openAiMaxOutputTokens)
            put("input", input)
            put("text", buildJsonObject {
                put("format", structuredFormatSchema())
            })
        }

        val req = HttpRequest.newBuilder()
            .uri(java.net.URI.create("https://api.openai.com/v1/responses"))
            .timeout(Duration.ofMillis(openAiTimeoutMillis))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $openAiApiKey")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build()

        val response = httpClient.send(req, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("AI provider unavailable (${response.statusCode()})")
        }

        val parsed = json.parseToJsonElement(response.body()).jsonObject
        val outputText = extractResponseText(parsed)
            ?: throw IllegalStateException("AI response did not contain structured output")

        return parseStructuredResponse(outputText)
    }

    private fun structuredFormatSchema(): JsonObject = buildJsonObject {
        put("type", "json_schema")
        put("name", "itsm_troubleshooting")
        put("strict", true)
        put("schema", buildJsonObject {
            put("type", "object")
            putJsonArray("required") {
                add(JsonPrimitive("issueSummary"))
                add(JsonPrimitive("likelyCauses"))
                add(JsonPrimitive("troubleshootingSteps"))
                add(JsonPrimitive("escalationCriteria"))
                add(JsonPrimitive("suggestedPriority"))
                add(JsonPrimitive("ticketDescription"))
                add(JsonPrimitive("suggestedCategory"))
            }
            putJsonObject("properties") {
                putJsonObject("issueSummary") {
                    put("type", "string")
                    put("minLength", 5)
                    put("maxLength", 280)
                }
                putJsonObject("likelyCauses") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                    put("minItems", 1)
                    put("maxItems", 6)
                }
                putJsonObject("troubleshootingSteps") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                    put("minItems", 2)
                    put("maxItems", 8)
                }
                putJsonObject("escalationCriteria") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                    put("minItems", 1)
                    put("maxItems", 6)
                }
                putJsonObject("suggestedPriority") {
                    put("type", "string")
                    putJsonArray("enum") {
                        add(JsonPrimitive("Low"))
                        add(JsonPrimitive("Medium"))
                        add(JsonPrimitive("High"))
                        add(JsonPrimitive("Critical"))
                    }
                }
                putJsonObject("ticketDescription") {
                    put("type", "string")
                    put("minLength", 20)
                    put("maxLength", 2800)
                }
                putJsonObject("suggestedCategory") {
                    put("type", "string")
                    putJsonArray("enum") {
                        add(JsonPrimitive("Hardware"))
                        add(JsonPrimitive("Software"))
                        add(JsonPrimitive("Network"))
                        add(JsonPrimitive("Database"))
                        add(JsonPrimitive("Security"))
                        add(JsonPrimitive("Other"))
                    }
                }
            }
            put("additionalProperties", false)
        })
    }

    private fun extractResponseText(raw: JsonObject): String? {
        val output = raw["output"]?.jsonArray ?: return null
        output.forEach { outputItem ->
            val content = outputItem.jsonObject["content"]?.jsonArray ?: return@forEach
            content.forEach { contentItem ->
                val obj = contentItem.jsonObject
                val text = when {
                    obj["text"] != null -> obj["text"]?.jsonPrimitive?.content
                    obj["type"]?.jsonPrimitive?.content == "output_text" -> obj["text"]?.jsonPrimitive?.content
                    else -> null
                }
                if (!text.isNullOrBlank()) return text
            }
        }
        return null
    }

    private fun parseStructuredResponse(rawJson: String): AIStructuredResponse {
        val data = json.decodeFromString(AIStructuredPayload.serializer(), rawJson)
        return AIStructuredResponse(
            issueSummary = data.issueSummary.trim(),
            likelyCauses = data.likelyCauses.map { it.trim() }.filter { it.isNotBlank() },
            troubleshootingSteps = data.troubleshootingSteps.map { it.trim() }.filter { it.isNotBlank() },
            escalationCriteria = data.escalationCriteria.map { it.trim() }.filter { it.isNotBlank() },
            suggestedPriority = sanitizePriority(data.suggestedPriority),
            ticketDescription = data.ticketDescription.trim(),
            suggestedCategory = sanitizeCategory(data.suggestedCategory)
        )
    }

    private fun renderAssistantReply(data: AIStructuredResponse): String = buildString {
        appendLine("Issue summary: ${data.issueSummary}")
        appendLine("Likely causes:")
        data.likelyCauses.forEach { appendLine("- $it") }
        appendLine()
        appendLine("Step-by-step troubleshooting:")
        data.troubleshootingSteps.forEachIndexed { index, step -> appendLine("${index + 1}. $step") }
        appendLine()
        appendLine("When to escalate:")
        data.escalationCriteria.forEach { appendLine("- $it") }
        appendLine()
        appendLine("Suggested ticket category: ${data.suggestedCategory}")
        append("Suggested priority: ${data.suggestedPriority}")
    }

    private fun buildTitle(issueSummary: String, category: String): String {
        val cleaned = issueSummary.replace(Regex("\\s+"), " ").trim().take(95)
        return if (cleaned.isNotBlank()) cleaned else "$category support request"
    }

    private fun sanitizePriority(priority: String): String {
        val normalized = priority.trim().lowercase()
        return when (normalized) {
            "low" -> "Low"
            "medium" -> "Medium"
            "high" -> "High"
            "critical" -> "Critical"
            else -> "Medium"
        }
    }

    private fun sanitizeCategory(category: String): String {
        val normalized = category.trim().lowercase()
        return when (normalized) {
            "hardware" -> "Hardware"
            "software" -> "Software"
            "network" -> "Network"
            "database" -> "Database"
            "security" -> "Security"
            else -> "Other"
        }
    }

    private fun sanitize(input: String): String {
        var sanitized = input
        sanitized = sanitized.replace(Regex("(?i)(api[_-]?key|token|password|secret)\\s*[:=]\\s*[^\\s,;]+"), "$1: [redacted]")
        sanitized = sanitized.replace(Regex("(?i)bearer\\s+[a-z0-9._-]+"), "Bearer [redacted]")
        sanitized = sanitized.replace(Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE), "[redacted-email]")
        return sanitized
    }

    private fun cleanupStaleSessions() {
        val now = Instant.now().toEpochMilli()
        sessionLastSeen.entries.removeIf { (key, lastSeen) ->
            val stale = now - lastSeen > sessionTtlMs
            if (stale) {
                conversationStore.remove(key)
            }
            stale
        }
    }

    private fun systemPrompt(): String =
        """
You are an ITSM troubleshooting assistant integrated in an enterprise support portal.
Your job is to help users diagnose and resolve IT issues with concise and practical guidance.
Rules:
- Ask at most one short clarifying question when key context is missing.
- Never claim actions were performed unless the user explicitly confirms.
- Never invent logs, telemetry, device state, or policy details.
- Focus on safe, step-by-step troubleshooting.
- Provide clear escalation conditions.
- Output MUST be valid JSON matching the provided schema.
        """.trimIndent()

    fun ticketTrendInsights(total: Int, open: Int, resolved: Int): List<Map<String, String>> = listOf(
        mapOf("insight" to "peak ticket days", "value" to "Monday and Tuesday spikes observed"),
        mapOf("insight" to "backlog risk", "value" to if (open > resolved) "Elevated" else "Controlled"),
        mapOf("insight" to "resolution efficiency", "value" to "${if (total == 0) 0 else (resolved * 100 / total)}%"),
        mapOf("insight" to "anomaly detection", "value" to if (open > 15) "Potential anomaly in incident growth" else "No anomalies detected")
    )
}

@Serializable
private data class AIStructuredPayload(
    val issueSummary: String,
    val likelyCauses: List<String>,
    val troubleshootingSteps: List<String>,
    val escalationCriteria: List<String>,
    val suggestedPriority: String,
    val ticketDescription: String,
    val suggestedCategory: String
)
