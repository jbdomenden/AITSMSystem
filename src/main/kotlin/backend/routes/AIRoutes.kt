package backend.routes

import backend.models.AIChatRequest
import backend.services.AIService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private val aiRateLimitWindowMs = 60_000L
private val aiRateLimitMaxRequests = 20
private val aiRateBucket = ConcurrentHashMap<String, MutableList<Long>>()

fun Route.aiRoutes(service: AIService) {
    post("/api/ai-assistant/chat") {
        val key = buildRateLimitKey(
            call.request.headers["X-User-Id"].orEmpty(),
            call.request.headers["X-Forwarded-For"].orEmpty()
        )
        if (isRateLimited(key)) {
            call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "Rate limit exceeded. Please try again shortly."))
            return@post
        }

        val request = call.receive<AIChatRequest>()
        if (request.message.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Message is required"))
            return@post
        }
        if (request.message.length > 2_000) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Message exceeds 2000 characters"))
            return@post
        }

        val response = service.chatAssistant(request.message, request.history)
        call.respond(response)
    }
}

private fun buildRateLimitKey(userId: String, remoteHost: String): String =
    if (userId.isNotBlank()) "u:$userId" else "ip:$remoteHost"

private fun isRateLimited(key: String): Boolean {
    val now = Instant.now().toEpochMilli()
    val bucket = aiRateBucket.computeIfAbsent(key) { mutableListOf() }
    synchronized(bucket) {
        bucket.removeIf { timestamp -> now - timestamp > aiRateLimitWindowMs }
        if (bucket.size >= aiRateLimitMaxRequests) return true
        bucket += now
    }
    return false
}
