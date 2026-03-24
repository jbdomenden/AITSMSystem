package backend.security

import backend.models.ApiErrorResponse
import backend.models.UserRole
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

fun ApplicationCall.userId(): Int? = request.headers["X-User-Id"]?.toIntOrNull()
fun ApplicationCall.userRole(): UserRole = UserRole.from(request.headers["X-User-Role"])

suspend fun ApplicationCall.requireRole(role: UserRole): Boolean {
    val callerRole = userRole()
    val allowed = when (role) {
        UserRole.ADMIN -> callerRole == UserRole.ADMIN || callerRole == UserRole.SUPERADMIN
        else -> callerRole == role
    }
    if (!allowed) {
        respond(HttpStatusCode.Forbidden, ApiErrorResponse(HttpStatusCode.Forbidden.value, "Access denied for ${role.name} resources"))
        return false
    }
    return true
}
