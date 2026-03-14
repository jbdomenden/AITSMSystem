package com.aitsm

import backend.config.DatabaseFactory
import backend.repository.AuditRepository
import backend.repository.DeviceRepository
import backend.repository.KnowledgeRepository
import backend.repository.TicketRepository
import backend.repository.UserRepository
import backend.routes.aiRoutes
import backend.routes.analyticsRoutes
import backend.routes.authRoutes
import backend.routes.deviceRoutes
import backend.routes.knowledgeRoutes
import backend.routes.monitoringRoutes
import backend.routes.notificationRoutes
import backend.routes.slaRoutes
import backend.routes.ticketRoutes
import backend.services.AIService
import backend.services.AuthService
import backend.services.KnowledgeService
import backend.services.MonitoringService
import backend.services.NotificationService
import backend.services.SLAService
import backend.services.TicketService
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
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.net.URI

fun main() {
    embeddedServer(Netty, port = System.getenv("PORT")?.toIntOrNull() ?: 8070, module = Application::module).start(wait = true)
}

fun Application.module() {
    val appEnv = (System.getenv("APP_ENV") ?: "development").lowercase()
    val isProduction = appEnv == "production"

    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Referrer-Policy", "no-referrer")
    }
    install(CallLogging)
    install(ContentNegotiation) { json() }
    install(CORS) {
        allowHeader("Content-Type")
        allowHeader("X-User-Id")
        allowHeader("X-User-Role")

        val allowedOrigins = (System.getenv("CORS_ALLOWED_ORIGINS") ?: "")
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
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "Invalid request")))
        }
        exception<IllegalStateException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "Invalid state")))
        }
        exception<SecurityException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to (cause.message ?: "Access denied")))
        }
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to if (isProduction) "Internal server error" else (cause.message ?: "Unexpected error"))
            )
        }
    }

    DatabaseFactory.init()

    val auditRepo = AuditRepository()
    val userRepo = UserRepository()
    val ticketRepo = TicketRepository()
    val deviceRepo = DeviceRepository()
    val knowledgeRepo = KnowledgeRepository()

    val authService = AuthService(userRepo, auditRepo)
    val ticketService = TicketService(ticketRepo, auditRepo)
    val monitoringService = MonitoringService(deviceRepo)
    val aiService = AIService()
    val slaService = SLAService().also { it.seedDefaults() }
    val notificationService = NotificationService()
    val knowledgeService = KnowledgeService(knowledgeRepo, auditRepo)

    val superAdminEmail = System.getenv("SUPERADMIN_EMAIL") ?: "superadmin@aitsm.local"
    val superAdminPassword = System.getenv("SUPERADMIN_PASSWORD") ?: "SuperAdmin@123"
    if (isProduction && superAdminPassword == "SuperAdmin@123") {
        error("SUPERADMIN_PASSWORD must be set in production")
    }
    userRepo.ensureSuperAdmin(
        email = superAdminEmail,
        passwordHash = PasswordHasher.hash(superAdminPassword),
        fullName = System.getenv("SUPERADMIN_NAME") ?: "System Super Admin",
        company = System.getenv("SUPERADMIN_COMPANY") ?: "AITSM",
        department = System.getenv("SUPERADMIN_DEPARTMENT") ?: "Platform"
    )

    routing {
        get("/api/health") {
            call.respond(mapOf("status" to "ok", "environment" to appEnv))
        }
        staticResources("/", "static/frontend")
        authRoutes(authService)
        ticketRoutes(ticketService)
        monitoringRoutes(monitoringService, deviceRepo)
        analyticsRoutes(ticketService, monitoringService, aiService)
        deviceRoutes(deviceRepo)
        notificationRoutes(notificationService)
        knowledgeRoutes(knowledgeService)
        slaRoutes(slaService)
        aiRoutes(aiService)
    }
}
