package backend.routes

import backend.models.AdminEligibilityRequest
import backend.models.AdminGrantRequest
import backend.models.AdminSensitiveVerifyRequest
import backend.models.EmailApprovalRequest
import backend.models.LoginRequest
import backend.models.InternalUserCreateRequest
import backend.models.PasswordResetRequest
import backend.models.ProfileUpdateRequest
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
        get("/me") {
            val actor = call.userId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            call.respond(authService.currentUser(actor))
        }
        put("/me") {
            val actor = call.userId() ?: return@put call.respond(HttpStatusCode.Unauthorized)
            val req = call.receive<ProfileUpdateRequest>()
            call.respond(authService.updateOwnProfile(actor, req))
        }

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
        put("/{id}/reset-password") {
            if (!call.requireRole("admin")) return@put
            val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
            val req = call.receive<PasswordResetRequest>()
            val updated = authService.resetUserPassword(id, req.newPassword, req.confirmPassword, call.userId())
            call.respond(mapOf("message" to "Password reset successful", "user" to updated))
        }
        put("/{id}/email-approval") {
            if (!call.requireRole("admin")) return@put
            val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
            val req = call.receive<EmailApprovalRequest>()
            val updated = authService.updateUserEmailApproval(id, req.approved, call.userId())
            call.respond(mapOf("message" to if (req.approved) "User approved for login" else "User marked as pending", "user" to updated))
        }
        delete("/{id}") {
            if (!call.requireRole("admin")) return@delete
            val id = call.parameters["id"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val deleted = authService.deleteUserAccount(id, call.userId())
            call.respond(mapOf("message" to "User deleted", "user" to deleted))
        }
        post {
            if (!call.requireRole("admin")) return@post
            val req = call.receive<InternalUserCreateRequest>()
            val created = authService.createUserInternally(req, call.userId())
            call.respond(HttpStatusCode.Created, mapOf("message" to "User account created", "user" to created))
        }

        route("/admin") {
            post("/eligibility") {
                if (!call.requireRole("admin")) return@post
                val req = call.receive<AdminEligibilityRequest>()
                call.respond(authService.adminGrantEligibility(req.targetEmail))
            }
            post("/verify") {
                if (!call.requireRole("admin")) return@post
                val actor = call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val req = call.receive<AdminSensitiveVerifyRequest>()
                val (ok, token, meta) = authService.verifySensitiveAction(actor, req.password)
                if (!ok) return@post call.respond(HttpStatusCode.Forbidden, mapOf("verified" to false, "message" to meta))
                call.respond(mapOf("verified" to true, "verificationToken" to token, "expiresAt" to meta, "message" to "Verification successful"))
            }
            post("/grant") {
                if (!call.requireRole("admin")) return@post
                val actor = call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val req = call.receive<AdminGrantRequest>()
                val result = authService.grantAdminByEmail(req.targetEmail, actor, req.verificationToken)
                if (!result.success) return@post call.respond(HttpStatusCode.BadRequest, result)
                call.respond(result)
            }
        }
    }
}
