package backend.queries

import backend.config.SystemSettingsTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

class SystemSettingsQueries {
    fun getByKey(key: String): String? = transaction {
        SystemSettingsTable.selectAll()
            .where { SystemSettingsTable.key eq key }
            .singleOrNull()
            ?.get(SystemSettingsTable.value)
    }

    fun upsert(key: String, value: String) = transaction {
        val existing = SystemSettingsTable.selectAll().where { SystemSettingsTable.key eq key }.singleOrNull()
        if (existing == null) {
            SystemSettingsTable.insert {
                it[SystemSettingsTable.key] = key
                it[SystemSettingsTable.value] = value
                it[updatedAt] = LocalDateTime.now()
            }
        } else {
            SystemSettingsTable.update({ SystemSettingsTable.key eq key }) {
                it[SystemSettingsTable.value] = value
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }
}
