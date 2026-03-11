package backend.services

import backend.models.KnowledgeRequest
import backend.repository.AuditRepository
import backend.repository.KnowledgeRepository

class KnowledgeService(private val repository: KnowledgeRepository, private val auditRepository: AuditRepository) {
    fun list() = repository.list()
    fun create(req: KnowledgeRequest, userId: Int?) = repository.create(req).also { auditRepository.log(userId, "Created article #${it.id}", "knowledge_articles") }
    fun update(id: Int, req: KnowledgeRequest, userId: Int?) = repository.update(id, req).also { auditRepository.log(userId, "Updated article #$id", "knowledge_articles") }
}
