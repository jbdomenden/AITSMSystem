package backend.routes

import backend.models.TroubleshootingResponse
import backend.services.AIService
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.aiRoutes(service: AIService) {
    post("/api/ai/troubleshoot") {
        val body = call.receive<Map<String, String>>()
        val description = body["description"].orEmpty()
        call.respond(TroubleshootingResponse(service.troubleshoot(description)))
    }
}
