package backend.services

import backend.config.SLAPoliciesTable
import backend.models.SLA
import backend.repository.AuditRepository
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class SLAService(private val audit: AuditRepository) {
    private val defaultPolicies = listOf(
        "Critical" to (15 to 120),
        "High" to (30 to 240),
        "Medium" to (60 to 480),
        "Low" to (120 to 1440)
    )

    fun seedDefaults() = transaction {
        defaultPolicies.forEach { (priority, times) ->
            val existing = SLAPoliciesTable
                .selectAll()
                .where { SLAPoliciesTable.priority eq priority }
                .orderBy(SLAPoliciesTable.id to SortOrder.ASC)
                .toList()

            if (existing.isEmpty()) {
                SLAPoliciesTable.insert {
                    it[SLAPoliciesTable.priority] = priority
                    it[responseTime] = times.first
                    it[resolutionTime] = times.second
                }
            } else {
                val duplicateIds = existing.drop(1).map { it[SLAPoliciesTable.id] }
                duplicateIds.forEach { duplicateId ->
                    SLAPoliciesTable.deleteWhere { SLAPoliciesTable.id eq duplicateId }
                }
            }
        }
    }

    fun policies(): List<SLA> = transaction {
        SLAPoliciesTable
            .selectAll()
            .orderBy(SLAPoliciesTable.id to SortOrder.ASC)
            .map {
                SLA(
                    it[SLAPoliciesTable.id],
                    it[SLAPoliciesTable.priority],
                    it[SLAPoliciesTable.responseTime],
                    it[SLAPoliciesTable.resolutionTime]
                )
            }
    }

    fun update(priorityValue: String, responseMinutes: Int, resolutionMinutes: Int, actorId: Int): SLA {
        val priority = priorityValue.trim().lowercase().replaceFirstChar { it.titlecase() }
        require(defaultPolicies.any { it.first == priority }) { "Unsupported SLA priority" }
        require(responseMinutes in 1..43_200) { "Response time must be between 1 minute and 30 days" }
        require(resolutionMinutes in 1..43_200) { "Resolution time must be between 1 minute and 30 days" }
        require(responseMinutes <= resolutionMinutes) { "Response time cannot exceed resolution time" }

        val updated = transaction {
            val affected = SLAPoliciesTable.update({ SLAPoliciesTable.priority eq priority }) {
                it[responseTime] = responseMinutes
                it[resolutionTime] = resolutionMinutes
            }
            check(affected == 1) { "SLA policy was not found" }
            SLAPoliciesTable.selectAll().where { SLAPoliciesTable.priority eq priority }.single().let {
                SLA(it[SLAPoliciesTable.id], it[SLAPoliciesTable.priority], it[SLAPoliciesTable.responseTime], it[SLAPoliciesTable.resolutionTime])
            }
        }
        audit.log(actorId, "Updated $priority SLA to response=$responseMinutes resolution=$resolutionMinutes minutes", "sla_policies")
        return updated
    }
}
