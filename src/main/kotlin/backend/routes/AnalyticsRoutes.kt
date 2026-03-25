package backend.routes

import backend.models.UserRole
import backend.models.TicketStatus
import backend.models.AnalyticsResponse
import backend.security.requireRole
import backend.services.AIService
import backend.services.MonitoringService
import backend.services.TicketService
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.analyticsRoutes(ticketService: TicketService, monitoringService: MonitoringService, aiService: AIService) {
    route("/api/analytics") {
        get("/ticket-trends") {
            if (!call.requireRole(UserRole.ADMIN)) return@get
            val tickets = ticketService.list(null, true, 500, 0).items
            val total = tickets.size
            val open = tickets.count { it.status == TicketStatus.OPEN }
            val resolved = tickets.count { it.status == TicketStatus.RESOLVED }
            call.respond(AnalyticsResponse("ticket-trends", aiService.ticketTrendInsights(total, open, resolved)))
        }
        get("/system-health") {
            if (!call.requireRole(UserRole.ADMIN)) return@get
            val devices = monitoringService.devices()
            val avgCpu = devices.map { it.cpuUsage }.average().toInt()
            val health = (100 - avgCpu).coerceAtLeast(0)
            call.respond(AnalyticsResponse("system-health", listOf(
                mapOf("insight" to "abnormal spikes", "value" to if (avgCpu > 75) "Detected" else "Stable"),
                mapOf("insight" to "system health score", "value" to "$health"),
                mapOf("insight" to "usage stability", "value" to if (avgCpu < 65) "High" else "Moderate")
            )))
        }
    }
}
