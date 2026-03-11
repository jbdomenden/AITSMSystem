package backend.services

import backend.config.SLAPoliciesTable
import backend.models.SLA
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class SLAService {
    fun seedDefaults() = transaction {
        listOf(
            "Critical" to (15 to 120),
            "High" to (30 to 240),
            "Medium" to (60 to 480),
            "Low" to (120 to 1440)
        ).forEach { (priority, times) ->
            SLAPoliciesTable.insertIgnore {
                it[SLAPoliciesTable.priority] = priority
                it[responseTime] = times.first
                it[resolutionTime] = times.second
            }
        }
    }

    fun policies(): List<SLA> = transaction { SLAPoliciesTable.selectAll().map { SLA(it[SLAPoliciesTable.id], it[SLAPoliciesTable.priority], it[SLAPoliciesTable.responseTime], it[SLAPoliciesTable.resolutionTime]) } }
}
