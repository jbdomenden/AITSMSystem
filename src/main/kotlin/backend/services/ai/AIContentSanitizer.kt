package backend.services.ai

object AIContentSanitizer {
    fun redact(input: String): String = input
        .replace(Regex("(?i)(api[_-]?key|token|password|secret)\\s*[:=]\\s*[^\\s,;]+"), "$1: [redacted]")
        .replace(Regex("(?i)bearer\\s+[a-z0-9._-]+"), "Bearer [redacted]")
        .replace(Regex("(?i)-----BEGIN [A-Z ]+PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]+PRIVATE KEY-----"), "[private key redacted]")
}
