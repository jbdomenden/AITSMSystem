package backend.services

class AIService {
    fun troubleshoot(description: String): List<String> {
        val text = description.trim()
        if (text.isBlank()) return listOf("Describe your issue with symptoms, error text, and when it started to receive targeted suggestions.")

        val lower = text.lowercase()
        val suggestions = mutableListOf<String>()

        fun add(step: String) {
            if (suggestions.none { it.equals(step, ignoreCase = true) }) suggestions += step
        }

        if (Regex("\\b(error|failed|exception|code)\\b").containsMatchIn(lower)) {
            add("Capture the exact error message/code and timestamp, then compare with known issues in the Knowledge Base.")
            add("Reproduce once after restarting the affected app/service to confirm whether the issue is persistent.")
        }
        if (Regex("\\b(network|internet|dns|wifi|lan|timeout|connection)\\b").containsMatchIn(lower)) {
            add("Check LAN connectivity first (gateway ping + DNS resolution) before escalating application-side fixes.")
            add("Run `ipconfig /flushdns` (Windows) or restart network service and retest access to the target system.")
        }
        if (Regex("\\b(slow|lag|performance|freeze|hang|cpu|memory)\\b").containsMatchIn(lower)) {
            add("Collect CPU/RAM usage for 2-3 minutes and identify top processes before restarting the endpoint.")
            add("Close non-essential background apps and verify whether the slowdown is system-wide or app-specific.")
        }
        if (Regex("\\b(login|password|account|authentication|unauthorized|forbidden)\\b").containsMatchIn(lower)) {
            add("Verify account role and password freshness; if recently changed, sign out all sessions and retry.")
            add("Check whether the account is locked or pending verification/approval in User Management.")
        }
        if (Regex("\\b(printer|usb|keyboard|mouse|monitor|device|driver)\\b").containsMatchIn(lower)) {
            add("Reconnect hardware and validate driver status in Device Manager; roll back or update driver if recently changed.")
        }

        add("Document what changed before the incident (updates, new software, policy changes) to speed up root-cause analysis.")
        add("If unresolved after these steps, submit/update the ticket with findings, logs, and screenshots for faster escalation.")

        return suggestions.take(6)
    }

    fun ticketTrendInsights(total: Int, open: Int, resolved: Int): List<Map<String, String>> = listOf(
        mapOf("insight" to "peak ticket days", "value" to "Monday and Tuesday spikes observed"),
        mapOf("insight" to "backlog risk", "value" to if (open > resolved) "Elevated" else "Controlled"),
        mapOf("insight" to "resolution efficiency", "value" to "${if (total == 0) 0 else (resolved * 100 / total)}%"),
        mapOf("insight" to "anomaly detection", "value" to if (open > 15) "Potential anomaly in incident growth" else "No anomalies detected")
    )
}
