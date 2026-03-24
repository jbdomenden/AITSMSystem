package backend.routes

import backend.models.ai.AIChatRequest
import backend.models.ai.AIConfigUpdateRequest
import backend.models.ai.AIConnectionTestRequest
import backend.models.ai.AIConnectionTestResponse
import backend.models.ai.AIModelsResponse
import backend.models.ai.AITicketDraftRequest
import backend.services.ai.AIChatService
import backend.services.ai.AIConfigService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val aiRateLimitWindowMs = 60_000L
private val aiRateLimitMaxRequests = 20
private val aiRateBucket = ConcurrentHashMap<String, MutableList<Long>>()

fun Route.aiRoutes(chatService: AIChatService, configService: AIConfigService) {
    post("/api/ai/chat") {
        val rateKey = buildRateLimitKey(
            call.request.headers["X-User-Id"].orEmpty(),
            call.request.headers["X-Forwarded-For"].orEmpty()
        )
        if (isRateLimited(rateKey)) {
            call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "Rate limit exceeded. Please try again shortly."))
            return@post
        }

        val request = call.receive<AIChatRequest>()
        val message = request.message.trim()
        if (message.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Message is required"))
            return@post
        }
        if (message.length > 2_000) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Message exceeds 2000 characters"))
            return@post
        }

        call.respond(chatService.chat(call.resolveSessionId(), message))
    }

    post("/api/ai-assistant/chat") {
        val request = call.receive<AIChatRequest>()
        val message = request.message.trim()
        if (message.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Message is required"))
            return@post
        }
        call.respond(chatService.chat(call.resolveSessionId(), message))
    }

    post("/api/ai/clear") {
        chatService.clearConversation(call.resolveSessionId())
        call.respond(HttpStatusCode.OK, mapOf("cleared" to true))
    }

    post("/api/ai/create-ticket-draft") {
        val request = call.receive<AITicketDraftRequest>()
        call.respond(chatService.createTicketDraft(request))
    }

    get("/api/ai/config") {
        call.respond(configService.snapshot())
    }

    post("/api/ai/config") {
        val request = call.receive<AIConfigUpdateRequest>()
        call.respond(configService.update(request))
    }

    get("/api/ai/models") {
        val cfg = configService.snapshot()
        val models = chatService.getModels()
        call.respond(AIModelsResponse(models = models, currentModel = cfg.model, baseUrl = cfg.baseUrl))
    }

    post("/api/ai/test") {
        val request = call.receive<AIConnectionTestRequest>()
        val result = chatService.testConnection(request.baseUrl, request.model)
        val response = if (result.ok) {
            AIConnectionTestResponse(success = true, message = "Connected to Ollama successfully")
        } else {
            AIConnectionTestResponse(success = false, message = result.errorMessage ?: "Failed to connect to Ollama")
        }
        if (result.ok) {
            call.respond(HttpStatusCode.OK, response)
        } else {
            call.respond(HttpStatusCode.BadGateway, response)
        }
    }
}

private fun ApplicationCall.resolveSessionId(): String {
    val raw = request.headers["X-AI-Session-Id"].orEmpty().trim()
    return if (raw.isNotBlank()) raw.take(128) else "anon-${UUID.randomUUID()}"
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
