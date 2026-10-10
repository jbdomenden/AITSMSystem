package backend.security

import backend.models.ApiErrorResponse
import backend.services.ReauthScope
import backend.services.ReauthenticationService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

suspend fun ApplicationCall.requireReauthentication(service: ReauthenticationService, scope: ReauthScope, singleUse: Boolean = true): Boolean {
    val authenticated = principal() ?: run {
        respond(HttpStatusCode.Unauthorized, ApiErrorResponse(401, "Authentication required")); return false
    }
    if (!service.consume(authenticated.sessionId, authenticated.user.id, scope, request.headers["X-Reauth-Token"], singleUse)) {
        respond(HttpStatusCode.Forbidden, ApiErrorResponse(403, "Recent reauthentication is required for ${scope.wireValue}")); return false
    }
    return true
}
