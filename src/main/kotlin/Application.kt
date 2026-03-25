package com.aitsm

import backend.config.DatabaseFactory
import backend.config.Env
import backend.config.ServiceContainer
import backend.models.ApiErrorResponse
import backend.routes.aiRoutes
import backend.routes.analyticsRoutes
import backend.routes.authRoutes
import backend.routes.deviceRoutes
import backend.routes.knowledgeRoutes
import backend.routes.monitoringRoutes
import backend.routes.notificationRoutes
import backend.routes.slaRoutes
import backend.routes.ticketRoutes
import backend.security.PasswordHasher
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

fun main() {
    embeddedServer(Netty, port = Env.get("PORT")?.toIntOrNull() ?: 8070, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    val appEnv = (Env.get("APP_ENV") ?: "development").lowercase()
    val isProduction = appEnv == "production"
    val metrics = ConcurrentHashMap<String, AtomicLong>()

    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Referrer-Policy", "no-referrer")
    }
    install(CallLogging)
    install(ContentNegotiation) { json() }
    intercept(ApplicationCallPipeline.Monitoring) {
        val started = System.nanoTime()
        proceed()
        val durationMs = (System.nanoTime() - started) / 1_000_000
        val key = "${call.request.httpMethod.value} ${call.request.path()}"
        metrics.computeIfAbsent("requests.total", { AtomicLong(0) }).incrementAndGet()
        metrics.computeIfAbsent("requests.$key.count", { AtomicLong(0) }).incrementAndGet()
        metrics.computeIfAbsent("requests.$key.durationMs", { AtomicLong(0) }).addAndGet(durationMs)
    }

    install(CORS) {
        allowHeader("Content-Type")
        allowHeader("X-User-Id")
        allowHeader("X-User-Role")
        allowHeader("X-AI-Session-Id")

        val allowedOrigins = (Env.get("CORS_ALLOWED_ORIGINS") ?: "")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (allowedOrigins.isEmpty()) {
            if (!isProduction) anyHost()
        } else {
            allowedOrigins.forEach { origin ->
                val uri = runCatching { URI(origin) }.getOrNull()
                val host = uri?.host ?: origin.removePrefix("https://").removePrefix("http://")
                val scheme = uri?.scheme ?: if (origin.startsWith("https://")) "https" else "http"
                allowHost(host, schemes = listOf(scheme))
            }
        }
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(HttpStatusCode.BadRequest.value, cause.message ?: "Invalid request"))
        }
        exception<IllegalStateException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(HttpStatusCode.BadRequest.value, cause.message ?: "Invalid state"))
        }
        exception<SecurityException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, ApiErrorResponse(HttpStatusCode.Forbidden.value, cause.message ?: "Access denied"))
        }
        exception<Throwable> { call, _ ->
            metrics.computeIfAbsent("requests.errors", { AtomicLong(0) }).incrementAndGet()
            call.respond(HttpStatusCode.InternalServerError, ApiErrorResponse(HttpStatusCode.InternalServerError.value, if (isProduction) "Internal server error" else "Unexpected error"))
        }
    }

    DatabaseFactory.init()
    val container = ServiceContainer(this)
    container.knowledgeRepo.seedDefaults()
    container.aiChatService.startupLog()
    container.slaService.seedDefaults()

    val superAdminEmail = Env.get("SUPERADMIN_EMAIL")?.takeIf { it.isNotBlank() }
    val superAdminPassword = Env.get("SUPERADMIN_PASSWORD")?.takeIf { it.isNotBlank() }

    if (isProduction && (superAdminEmail == null || superAdminPassword == null)) {
        error("SUPERADMIN_EMAIL and SUPERADMIN_PASSWORD are required in production")
    }
    if (superAdminEmail != null && superAdminPassword != null) {
        container.userRepo.ensureSuperAdmin(
            email = superAdminEmail,
            passwordHash = PasswordHasher.hash(superAdminPassword),
            fullName = Env.get("SUPERADMIN_NAME") ?: "System Super Admin",
            company = Env.get("SUPERADMIN_COMPANY") ?: "AITSM",
            department = Env.get("SUPERADMIN_DEPARTMENT") ?: "Platform"
        )
    }

    routing {
        get("/api/health") {
            call.respond(mapOf("status" to "ok", "environment" to appEnv))
        }
        get("/metrics") {
            call.respond(metrics.mapValues { it.value.get() })
        }
        get("/docs") {
            call.respond(mapOf("message" to "OpenAPI publishing is tracked for a follow-up release."))
        }
        staticResources("/", "static/frontend")
        authRoutes(container.authService)
        ticketRoutes(container.ticketService)
        monitoringRoutes(container.monitoringService, container.deviceRepo)
        analyticsRoutes(container.ticketService, container.monitoringService, container.aiService)
        deviceRoutes(container.deviceRepo, container.userRepo, container.monitoringService)
        notificationRoutes(container.notificationService)
        knowledgeRoutes(container.knowledgeService)
        slaRoutes(container.slaService)
        aiRoutes(container.aiChatService, container.aiConfigService)
    }
}
