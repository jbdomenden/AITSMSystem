package backend.services

import backend.models.Ticket
import backend.models.TicketStatus
import backend.models.UserRole
import backend.repository.TicketRepository
import backend.repository.UserRepository
import backend.security.Permission
import backend.security.PermissionMatrix
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong

class SlaAutomationService(
    private val tickets: TicketRepository,
    private val users: UserRepository,
    private val notifications: NotificationService,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    private val lastEvaluationEpoch = AtomicLong(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start(application: Application) {
        scope.launch {
            while (isActive) {
                runCatching { evaluate(force = true) }
                    .onFailure { application.environment.log.error("SLA automation evaluation failed", it) }
                delay(60_000)
            }
        }
        application.monitor.subscribe(ApplicationStopping) { scope.cancel() }
    }

    fun evaluateIfDue() = evaluate(force = false)

    internal fun evaluate(force: Boolean) {
        val epoch = clock.instant().epochSecond
        if (!force) {
            val previous = lastEvaluationEpoch.get()
            if (epoch - previous < 30 || !lastEvaluationEpoch.compareAndSet(previous, epoch)) return
        } else {
            lastEvaluationEpoch.set(epoch)
        }

        val all = tickets.allForSla()
        val active = all.filter { it.status !in setOf(TicketStatus.RESOLVED, TicketStatus.CLOSED) }
        val completed = all.filter { it.status in setOf(TicketStatus.RESOLVED, TicketStatus.CLOSED) }
        val recipients = users.listUsers()
            .filter { Permission.TICKET_MANAGE in PermissionMatrix.permissions(it.role) }
            .map { it.id }
        val now = LocalDateTime.now(clock)

        active.forEach { ticket ->
            evaluateClock(ticket, "response", ticket.slaResponseTargetMinutes, ticket.slaResponseRemainingMinutes,
                ticket.slaFirstResponseAt == null, recipients, now)
            evaluateClock(ticket, "resolution", ticket.slaResolutionTargetMinutes, ticket.slaRemainingMinutes,
                true, recipients, now)

            val comparable = completed.filter { it.priority == ticket.priority && it.category == ticket.category }
            val breachRate = if (comparable.isEmpty()) 0.0 else comparable.count { it.slaResolutionState == "BREACHED" }.toDouble() / comparable.size
            val risk = assessSlaRisk(ticket, active.size, breachRate)
            tickets.recordRiskPrediction(ticket.id, risk.score, risk.level, risk.reasons)
            if (risk.level in setOf("HIGH", "CRITICAL")) {
                recipients.forEach { recipient ->
                    notifications.push(
                        recipient,
                        "${risk.level.lowercase().replaceFirstChar { it.titlecase() }} SLA breach risk",
                        "Ticket #${ticket.id} has a ${risk.score}% predicted breach risk: ${risk.reasons.take(2).joinToString("; ")}.",
                        "ticket_sla_risk",
                        relatedTicketId = ticket.id,
                        eventKey = "sla-risk:${ticket.id}:${risk.level}:$recipient"
                    )
                }
            }
        }
    }

    private fun evaluateClock(
        ticket: Ticket,
        clockType: String,
        targetMinutes: Int?,
        remainingMinutes: Long?,
        active: Boolean,
        adminRecipients: List<Int>,
        now: LocalDateTime
    ) {
        if (!active || targetMinutes == null || targetMinutes <= 0 || remainingMinutes == null) return
        val consumed = 1.0 - remainingMinutes.toDouble() / targetMinutes
        if (remainingMinutes < 0) {
            val claimed = if (clockType == "response") tickets.claimResponseBreach(ticket.id, now)
                else tickets.claimResolutionBreach(ticket.id, now)
            if (claimed) notifySlaEvent(ticket, clockType, "breached", adminRecipients)
            return
        }
        val level = when {
            consumed >= 0.9 -> 90
            consumed >= 0.75 -> 75
            else -> return
        }
        val claimed = if (clockType == "response") tickets.claimResponseWarning(ticket.id, level)
            else tickets.claimResolutionWarning(ticket.id, level)
        if (claimed) notifySlaEvent(ticket, clockType, "$level% consumed", adminRecipients)
    }

    private fun notifySlaEvent(ticket: Ticket, clockType: String, event: String, adminRecipients: List<Int>) {
        val title = "Ticket #${ticket.id} SLA $event"
        val message = "The $clockType SLA for ticket #${ticket.id} is $event (${ticket.priority} priority)."
        (adminRecipients + ticket.userId).distinct().forEach { recipient ->
            notifications.push(
                recipient, title, message, "ticket_sla_${clockType}_${event.substringBefore('%').replace(' ', '_')}",
                relatedTicketId = ticket.id,
                eventKey = "sla:${ticket.id}:$clockType:${event.replace(' ', '-')}:$recipient"
            )
        }
    }
}
