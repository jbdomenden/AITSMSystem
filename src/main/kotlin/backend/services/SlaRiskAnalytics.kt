package backend.services

import backend.models.Ticket
import backend.models.TicketStatus
import kotlin.math.roundToInt

data class SlaRiskAssessment(val score: Int, val level: String, val reasons: List<String>)

internal fun assessSlaRisk(ticket: Ticket, activeQueueSize: Int, historicalBreachRate: Double): SlaRiskAssessment {
    if (ticket.status in setOf(TicketStatus.RESOLVED, TicketStatus.CLOSED)) {
        return SlaRiskAssessment(0, "LOW", listOf("Ticket is already completed"))
    }
    if (ticket.slaResolutionState == "BREACHED") {
        return SlaRiskAssessment(100, "CRITICAL", listOf("Resolution deadline has already been breached"))
    }

    var score = 0.0
    val reasons = mutableListOf<String>()
    val target = ticket.slaResolutionTargetMinutes?.takeIf { it > 0 }
    val remaining = ticket.slaRemainingMinutes
    if (target != null && remaining != null) {
        val consumed = (1.0 - remaining.toDouble() / target).coerceIn(0.0, 1.0)
        score += consumed * 65.0
        if (consumed >= 0.9) reasons += "More than 90% of the resolution window is consumed"
        else if (consumed >= 0.75) reasons += "More than 75% of the resolution window is consumed"
    }

    when (ticket.slaResponseState) {
        "BREACHED" -> { score += 15; reasons += "First-response SLA was breached" }
        "ACTIVE" -> {
            val responseTarget = ticket.slaResponseTargetMinutes?.takeIf { it > 0 }
            val responseRemaining = ticket.slaResponseRemainingMinutes
            if (responseTarget != null && responseRemaining != null && responseRemaining.toDouble() / responseTarget <= 0.25) {
                score += 8
                reasons += "First-response deadline is close"
            }
        }
    }

    val historyContribution = historicalBreachRate.coerceIn(0.0, 1.0) * 12.0
    score += historyContribution
    if (historicalBreachRate >= 0.5) reasons += "Similar completed tickets have a high breach rate"

    val queueContribution = (activeQueueSize / 10.0).coerceAtMost(1.0) * 8.0
    score += queueContribution
    if (activeQueueSize >= 10) reasons += "The active support queue is under pressure"

    if (ticket.slaReopenedCount > 0) {
        score += (ticket.slaReopenedCount * 5).coerceAtMost(10)
        reasons += "Ticket has been reopened"
    }
    score += when (ticket.priority) { "Critical" -> 5.0; "High" -> 3.0; else -> 0.0 }

    val normalized = score.roundToInt().coerceIn(0, 100)
    val level = when {
        normalized >= 80 -> "CRITICAL"
        normalized >= 60 -> "HIGH"
        normalized >= 35 -> "MEDIUM"
        else -> "LOW"
    }
    if (reasons.isEmpty()) reasons += "SLA consumption and historical patterns are currently normal"
    return SlaRiskAssessment(normalized, level, reasons.distinct())
}
