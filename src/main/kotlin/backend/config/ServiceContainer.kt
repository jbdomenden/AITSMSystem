package backend.config

import backend.repository.AIConversationRepository
import backend.repository.AuditRepository
import backend.repository.DeviceRepository
import backend.repository.KnowledgeRepository
import backend.repository.TicketRepository
import backend.repository.UserRepository
import backend.queries.SystemSettingsQueries
import backend.queries.InventoryQueries
import backend.services.AIService
import backend.services.AssetDetectionService
import backend.services.AuthService
import backend.services.InventoryService
import backend.services.KnowledgeService
import backend.services.MonitoringService
import backend.services.NotificationService
import backend.services.SLAService
import backend.services.TicketService
import backend.services.ai.AIChatService
import backend.services.ai.AIConfigService
import backend.services.ai.OllamaProvider
import io.ktor.server.application.Application

class ServiceContainer(application: Application) {
    val auditRepo = AuditRepository()
    val userRepo = UserRepository()
    val ticketRepo = TicketRepository()
    val systemSettingsQueries = SystemSettingsQueries()
    val inventoryQueries = InventoryQueries()
    val assetDetectionService = AssetDetectionService(systemSettingsQueries)
    val deviceRepo = DeviceRepository(assetDetectionService)
    val knowledgeRepo = KnowledgeRepository()
    val aiConversationRepository = AIConversationRepository()

    val authService = AuthService(userRepo, auditRepo)
    val ticketService = TicketService(ticketRepo, auditRepo)
    val monitoringService = MonitoringService(deviceRepo, assetDetectionService)
    val aiService = AIService()
    val aiConfigService = AIConfigService(application.environment.config)
    val aiChatService = AIChatService(OllamaProvider(), aiConfigService, aiConversationRepository)
    val slaService = SLAService()
    val notificationService = NotificationService()
    val knowledgeService = KnowledgeService(knowledgeRepo, auditRepo)
    val inventoryService = InventoryService(inventoryQueries, assetDetectionService)
}
