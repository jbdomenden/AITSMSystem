package backend.repository

import backend.config.AuditLogsTable
import backend.models.AuditLog
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class AuditRepository {
    fun log(userId: Int?, action: String, entity: String) = transaction {
        AuditLogsTable.insert {
            it[AuditLogsTable.userId] = userId
            it[AuditLogsTable.action] = action
            it[AuditLogsTable.entity] = entity
            it[timestamp] = LocalDateTime.now()
        }
    }

    fun all(): List<AuditLog> = transaction {
        AuditLogsTable.selectAll().map {
            AuditLog(it[AuditLogsTable.id], it[AuditLogsTable.userId], it[AuditLogsTable.action], it[AuditLogsTable.entity], it[AuditLogsTable.timestamp].toString())
        }
    }
}
