package backend.services

import backend.models.Colleague
import backend.models.ColleagueRequest
import backend.models.ColleagueRequestStatus
import backend.models.ConversationSummary
import backend.models.DirectMessage
import backend.repository.AuditRepository
import backend.repository.ColleagueRepository

class ColleagueService(private val repository: ColleagueRepository, private val audit: AuditRepository, private val notifications: NotificationService) {
    fun search(actorId: Int, query: String) = repository.searchableUsers(actorId, query.trim())
    fun colleagues(actorId: Int) = repository.colleagues(actorId)
    fun requests(actorId: Int) = repository.requestsFor(actorId)
    fun sendRequest(actorId: Int, recipientId: Int): ColleagueRequest {
        require(actorId != recipientId) { "You cannot add yourself." }
        require(repository.searchableUsers(actorId, "").any { it.id == recipientId }) { "Colleague account is unavailable." }
        require(repository.requestBetween(actorId, recipientId) == null) { "A colleague request or connection already exists." }
        return repository.request(actorId, recipientId).also { audit.log(actorId, "Sent colleague request to user #$recipientId", "colleague_requests"); notifications.push(recipientId, "New colleague request", "You have a new colleague request.", "colleague_request") }
    }
    fun respond(actorId: Int, requestId: Int, accepted: Boolean): ColleagueRequest {
        val result = repository.respond(requestId, actorId, if (accepted) ColleagueRequestStatus.ACCEPTED else ColleagueRequestStatus.DECLINED)
            ?: throw SecurityException("This colleague request is unavailable.")
        audit.log(actorId, "${if (accepted) "Accepted" else "Declined"} colleague request #$requestId", "colleague_requests")
        if (accepted) notifications.push(result.senderId, "Colleague request accepted", "Your colleague request was accepted.", "colleague_request")
        return result
    }
    fun cancel(actorId: Int, requestId: Int) { require(repository.cancel(requestId, actorId)) { "This colleague request cannot be cancelled." } }
    fun remove(actorId: Int, colleagueId: Int) { require(repository.remove(actorId, colleagueId)) { "Colleague connection not found." } }
    fun messages(actorId: Int, colleagueId: Int): List<DirectMessage> { ensureColleague(actorId, colleagueId); repository.markRead(actorId, colleagueId); return repository.messages(actorId, colleagueId) }
    fun sendMessage(actorId: Int, colleagueId: Int, body: String): DirectMessage {
        ensureColleague(actorId, colleagueId); val normalized = body.trim(); require(normalized.isNotBlank() && normalized.length <= 2000) { "Message must be between 1 and 2000 characters." }
        return repository.sendMessage(actorId, colleagueId, normalized).also { notifications.push(colleagueId, "New message", "You received a new direct message.", "direct_message"); audit.log(actorId, "Sent direct message to user #$colleagueId", "direct_messages") }
    }
    fun conversations(actorId: Int): List<ConversationSummary> = colleagues(actorId).map { colleague ->
        val messages = repository.messages(actorId, colleague.id); ConversationSummary(colleague, messages.lastOrNull(), messages.count { it.recipientId == actorId && it.readAt == null }.toLong())
    }.sortedByDescending { it.lastMessage?.sentAt ?: "" }
    private fun ensureColleague(actorId: Int, colleagueId: Int) { require(repository.colleagues(actorId).any { it.id == colleagueId }) { "You can only message accepted colleagues." } }
}
