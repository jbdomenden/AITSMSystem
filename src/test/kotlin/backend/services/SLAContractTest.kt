package backend.services

import backend.models.Ticket
import backend.models.TicketStatus
import backend.repository.isTicketSlaOverdue
import backend.repository.slaState
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SLAContractTest {
    @Test fun `only active tickets can be currently overdue`() {
        assertTrue(isTicketSlaOverdue(-1, TicketStatus.OPEN))
        assertTrue(isTicketSlaOverdue(-1, TicketStatus.PENDING))
        assertFalse(isTicketSlaOverdue(1, TicketStatus.OPEN))
        assertFalse(isTicketSlaOverdue(-1, TicketStatus.RESOLVED))
        assertFalse(isTicketSlaOverdue(-1, TicketStatus.CLOSED))
    }

    @Test fun `sla policy changes are protected validated and audited`() {
        val route = Files.readString(Path.of("src/main/kotlin/backend/routes/SLARoutes.kt"))
        val service = Files.readString(Path.of("src/main/kotlin/backend/services/SLAService.kt"))
        val settings = Files.readString(Path.of("src/main/resources/static/frontend/settings/js/settings.js"))

        assertTrue(route.contains("requirePermission(Permission.SECURITY_MANAGE)"))
        assertTrue(route.contains("requireReauthentication(reauthentication, ReauthScope.SECURITY_CONFIG)"))
        assertTrue(service.contains("responseMinutes <= resolutionMinutes"))
        assertTrue(service.contains("audit.log(actorId"))
        assertTrue(settings.contains("method: 'PUT'"))
        assertTrue(settings.contains("Response time cannot exceed resolution time"))
    }

    @Test fun `startup seeds missing policies without overwriting saved targets`() {
        val service = Files.readString(Path.of("src/main/kotlin/backend/services/SLAService.kt"))
        val existingBranch = service.substringAfter("if (existing.isEmpty())").substringBefore("fun policies")
        assertTrue(existingBranch.contains("SLAPoliciesTable.insert"))
        assertFalse(existingBranch.substringAfter("} else {").contains("SLAPoliciesTable.update"))
    }

    @Test fun `sla clocks distinguish active met and breached outcomes`() {
        val now = LocalDateTime.of(2026, 10, 14, 12, 0)
        val due = now.plusMinutes(30)
        assertEquals("ACTIVE", slaState(null, due, null, now))
        assertEquals("MET", slaState(now.plusMinutes(10), due, null, now))
        assertEquals("BREACHED", slaState(now.plusMinutes(31), due, null, now))
        assertEquals("BREACHED", slaState(null, due, now, now))
    }

    @Test fun `risk model is explainable and rises as deadlines approach`() {
        val healthy = sampleTicket(slaRemainingMinutes = 420, slaResponseRemainingMinutes = 45)
        val endangered = sampleTicket(
            priority = "Critical",
            slaRemainingMinutes = 20,
            slaResponseRemainingMinutes = -5,
            slaResponseState = "BREACHED",
            slaReopenedCount = 1
        )
        val healthyRisk = assessSlaRisk(healthy, activeQueueSize = 2, historicalBreachRate = 0.0)
        val endangeredRisk = assessSlaRisk(endangered, activeQueueSize = 20, historicalBreachRate = 0.75)

        assertTrue(endangeredRisk.score > healthyRisk.score)
        assertTrue(endangeredRisk.level in setOf("HIGH", "CRITICAL"))
        assertTrue(endangeredRisk.reasons.any { it.contains("response", ignoreCase = true) })
        assertTrue(endangeredRisk.reasons.any { it.contains("reopened", ignoreCase = true) })
    }

    @Test fun `production migration and automation include lifecycle alerts and prediction history`() {
        val migration = Files.readString(Path.of("db/migrations/V010__sla_lifecycle_and_prediction.sql"))
        val automation = Files.readString(Path.of("src/main/kotlin/backend/services/SlaAutomationService.kt"))
        assertTrue(migration.contains("sla_response_due_at"))
        assertTrue(migration.contains("sla_resolution_breached_at"))
        assertTrue(migration.contains("sla_risk_predictions"))
        assertTrue(automation.contains("consumed >= 0.75"))
        assertTrue(automation.contains("consumed >= 0.9"))
        assertTrue(automation.contains("recordRiskPrediction"))
    }

    private fun sampleTicket(
        priority: String = "Medium",
        slaRemainingMinutes: Long,
        slaResponseRemainingMinutes: Long,
        slaResponseState: String = "ACTIVE",
        slaReopenedCount: Int = 0
    ) = Ticket(
        id = 1,
        userId = 1,
        title = "Test ticket",
        description = "Test ticket description",
        priority = priority,
        category = "Software",
        status = TicketStatus.OPEN,
        createdAt = "2026-10-14T10:00:00",
        updatedAt = "2026-10-14T10:00:00",
        slaResponseTargetMinutes = 60,
        slaResolutionTargetMinutes = 480,
        slaResponseRemainingMinutes = slaResponseRemainingMinutes,
        slaRemainingMinutes = slaRemainingMinutes,
        slaResponseState = slaResponseState,
        slaResolutionState = "ACTIVE",
        slaReopenedCount = slaReopenedCount
    )
}
