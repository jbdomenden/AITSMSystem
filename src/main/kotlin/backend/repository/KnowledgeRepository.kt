package backend.repository

import backend.config.KnowledgeArticlesTable
import backend.models.KnowledgeArticle
import backend.models.KnowledgeRequest
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

class KnowledgeRepository {
    fun list(): List<KnowledgeArticle> = transaction { KnowledgeArticlesTable.selectAll().map(::toArticle) }

    fun create(req: KnowledgeRequest): KnowledgeArticle = transaction {
        val id = KnowledgeArticlesTable.insert {
            it[title] = req.title
            it[content] = req.content
            it[category] = req.category
            it[createdAt] = LocalDateTime.now()
        }[KnowledgeArticlesTable.id]
        list().first { it.id == id }
    }

    fun update(id: Int, req: KnowledgeRequest): KnowledgeArticle? = transaction {
        KnowledgeArticlesTable.update({ KnowledgeArticlesTable.id eq id }) {
            it[title] = req.title
            it[content] = req.content
            it[category] = req.category
        }
        KnowledgeArticlesTable.selectAll().where { KnowledgeArticlesTable.id eq id }.singleOrNull()?.let(::toArticle)
    }

    fun delete(id: Int): Boolean = transaction {
        KnowledgeArticlesTable.deleteWhere { KnowledgeArticlesTable.id eq id } > 0
    }

    fun seedDefaults() = transaction {
        if (KnowledgeArticlesTable.selectAll().limit(1).any()) return@transaction

        val now = LocalDateTime.now()
        KnowledgeArticlesTable.batchInsert(defaultArticles) { article ->
            this[KnowledgeArticlesTable.title] = article.title
            this[KnowledgeArticlesTable.content] = article.content
            this[KnowledgeArticlesTable.category] = article.category
            this[KnowledgeArticlesTable.createdAt] = now
        }
    }

    private fun toArticle(row: ResultRow) = KnowledgeArticle(
        id = row[KnowledgeArticlesTable.id],
        title = row[KnowledgeArticlesTable.title],
        content = row[KnowledgeArticlesTable.content],
        category = row[KnowledgeArticlesTable.category],
        createdAt = row[KnowledgeArticlesTable.createdAt].toString()
    )

    private val defaultArticles = listOf(
        KnowledgeRequest(
            title = "Troubleshoot a PC That Will Not Power On",
            category = "Hardware",
            content = "1. Confirm the power cable, surge protector, and wall outlet are working.\n2. Check for laptop battery charge or test with a known-good adapter.\n3. Disconnect docking stations, USB devices, and external displays.\n4. Look for motherboard, charger, or status LEDs and listen for fans or beep codes.\n5. Reseat RAM and power connections if the device was recently moved or serviced.\n6. Escalate for PSU, battery, or motherboard replacement if there are still no signs of power."
        ),
        KnowledgeRequest(
            title = "Resolve Overheating or Unexpected Shutdowns",
            category = "Hardware",
            content = "1. Verify vents are unobstructed and fans are spinning normally.\n2. Check Task Manager or system monitor for sustained CPU or memory spikes.\n3. Clean dust from vents, heat sinks, and fan intakes using approved maintenance steps.\n4. Confirm the device is on a hard surface and not inside an enclosed cabinet.\n5. Apply BIOS, firmware, and driver updates if thermal control issues are known.\n6. Replace failed fans or thermal components if shutdowns continue under normal load."
        ),
        KnowledgeRequest(
            title = "Fix Keyboard, Mouse, or Peripheral Detection Problems",
            category = "Hardware",
            content = "1. Test the peripheral on another USB port or with fresh batteries if wireless.\n2. Re-pair Bluetooth accessories and remove nearby wireless interference.\n3. Confirm the device appears in Device Manager without warning icons.\n4. Install or update the manufacturer driver package when advanced features are missing.\n5. Inspect cables, dongles, and docking stations for physical damage.\n6. Replace the accessory if it fails on multiple systems with known-good ports."
        ),
        KnowledgeRequest(
            title = "Troubleshoot Slow Application Performance",
            category = "Software",
            content = "1. Capture the affected application name, version, and the exact time slowness occurs.\n2. Check CPU, memory, and disk utilization to identify contention from other processes.\n3. Restart the application and the workstation to clear hung processes and cached sessions.\n4. Clear temporary files, browser cache, or app cache if performance degrades over time.\n5. Install pending application patches and confirm the workstation meets the required specs.\n6. Escalate with logs and screenshots if only one workflow or dataset is consistently slow."
        ),
        KnowledgeRequest(
            title = "Resolve Login and Password Issues",
            category = "Software",
            content = "1. Verify the username format, keyboard layout, and caps lock state.\n2. Confirm the account is active, licensed, and not locked by failed sign-in attempts.\n3. Reset the password and have the user retry in a private browser or fresh session.\n4. Clear saved credentials from the browser, password manager, or Windows Credential Manager.\n5. Check multifactor prompts, authenticator time sync, and conditional access requirements.\n6. Escalate with the exact error message and timestamp if authentication still fails."
        ),
        KnowledgeRequest(
            title = "Handle Application Crashes or Startup Errors",
            category = "Software",
            content = "1. Record the exact error message, crash code, and steps to reproduce the issue.\n2. Confirm whether the problem started after an update, plugin install, or configuration change.\n3. Launch the application in safe mode or disable add-ins to isolate extensions.\n4. Repair or reinstall the application if core files are missing or corrupted.\n5. Review local logs, Windows Event Viewer, or vendor diagnostics for fault details.\n6. Attach crash logs to the ticket before escalating to the application owner or vendor."
        )
    )
}
