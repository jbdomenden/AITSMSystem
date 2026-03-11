package backend.services

class AIService {
    fun troubleshoot(description: String): List<String> {
        if (description.isBlank()) return listOf("Describe your issue to receive AI troubleshooting suggestions.")
        val suggestions = mutableListOf(
            "Have you restarted your device?",
            "Did you check the peripherals if they are properly connected?",
            "Ensure all cables are properly plugged in.",
            "Try reconnecting the device."
        )
        val lower = description.lowercase()
        if ("network" in lower || "internet" in lower) suggestions += "Run network diagnostics and verify LAN connectivity."
        if ("slow" in lower) suggestions += "Check running background processes and clear temporary files."
        if ("error" in lower) suggestions += "Capture the error code and cross-reference with the knowledge base."
        return suggestions
    }

    fun ticketTrendInsights(total: Int, open: Int, resolved: Int): List<Map<String, String>> = listOf(
        mapOf("insight" to "peak ticket days", "value" to "Monday and Tuesday spikes observed"),
        mapOf("insight" to "backlog risk", "value" to if (open > resolved) "Elevated" else "Controlled"),
        mapOf("insight" to "resolution efficiency", "value" to "${if (total == 0) 0 else (resolved * 100 / total)}%"),
        mapOf("insight" to "anomaly detection", "value" to if (open > 15) "Potential anomaly in incident growth" else "No anomalies detected")
    )
}
