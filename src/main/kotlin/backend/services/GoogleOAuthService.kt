package backend.services

import backend.config.Env
import backend.models.OAuthLoginResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GoogleOAuthService(private val authService: AuthService) {
    private data class PendingState(val expiresAt: LocalDateTime)
    private data class LoginTicket(val response: OAuthLoginResult, val expiresAt: LocalDateTime)

    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val states = ConcurrentHashMap<String, PendingState>()
    private val tickets = ConcurrentHashMap<String, LoginTicket>()
    private val clientId get() = Env.get("GOOGLE_CLIENT_ID")
    private val clientSecret get() = Env.get("GOOGLE_CLIENT_SECRET")
    private val appBaseUrl get() = (Env.get("APP_BASE_URL") ?: "http://127.0.0.1:8070").removeSuffix("/")
    val callbackUrl get() = "$appBaseUrl/api/auth/oauth/callback/google"
    val usesSecureCookies get() = appBaseUrl.startsWith("https://")

    fun start(): Pair<String, String> {
        requireConfigured()
        purgeExpired()
        val state = UUID.randomUUID().toString()
        states[state] = PendingState(LocalDateTime.now().plusMinutes(10))
        val query = mapOf(
            "client_id" to clientId!!,
            "redirect_uri" to callbackUrl,
            "response_type" to "code",
            "scope" to "openid email profile",
            "state" to state,
            "prompt" to "select_account"
        ).entries.joinToString("&") { "${it.key}=${it.value.formEncode()}" }
        return "https://accounts.google.com/o/oauth2/v2/auth?$query" to state
    }

    fun complete(code: String, state: String): String {
        requireConfigured()
        val pending = states.remove(state) ?: throw SecurityException("Google sign-in session is invalid or expired. Please try again.")
        require(pending.expiresAt.isAfter(LocalDateTime.now())) { "Google sign-in session expired. Please try again." }
        val form = mapOf(
            "code" to code,
            "client_id" to clientId!!,
            "client_secret" to clientSecret!!,
            "redirect_uri" to callbackUrl,
            "grant_type" to "authorization_code"
        ).entries.joinToString("&") { "${it.key}=${it.value.formEncode()}" }
        val tokenRequest = HttpRequest.newBuilder(URI("https://oauth2.googleapis.com/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form)).build()
        val tokenResponse = client.send(tokenRequest, HttpResponse.BodyHandlers.ofString())
        require(tokenResponse.statusCode() == 200) { "Google sign-in could not be completed." }
        val accessToken = json.parseToJsonElement(tokenResponse.body()).jsonObject["access_token"]?.jsonPrimitive?.content
            ?: error("Google did not return an access token.")
        val profileRequest = HttpRequest.newBuilder(URI("https://openidconnect.googleapis.com/v1/userinfo"))
            .header("Authorization", "Bearer $accessToken").GET().build()
        val profileResponse = client.send(profileRequest, HttpResponse.BodyHandlers.ofString())
        require(profileResponse.statusCode() == 200) { "Google account information could not be verified." }
        val profile = json.parseToJsonElement(profileResponse.body()).jsonObject
        val email = profile["email"]?.jsonPrimitive?.content ?: error("Google did not provide an email address.")
        val fullName = profile["name"]?.jsonPrimitive?.content ?: email.substringBefore('@')
        val verified = profile["email_verified"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        require(verified) { "Your Google email address must be verified before it can be used to sign in." }
        val response = authService.completeExternalLogin(email, fullName, "Google")
        val ticket = UUID.randomUUID().toString()
        tickets[ticket] = LoginTicket(response, LocalDateTime.now().plusMinutes(2))
        return ticket
    }

    fun consumeTicket(ticket: String): OAuthLoginResult {
        val login = tickets.remove(ticket) ?: throw SecurityException("Google sign-in result is invalid or expired. Please try again.")
        require(login.expiresAt.isAfter(LocalDateTime.now())) { "Google sign-in result expired. Please try again." }
        return login.response
    }

    private fun requireConfigured() {
        require(!clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()) { "Google sign-in is not configured yet." }
    }

    private fun purgeExpired() {
        val now = LocalDateTime.now()
        states.entries.removeIf { it.value.expiresAt.isBefore(now) }
        tickets.entries.removeIf { it.value.expiresAt.isBefore(now) }
    }

    private fun String.formEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8)
}
