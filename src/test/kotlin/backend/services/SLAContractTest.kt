package backend.services

import backend.models.TicketStatus
import backend.repository.isTicketSlaOverdue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
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
}
