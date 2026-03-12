package backend.routes

import backend.models.LoginRequest
import backend.models.RegisterRequest
import backend.models.ResendVerificationRequest
import backend.models.RoleUpdateRequest
import backend.models.VerifyEmailRequest
import backend.security.requireRole
import backend.security.userId
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
            call.respond(HttpStatusCode.Created, authService.register(req))
        }
        post("/verify-email") {
            val req = call.receive<VerifyEmailRequest>()
            call.respond(authService.verifyEmail(req.email, req.code))
        }
        post("/resend-verification") {
            val req = call.receive<ResendVerificationRequest>()
            call.respond(authService.resendVerification(req.email))
        }
        post("/login") {
            val req = call.receive<LoginRequest>()
            call.respond(authService.login(req))
        }
        post("/logout") {
            call.respond(HttpStatusCode.OK, mapOf("message" to "Logged out"))
        }
    }

    route("/api/users") {
        get {
            if (!call.requireRole("admin")) return@get
            call.respond(authService.listUsers())
        }
        put("/{id}/role") {
            if (!call.requireRole("admin")) return@put
            val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
            val req = call.receive<RoleUpdateRequest>()
            call.respond(authService.updateUserRole(id, req.role, call.userId()))
        }
    }
}
