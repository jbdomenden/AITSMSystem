package backend.models

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Int,
    val fullName: String,
    val email: String,
    val company: String,
    val department: String,
    val role: String,
    val emailVerified: Boolean = false,
    val createdAt: String
)

@Serializable
data class RegisterRequest(
    val fullName: String,
    val email: String,
    val company: String,
    val department: String,
    val password: String,
    val confirmPassword: String,
    val eulaAccepted: Boolean,
    val eulaVersion: String = "1.0"
)

@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class AuthResponse(val token: String, val user: User)
@Serializable data class RegistrationResponse(val message: String, val email: String, val devVerificationCode: String? = null)
@Serializable data class VerifyEmailRequest(val email: String, val code: String)
@Serializable data class ResendVerificationRequest(val email: String)
@Serializable data class RoleUpdateRequest(val role: String)

@Serializable data class ProfileUpdateRequest(val fullName: String, val company: String, val department: String)
@Serializable data class PasswordResetRequest(val newPassword: String, val confirmPassword: String)
@Serializable data class ChangeOwnPasswordRequest(val currentPassword: String, val newPassword: String, val confirmPassword: String)
@Serializable data class EmailApprovalRequest(val approved: Boolean)
@Serializable data class EmailVerificationUpdateRequest(val approved: Boolean)
@Serializable data class InternalUserCreateRequest(
    val fullName: String,
    val email: String,
    val company: String,
    val department: String,
    val password: String,
    val confirmPassword: String,
    val role: String = "end-user",
    val emailVerified: Boolean = true
)
@Serializable data class AdminEligibilityRequest(val targetEmail: String)
@Serializable data class AdminEligibilityResponse(val found: Boolean, val eligible: Boolean, val alreadyAdmin: Boolean, val targetUserId: Int? = null, val message: String)
@Serializable data class AdminSensitiveVerifyRequest(val password: String)
@Serializable data class AdminSensitiveVerifyResponse(val verified: Boolean, val verificationToken: String? = null, val expiresAt: String? = null, val message: String)
@Serializable data class AdminGrantRequest(val targetEmail: String, val verificationToken: String)
@Serializable data class AdminGrantResponse(val success: Boolean, val user: User? = null, val message: String)
@Serializable data class UserActionResponse(val message: String, val user: User)


@Serializable
data class Ticket(
    val id: Int,
    val userId: Int,
    val title: String,
    val description: String,
    val priority: String,
    val category: String,
    val status: String,
    val assignedTo: String? = null,
    val deviceId: Int? = null,
    val createdAt: String,
    val updatedAt: String,
    val slaRemainingMinutes: Long? = null,
    val overdue: Boolean = false
)

@Serializable data class TicketRequest(val title: String, val description: String, val priority: String, val category: String, val deviceId: Int? = null)
@Serializable data class TicketStatusUpdate(val status: String)

@Serializable
data class Device(
    val id: Int,
    val deviceName: String,
    val ipAddress: String,
    val department: String,
    val assignedUser: String,
    val cpuUsage: Int,
    val memoryUsage: Int,
    val status: String,
    val lastSeen: String
)

@Serializable data class DeviceRequest(val deviceName: String, val ipAddress: String, val department: String, val assignedUser: String, val status: String)
@Serializable data class ClientMetricsRequest(val deviceName: String, val ipAddress: String, val department: String, val assignedUser: String, val cpuUsage: Int, val memoryUsage: Int, val status: String)

@Serializable
 data class HostTelemetryDto(
    val cpuUsagePercent: Double,
    val memoryUsagePercent: Double,
    val totalMemoryBytes: Long,
    val usedMemoryBytes: Long,
    val hostname: String,
    val ipAddress: String,
    val timestamp: String
)

@Serializable
 data class LanDeviceDto(
    val id: String,
    val hostname: String,
    val ipAddress: String,
    val reachable: Boolean,
    val telemetryAvailable: Boolean,
    val telemetrySourceType: String,
    val cpuUsagePercent: Double? = null,
    val memoryUsagePercent: Double? = null,
    val lastSeen: String
)

@Serializable
 data class MonitoringSummaryDto(
    val totalDiscovered: Int,
    val monitoredDevices: Int,
    val telemetryAvailableDevices: Int,
    val hostTelemetry: HostTelemetryDto,
    val timestamp: String
)

@Serializable data class Notification(val id: Int, val userId: Int, val message: String, val type: String, val createdAt: String)
@Serializable data class SLA(val id: Int, val priority: String, val responseTime: Int, val resolutionTime: Int)
@Serializable data class KnowledgeArticle(val id: Int, val title: String, val content: String, val category: String, val createdAt: String)
@Serializable data class KnowledgeRequest(val title: String, val content: String, val category: String)
@Serializable data class AuditLog(val id: Int, val userId: Int?, val action: String, val entity: String, val timestamp: String)

@Serializable data class TroubleshootingResponse(val suggestions: List<String>)
@Serializable data class AnalyticsResponse(val metric: String, val points: List<Map<String, String>>)

@Serializable data class AIChatMessage(val role: String, val content: String)
@Serializable data class AIChatRequest(val message: String)

@Serializable
data class AIStructuredResponse(
    val issueSummary: String,
    val likelyCauses: List<String>,
    val troubleshootingSteps: List<String>,
    val escalationCriteria: List<String>,
    val suggestedPriority: String,
    val ticketDescription: String,
    val suggestedCategory: String
)

@Serializable
data class AIChatResponse(
    val reply: String,
    val structured: AIStructuredResponse,
    val priority: String,
    val category: String,
    val titleSuggestion: String,
    val descriptionSuggestion: String,
    val conversationSize: Int,
    val provider: String = "openai",
    val model: String
)

@Serializable
data class AITicketDraftRequest(
    val issueSummary: String,
    val ticketDescription: String,
    val suggestedPriority: String,
    val suggestedCategory: String,
    val originalUserMessage: String? = null
)

@Serializable
data class AITicketDraftResponse(
    val title: String,
    val description: String,
    val priority: String,
    val category: String
)
