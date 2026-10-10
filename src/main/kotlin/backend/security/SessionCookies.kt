package backend.security

import backend.services.SessionService
import io.ktor.http.Cookie

object SessionCookies {
    fun auth(value: String, production: Boolean, maxAge: Int? = null) = Cookie(
        SessionService.COOKIE_NAME, value, maxAge = maxAge, httpOnly = true, secure = production,
        path = "/", extensions = mapOf("SameSite" to "Lax")
    )
    fun csrf(value: String, production: Boolean, maxAge: Int? = null) = Cookie(
        SessionService.CSRF_COOKIE_NAME, value, maxAge = maxAge, httpOnly = false, secure = production,
        path = "/", extensions = mapOf("SameSite" to "Lax")
    )
}
