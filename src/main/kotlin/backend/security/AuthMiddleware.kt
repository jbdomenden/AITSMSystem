package backend.security

import backend.config.Env
import backend.models.ApiErrorResponse
import backend.models.UserRole
import backend.services.SessionPrincipal
import backend.services.SessionService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.AttributeKey
import java.net.URI

private val PrincipalKey = AttributeKey<SessionPrincipal>("aitsm.session.principal")
private val unsafeMethods = setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete)
private val publicAssetExtensions = setOf("css", "js", "png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "woff", "woff2", "ttf", "map")
private val csrfExempt = setOf(
    "/api/auth/login", "/api/auth/register", "/api/auth/verify-email", "/api/auth/resend-verification",
    "/api/auth/forgot-password", "/api/auth/reset-password", "/api/auth/invitations/accept", "/api/monitoring/client-metrics"
)

internal enum class PageAudience { AUTHENTICATED, END_USER, ADMIN }

private val endUserPages = setOf(
    "dashboard-user", "dashboard-user.html", "create-ticket", "create-ticket.html", "tickets", "tickets.html",
    "knowledge-library", "knowledge-library.html", "ai-assistant", "ai-assistant.html"
)
private val adminPages = setOf(
    "dashboard-admin", "dashboard-admin.html", "ticket-management", "ticket-management.html",
    "monitoring", "monitoring.html", "inventory", "inventory.html", "assets", "assets.html",
    "settings", "settings.html", "user-management", "user-management.html", "knowledge", "knowledge.html"
)
private val authenticatedPages = setOf("profile", "profile.html", "security", "security.html", "colleagues", "colleagues.html")
private val cleanPagePaths = (endUserPages + adminPages + authenticatedPages)
    .filterNot { it.endsWith(".html") }
    .mapTo(mutableSetOf()) { "/$it" }
    .plus(setOf("/login", "/signup"))

internal fun protectedPageAudience(path: String): PageAudience? {
    val page = path.substringAfterLast('/')
    return when (page) {
        in endUserPages -> PageAudience.END_USER
        in adminPages -> PageAudience.ADMIN
        in authenticatedPages -> PageAudience.AUTHENTICATED
        else -> null
    }
}

internal fun isAdminPortalRole(role: UserRole): Boolean =
    role in setOf(UserRole.ADMIN, UserRole.IT_ADMIN, UserRole.SUPERADMIN)

internal fun portalHome(role: UserRole): String =
    if (isAdminPortalRole(role)) "/dashboard-admin" else "/dashboard-user"

fun Application.configureSecurityBoundary(sessions: SessionService) {
    val production = Env.get("APP_ENV").equals("production", true)
    val allowedOrigins = (Env.get("CORS_ALLOWED_ORIGINS") ?: Env.get("APP_BASE_URL") ?: "")
        .split(',').map { it.trim().removeSuffix("/") }.filter { it.isNotBlank() }.toSet()
    if (production && allowedOrigins.isEmpty()) error("CORS_ALLOWED_ORIGINS or APP_BASE_URL is required in production")
    val authLimiter = RateLimiter(10, 5 * 60_000L)
    intercept(ApplicationCallPipeline.Plugins) {
        val path = call.request.path()
        when {
            path.startsWith("/api/") || path.startsWith("/settings/") || path.endsWith(".html") || path in cleanPagePaths -> {
                call.response.headers.append("Cache-Control", "no-store, max-age=0")
                call.response.headers.append("Pragma", "no-cache")
            }
            path.endsWith(".css") || path.endsWith(".js") -> {
                // Frontend assets are intentionally not fingerprinted yet. Revalidate them so a
                // repaired deployment cannot remain hidden behind a stale browser cache entry.
                call.response.headers.append("Cache-Control", "no-cache, max-age=0, must-revalidate")
            }
        }
        if (hasLegacyIdentityHeaders(call.request.headers)) {
            call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(400, "Legacy identity headers are not accepted"))
            finish(); return@intercept
        }
        if (call.request.httpMethod == HttpMethod.Post && path in csrfExempt &&
            !authLimiter.allow("${call.request.local.remoteHost}:$path")) {
            call.respond(HttpStatusCode.TooManyRequests, ApiErrorResponse(429, "Too many requests")); finish(); return@intercept
        }
        if (!isPublicStaticAsset(path, call.request.httpMethod)) {
            sessions.authenticate(call.request.cookies[SessionService.COOKIE_NAME])
                ?.let { call.attributes.put(PrincipalKey, it) }
        }
        val principal = call.principal()
        if (principal != null && !principal.mfaComplete && !isMfaRecoveryPath(path)) {
            call.response.headers.append(HttpHeaders.CacheControl, "no-store, max-age=0")
            call.response.headers.append(HttpHeaders.Pragma, "no-cache")
            call.respond(HttpStatusCode.Forbidden, ApiErrorResponse(403, "MFA completion is required")); finish(); return@intercept
        }
        if (principal?.mfaComplete == true && isAuthenticationPage(path)) {
            call.respondRedirect(portalHome(principal.user.role))
            finish(); return@intercept
        }
        protectedPageAudience(path)?.let { audience ->
            if (principal == null) {
                call.respondRedirect("/login")
                finish(); return@intercept
            }
            val adminPortal = isAdminPortalRole(principal.user.role)
            val wrongPortal = (audience == PageAudience.ADMIN && !adminPortal) ||
                (audience == PageAudience.END_USER && adminPortal)
            if (wrongPortal) {
                call.respondRedirect(portalHome(principal.user.role))
                finish(); return@intercept
            }
        }
        if (call.request.httpMethod in unsafeMethods && path !in csrfExempt) {
            val origin = call.request.headers["Origin"] ?: call.request.headers["Referer"]?.let {
                runCatching { URI(it).let { uri -> "${uri.scheme}://${uri.authority}" } }.getOrNull()
            }
            if (origin != null && origin.removeSuffix("/") !in allowedOrigins && (production || allowedOrigins.isNotEmpty())) {
                call.respond(HttpStatusCode.Forbidden, ApiErrorResponse(403, "Request origin is not allowed")); finish(); return@intercept
            }
            call.principal()?.let { principal ->
                val header = call.request.headers["X-CSRF-Token"]
                val cookie = call.request.cookies[SessionService.CSRF_COOKIE_NAME]
                if (header == null || header != cookie || !sessions.validateCsrf(principal.sessionId, header)) {
                    call.respond(HttpStatusCode.Forbidden, ApiErrorResponse(403, "CSRF validation failed")); finish(); return@intercept
                }
            }
        }
    }
}

