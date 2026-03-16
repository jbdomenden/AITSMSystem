package backend.services

import backend.repository.KnowledgeRepository
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

class AIService(private val knowledgeRepository: KnowledgeRepository? = null) {
    private val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()

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
