package backend.repository

import backend.config.AuditLogsTable
import backend.models.AuditLog
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class AuditRepository {
    fun log(userId: Int?, action: String, entity: String, result: String = "SUCCESS", target: String? = null, requestId: String? = null, sourceIp: String? = null, userAgent: String? = null, metadata: String? = null) = transaction {
        AuditLogsTable.insert {
            it[AuditLogsTable.userId] = userId
            it[AuditLogsTable.action] = action
            it[AuditLogsTable.entity] = entity
            it[AuditLogsTable.target] = target?.take(200)
            it[AuditLogsTable.result] = result.take(20)
            it[AuditLogsTable.requestId] = requestId?.take(80)
            it[AuditLogsTable.sourceIp] = sourceIp?.take(45)
            it[AuditLogsTable.userAgent] = userAgent?.take(300)
            it[AuditLogsTable.metadata] = metadata
            it[timestamp] = LocalDateTime.now()
        }
    }

    fun all(): List<AuditLog> = transaction {
        AuditLogsTable.selectAll().map {
            AuditLog(it[AuditLogsTable.id], it[AuditLogsTable.userId], it[AuditLogsTable.action], it[AuditLogsTable.entity], it[AuditLogsTable.timestamp].toString(), it[AuditLogsTable.target], it[AuditLogsTable.result], it[AuditLogsTable.requestId], it[AuditLogsTable.sourceIp], it[AuditLogsTable.userAgent], it[AuditLogsTable.metadata])
        }
    }

    fun recentForActor(userId: Int, limit: Int = 25): List<AuditLog> = transaction {
        AuditLogsTable.selectAll().where { AuditLogsTable.userId eq userId }
            .orderBy(AuditLogsTable.timestamp, SortOrder.DESC).limit(limit.coerceIn(1, 100)).map {
                AuditLog(it[AuditLogsTable.id], it[AuditLogsTable.userId], it[AuditLogsTable.action], it[AuditLogsTable.entity], it[AuditLogsTable.timestamp].toString(), it[AuditLogsTable.target], it[AuditLogsTable.result], it[AuditLogsTable.requestId], null, it[AuditLogsTable.userAgent], it[AuditLogsTable.metadata])
            }
    }
}
