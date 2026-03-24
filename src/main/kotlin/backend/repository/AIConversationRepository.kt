package backend.repository

import backend.config.AIConversationMessagesTable
import backend.models.ai.AIMessage
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class AIConversationRepository {
    fun appendMessage(sessionId: String, message: AIMessage) = transaction {
        AIConversationMessagesTable.insert {
            it[AIConversationMessagesTable.sessionId] = sessionId
            it[role] = message.role
            it[content] = message.content
            it[createdAt] = LocalDateTime.now()
        }
    }

    fun loadSession(sessionId: String, limit: Int): MutableList<AIMessage> = transaction {
        AIConversationMessagesTable
            .selectAll()
            .where { AIConversationMessagesTable.sessionId eq sessionId }
            .orderBy(AIConversationMessagesTable.createdAt)
            .limit(limit)
            .map { AIMessage(it[AIConversationMessagesTable.role], it[AIConversationMessagesTable.content]) }
            .toMutableList()
    }
}
