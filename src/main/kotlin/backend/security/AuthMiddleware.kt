package backend.security

import io.ktor.server.application.*

fun ApplicationCall.userId(): Int? = request.headers["X-User-Id"]?.toIntOrNull()
fun ApplicationCall.userRole(): String = request.headers["X-User-Role"] ?: "end-user"