internal fun isPublicStaticAsset(path: String, method: HttpMethod): Boolean =
    method == HttpMethod.Get &&
        !path.startsWith("/api/") &&
        !path.startsWith("/uploads/") &&
        path.substringAfterLast('.', missingDelimiterValue = "").lowercase() in publicAssetExtensions

internal fun hasLegacyIdentityHeaders(headers: Headers): Boolean =
    headers["X-User-Id"] != null || headers["X-User-Role"] != null

internal fun isMfaRecoveryPath(path: String): Boolean =
    path in setOf("/", "/index.html", "/login", "/login.html", "/api/auth/login", "/api/auth/me", "/api/auth/logout") ||
        path.startsWith("/index/") || path.startsWith("/login/") || path.startsWith("/shared/") ||
        path.startsWith("/modules/") || path.startsWith("/api/auth/mfa/")

internal fun isAuthenticationPage(path: String): Boolean =
    path in setOf("/", "/index.html", "/index/html/index.html", "/login", "/login.html", "/login/html/login.html")

fun ApplicationCall.principal(): SessionPrincipal? = attributes.getOrNull(PrincipalKey)
fun ApplicationCall.userId(): Int? = principal()?.user?.id
fun ApplicationCall.userRole(): UserRole = principal()?.user?.role ?: UserRole.END_USER

suspend fun ApplicationCall.requireAuthenticated(): Boolean {
    if (principal() == null) {
        respond(HttpStatusCode.Unauthorized, ApiErrorResponse(HttpStatusCode.Unauthorized.value, "Authentication required"))
        return false
    }
    return true
}

suspend fun ApplicationCall.requirePermission(permission: Permission): Boolean {
    val p = principal()
    if (p == null) {
        respond(HttpStatusCode.Unauthorized, ApiErrorResponse(401, "Authentication required")); return false
    }
    if (permission !in PermissionMatrix.permissions(p.user.role)) {
        respond(HttpStatusCode.Forbidden, ApiErrorResponse(403, "Permission denied")); return false
    }
    return true
}

suspend fun ApplicationCall.requireRole(role: UserRole): Boolean {
    if (principal() == null) {
        respond(HttpStatusCode.Unauthorized, ApiErrorResponse(HttpStatusCode.Unauthorized.value, "Authentication required"))
        return false
    }
    val callerRole = userRole()
    val allowed = when (role) {
        UserRole.ADMIN -> legacyAdminPermission(request.path(), request.httpMethod) in PermissionMatrix.permissions(callerRole)
        else -> callerRole == role
    }
    if (!allowed) {
        respond(HttpStatusCode.Forbidden, ApiErrorResponse(HttpStatusCode.Forbidden.value, "Access denied for ${role.name} resources"))
        return false
    }
    return true
}

private fun legacyAdminPermission(path: String, method: HttpMethod): Permission = when {
    path.startsWith("/api/tickets") -> if (method == HttpMethod.Get) Permission.TICKET_READ_ALL else Permission.TICKET_MANAGE
    path.startsWith("/api/inventory") || path.startsWith("/api/devices") || path.startsWith("/api/monitoring") ->
        if (method == HttpMethod.Get) Permission.ASSET_READ else Permission.ASSET_MANAGE
    path.startsWith("/api/knowledge") -> Permission.KNOWLEDGE_MANAGE
    path.startsWith("/api/analytics") -> Permission.TICKET_READ_ALL
    path.startsWith("/api/profile-photo") || path.startsWith("/api/users") -> Permission.USER_MANAGE
    path.startsWith("/api/ai/config") || path.startsWith("/api/ai/test") -> Permission.AI_CONFIGURE
    else -> Permission.SECURITY_MANAGE
}
