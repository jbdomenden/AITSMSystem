package backend.routes

import backend.models.ai.AIChatRequest
import backend.models.ai.TicketAiAnalysisRequest
import backend.models.ai.AIConfigUpdateRequest
import backend.models.ai.AIConnectionTestResponse
import backend.models.ai.AITicketDraftRequest
import backend.models.UserRole
import backend.security.requireAuthenticated
import backend.security.requireRole
import backend.services.ai.AIChatService
import backend.services.ai.AIConfigService
import backend.services.ai.TicketAiAdvisor
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

fun Route.aiRoutes(chatService: AIChatService, configService: AIConfigService, ticketAiAdvisor: TicketAiAdvisor) {
    post("/api/ai/chat") {
        if (!call.requireAuthenticated()) return@post
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
        if (!call.requireAuthenticated()) return@post
        val request = call.receive<AIChatRequest>()
        val message = request.message.trim()
        if (message.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Message is required"))
            return@post
        }
        call.respond(chatService.chat(call.resolveSessionId(), message))
    }

    post("/api/ai/clear") {
        if (!call.requireAuthenticated()) return@post
        chatService.clearConversation(call.resolveSessionId())
        call.respond(HttpStatusCode.OK, mapOf("cleared" to true))
    }

    post("/api/ai/create-ticket-draft") {
        if (!call.requireAuthenticated()) return@post
        val request = call.receive<AITicketDraftRequest>()
        call.respond(chatService.createTicketDraft(request))
    }

    post("/api/ai/ticket-insights") {
        if (!call.requireAuthenticated()) return@post
        val rateKey = buildRateLimitKey(
            call.request.headers["X-User-Id"].orEmpty(),
            call.request.headers["X-Forwarded-For"].orEmpty()
        )
        if (isRateLimited(rateKey)) {
            call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "Rate limit exceeded. Please try again shortly."))
            return@post
        }
        val request = call.receive<TicketAiAnalysisRequest>()
        require(request.description.trim().length >= 10) { "Describe the issue in at least 10 characters." }
        call.respond(ticketAiAdvisor.analyze(request))
    }

    post("/api/ai/tickets/{id}/assist") {
        if (!call.requireRole(UserRole.ADMIN)) return@post
        val rateKey = buildRateLimitKey(
            call.request.headers["X-User-Id"].orEmpty(),
            call.request.headers["X-Forwarded-For"].orEmpty()
        )
        if (isRateLimited(rateKey)) {
            call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "Rate limit exceeded. Please try again shortly."))
            return@post
        }
        val ticketId = call.parameters["id"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ticket id"))
        val assistance = ticketAiAdvisor.assist(ticketId)
            ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "Ticket not found"))
        call.respond(assistance)
    }

    get("/api/ai/config") {
        if (!call.requireRole(UserRole.ADMIN)) return@get
        call.respond(configService.snapshot())
    }

    post("/api/ai/config") {
        if (!call.requireRole(UserRole.ADMIN)) return@post
        val request = call.receive<AIConfigUpdateRequest>()
        call.respond(configService.update(request))
    }

    post("/api/ai/test") {
        if (!call.requireRole(UserRole.ADMIN)) return@post
        val result = chatService.testConnection()
        val response = if (result.ok) {
            AIConnectionTestResponse(success = true, message = "Connected to Gemini successfully")
        } else {
            AIConnectionTestResponse(success = false, message = result.errorMessage ?: "Failed to connect to Gemini")
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
