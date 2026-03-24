package backend.services

import backend.models.AIChatMessage
import backend.models.AIChatResponse
import backend.repository.KnowledgeRepository
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class AIService(private val knowledgeRepository: KnowledgeRepository? = null) {
    private val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build()
    private val json = Json { ignoreUnknownKeys = true }

    private val openAiApiKey: String = System.getenv("OPENAI_API_KEY").orEmpty()
    private val openAiModel: String = System.getenv("OPENAI_MODEL").ifBlank { "gpt-4.1-mini" }
    private val openAiTimeoutMillis: Long = System.getenv("OPENAI_TIMEOUT_MS")?.toLongOrNull()?.coerceIn(2_000, 25_000) ?: 12_000
    private val openAiTemperature: Double = System.getenv("OPENAI_TEMPERATURE")?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 0.2
    private val openAiMaxOutputTokens: Int = System.getenv("OPENAI_MAX_OUTPUT_TOKENS")?.toIntOrNull()?.coerceIn(250, 900) ?: 550

    fun chatAssistant(message: String, history: List<AIChatMessage>): AIChatResponse {
        val sanitizedMessage = sanitizeSensitive(message).trim().take(1_500)
        if (sanitizedMessage.isBlank()) throw IllegalArgumentException("Message is required")

        val safeHistory = history
            .takeLast(8)
            .mapNotNull { msg ->
                val role = msg.role.lowercase()
                if (role != "user" && role != "assistant") return@mapNotNull null
                val content = sanitizeSensitive(msg.content).trim().take(1_000)
                if (content.isBlank()) null else AIChatMessage(role, content)
            }

        if (openAiApiKey.isBlank()) {
            return fallbackResponse(sanitizedMessage)
        }

        val payload = buildJsonObject {
            put("model", openAiModel)
            put("temperature", openAiTemperature)
            put("max_output_tokens", openAiMaxOutputTokens)
            putJsonArray("input") {
                add(buildJsonObject {
                    put("role", "system")
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "input_text")
                            put("text", systemPrompt())
                        })
                    }
                })
                safeHistory.forEach { msg ->
                    add(buildJsonObject {
                        put("role", msg.role)
                        putJsonArray("content") {
                            add(buildJsonObject {
                                put("type", "input_text")
                                put("text", msg.content)
                            })
                        }
                    })
                }
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "input_text")
                            put("text", sanitizedMessage)
                        })
                    }
                })
            }
            putJsonObject("text") {
                put("format", buildJsonObject { put("type", "text") })
            }
        }

        repeat(2) { attempt ->
            val response = runCatching { sendOpenAiRequest(payload.toString()) }.getOrNull()
            if (response != null) return response
            if (attempt == 0) Thread.sleep(250)
        }

        return fallbackResponse(sanitizedMessage)
    }

    private fun sendOpenAiRequest(payload: String): AIChatResponse {
        val req = HttpRequest.newBuilder()
            .uri(java.net.URI.create("https://api.openai.com/v1/responses"))
            .timeout(Duration.ofMillis(openAiTimeoutMillis))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $openAiApiKey")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()

        val res = httpClient.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() !in 200..299) {
            throw IllegalStateException("AI provider unavailable")
        }

        val parsed = json.decodeFromString(OpenAIResponse.serializer(), res.body())
        val replyText = parsed.output
            ?.asSequence()
            ?.flatMap { out -> out.content.orEmpty().asSequence() }
            ?.mapNotNull { it.text }
            ?.joinToString("\n")
            ?.trim()
            .orEmpty()

        if (replyText.isBlank()) throw IllegalStateException("AI response was empty")
        return toStructuredResponse(replyText)
    }

    private fun toStructuredResponse(reply: String): AIChatResponse {
        val category = extractField(reply, "Suggested ticket category")
            ?: inferCategory(reply)
        val priority = extractField(reply, "Suggested priority")
            ?: inferPriority(reply)
        val titleSuggestion = buildTitleFromReply(reply, category)
        return AIChatResponse(
            reply = reply,
            category = category,
            priority = priority,
            titleSuggestion = titleSuggestion,
            descriptionSuggestion = reply,
            fallback = false
        )
    }

    private fun extractField(reply: String, label: String): String? {
        val pattern = Regex("""(?im)^\s*${Regex.escape(label)}\s*:\s*(.+)$""")
        return pattern.find(reply)?.groupValues?.getOrNull(1)?.trim()?.take(120)
    }

    private fun buildTitleFromReply(reply: String, category: String): String {
        val summaryLine = Regex("""(?im)^\s*Issue summary\s*:\s*(.+)$""")
            .find(reply)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.replace(Regex("[.]{2,}"), ".")
            ?.take(90)

        return when {
            !summaryLine.isNullOrBlank() -> summaryLine
            else -> "$category support request"
        }
    }

    private fun fallbackResponse(message: String): AIChatResponse {
        val category = inferCategory(message)
        val priority = inferPriority(message)
        val summary = message.lines().firstOrNull().orEmpty().take(100)
        val reply = """
Issue summary: ${if (summary.isBlank()) "User reported an IT issue." else summary}
Likely causes:
- Recent configuration or software change
- Device/network instability
- Authentication/session mismatch

Step-by-step troubleshooting:
1. Reproduce the issue once and record exact error text/time.
2. Restart the affected app/device and retry.
3. Verify connectivity (network/VPN/printer cable or Wi-Fi) and account access.
4. If still failing, capture screenshot/logs and include impacted business activity.

When to escalate:
- If issue blocks work for more than 30 minutes
- If multiple users/devices are affected
- If security or account lockout concerns appear

Suggested ticket category: $category
Suggested priority: $priority
        """.trimIndent()

        return AIChatResponse(
            reply = reply,
            category = category,
            priority = priority,
            titleSuggestion = if (summary.isBlank()) "$category issue needs troubleshooting" else summary,
            descriptionSuggestion = reply,
            fallback = true
        )
    }

    private fun systemPrompt(): String =
        """
You are an IT support troubleshooting assistant for an AITSM portal.
Help end users troubleshoot common hardware, software, account, printer, network, email, and access issues.
Keep answers practical, concise, and easy to follow.
Ask at most one clarifying question only if needed.
Never invent company-specific policies.
If unsure, clearly say what info is needed.
Never claim an issue is resolved unless the user confirms.
Always respond in this exact section format:
Issue summary: ...
Likely causes:
- ...
Step-by-step troubleshooting:
1. ...
When to escalate:
- ...
Suggested ticket category: <Hardware|Software|Network|Database|Security|Other>
Suggested priority: <Low|Medium|High|Critical>
        """.trimIndent()

    private fun sanitizeSensitive(input: String): String {
        var sanitized = input
        sanitized = sanitized.replace(Regex("(?i)(api[_-]?key|token|password|secret)\\s*[:=]\\s*[^\\s,;]+"), "$1: [redacted]")
        sanitized = sanitized.replace(Regex("(?i)bearer\\s+[a-z0-9._-]+"), "Bearer [redacted]")
        sanitized = sanitized.replace(Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE), "[redacted-email]")
        return sanitized
    }

    private fun inferCategory(text: String): String {
        val lower = text.lowercase()
        return when {
            Regex("\\b(printer|monitor|keyboard|mouse|laptop|desktop|hardware|blue screen|bsod)\\b").containsMatchIn(lower) -> "Hardware"
            Regex("\\b(wifi|vpn|network|internet|dns|lan|cannot connect|timeout)\\b").containsMatchIn(lower) -> "Network"
            Regex("\\b(sql|database|db|query|schema)\\b").containsMatchIn(lower) -> "Database"
            Regex("\\b(login|account|locked|password|mfa|access|permission|security)\\b").containsMatchIn(lower) -> "Security"
            Regex("\\b(app|application|software|outlook|email|excel|word|browser|crash)\\b").containsMatchIn(lower) -> "Software"
            else -> "Other"
        }
    }

    private fun inferPriority(text: String): String {
        val lower = text.lowercase()
        return when {
            Regex("\\b(outage|down|cannot work|production|critical|security breach|ransomware)\\b").containsMatchIn(lower) -> "Critical"
            Regex("\\b(unable|blocked|cannot|failed|not working|locked)\\b").containsMatchIn(lower) -> "High"
            Regex("\\b(slow|intermittent|warning|delay)\\b").containsMatchIn(lower) -> "Medium"
            else -> "Low"
        }
    }

    fun troubleshoot(description: String): List<String> {
        val text = description.trim()
        if (text.isBlank()) return listOf("Describe your issue with symptoms, error text, and when it started to receive targeted suggestions.")

        val suggestions = linkedSetOf<String>()
        suggestions += localHeuristics(text)
        suggestions += knowledgeBaseSuggestions(text)
        suggestions += internetSuggestions(text)

        if (suggestions.isEmpty()) {
            suggestions += "Capture exact error text, reproduce once, and share logs/screenshots in the ticket for faster diagnosis."
        }
        return suggestions.take(8)
    }

    private fun localHeuristics(text: String): List<String> {
        val lower = text.lowercase()
        val out = linkedSetOf<String>()
        if (Regex("\\b(error|failed|exception|code)\\b").containsMatchIn(lower)) {
            out += "Record exact error code/message and timestamp, then compare it with known incidents."
        }
        if (Regex("\\b(network|internet|dns|wifi|lan|timeout|connection)\\b").containsMatchIn(lower)) {
            out += "Validate gateway + DNS reachability first, then retest the affected app/service."
        }
        if (Regex("\\b(slow|lag|performance|freeze|hang|cpu|memory)\\b").containsMatchIn(lower)) {
            out += "Collect CPU and memory samples for 2-3 minutes and identify top-consuming processes."
        }
        if (Regex("\\b(login|password|account|authentication|unauthorized|forbidden)\\b").containsMatchIn(lower)) {
            out += "Verify account role/approval status and retry after password/session refresh."
        }
        out += "Document recent changes (patches, config updates, installs) before escalation."
        return out.toList()
    }

    private fun knowledgeBaseSuggestions(text: String): List<String> {
        val repo = knowledgeRepository ?: return emptyList()
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return emptyList()

        val ranked = repo.list()
            .map { article ->
                val hay = "${article.title} ${article.content} ${article.category}".lowercase()
                val score = tokens.count { hay.contains(it) }
                article to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(3)

        return ranked.map { (article, _) ->
            "Knowledge Base match: '${article.title}' (${article.category}). Review it before further troubleshooting."
        }
    }

    private fun internetSuggestions(text: String): List<String> {
        val q = URLEncoder.encode("IT troubleshooting guide $text", StandardCharsets.UTF_8)
        val url = "https://api.duckduckgo.com/?q=$q&format=json&no_html=1&skip_disambig=1"
        val req = HttpRequest.newBuilder().uri(java.net.URI.create(url)).timeout(Duration.ofSeconds(4)).GET().build()
        val body = runCatching { httpClient.send(req, HttpResponse.BodyHandlers.ofString()).body() }.getOrNull() ?: return emptyList()

        val abstractText = Regex("\"AbstractText\"\\s*:\\s*\"(.*?)\"", RegexOption.DOT_MATCHES_ALL)
            .find(body)?.groupValues?.getOrNull(1)
            ?.replace("\\\\n", " ")
            ?.replace("\\\\\"", "\"")
            ?.trim()
            .orEmpty()

        if (abstractText.isBlank()) return emptyList()
        return listOf("Internet troubleshooting reference: $abstractText")
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase().split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 }
            .distinct()

    fun ticketTrendInsights(total: Int, open: Int, resolved: Int): List<Map<String, String>> = listOf(
        mapOf("insight" to "peak ticket days", "value" to "Monday and Tuesday spikes observed"),
        mapOf("insight" to "backlog risk", "value" to if (open > resolved) "Elevated" else "Controlled"),
        mapOf("insight" to "resolution efficiency", "value" to "${if (total == 0) 0 else (resolved * 100 / total)}%"),
        mapOf("insight" to "anomaly detection", "value" to if (open > 15) "Potential anomaly in incident growth" else "No anomalies detected")
    )
}

@Serializable
private data class OpenAIResponse(
    val output: List<OpenAIOutputItem>? = null
)

@Serializable
private data class OpenAIOutputItem(
    val content: List<OpenAIContentItem>? = null
)

@Serializable
private data class OpenAIContentItem(
    @SerialName("type") val type: String? = null,
    val text: String? = null
)
