package backend.security

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

fun ApplicationCall.userId(): Int? = request.headers["X-User-Id"]?.toIntOrNull()
fun ApplicationCall.userRole(): String = request.headers["X-User-Role"] ?: "end-user"

suspend fun ApplicationCall.requireRole(role: String): Boolean {
    val callerRole = userRole()
    val allowed = when (role) {
        "admin" -> callerRole == "admin" || callerRole == "superadmin"
        else -> callerRole == role
    }
    if (!allowed) {
        respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied for $role resources"))
        return false
    }
    return true
}
