package backend.services

import backend.config.SLAPoliciesTable
import backend.models.SLA
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class SLAService {
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
                val keepId = existing.first()[SLAPoliciesTable.id]
                SLAPoliciesTable.update({ SLAPoliciesTable.id eq keepId }) {
                    it[responseTime] = times.first
                    it[resolutionTime] = times.second
                }
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
}
