package backend.repository

import backend.config.KnowledgeArticlesTable
import backend.models.KnowledgeArticle
import backend.models.KnowledgeRequest
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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

    private fun toArticle(row: ResultRow) = KnowledgeArticle(
        id = row[KnowledgeArticlesTable.id],
        title = row[KnowledgeArticlesTable.title],
        content = row[KnowledgeArticlesTable.content],
        category = row[KnowledgeArticlesTable.category],
        createdAt = row[KnowledgeArticlesTable.createdAt].toString()
    )
}
