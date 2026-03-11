package backend.routes

import backend.models.LoginRequest
import backend.models.RegisterRequest
import backend.services.AuthService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(authService: AuthService) {
    route("/api/auth") {
        post("/register") {
            val req = call.receive<RegisterRequest>()
            call.respond(authService.register(req))
        }
        post("/login") {
            val req = call.receive<LoginRequest>()
            call.respond(authService.login(req))
        }
        post("/logout") {
            call.respond(HttpStatusCode.OK, mapOf("message" to "Logged out"))
        }
    }
}
