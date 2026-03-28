package backend.models

import kotlinx.serialization.Serializable

@Serializable
data class InventoryAssetListItem(
    val id: Int,
    val deviceName: String,
    val hostname: String? = null,
    val ipAddress: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val processorName: String? = null,
    val installedRam: String? = null,
    val storageSummary: String? = null,
    val osSummary: String? = null,
    val status: String,
    val lastSeenAt: String? = null,
    val assignedDepartment: String? = null,
    val connectionSource: String? = null
)

@Serializable
data class InventoryAssetDetails(
    val id: Int,
    val assetTag: String? = null,
    val deviceName: String,
    val hostname: String? = null,
    val fullDeviceName: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val processorName: String? = null,
    val processorSpeed: String? = null,
    val installedRam: String? = null,
    val usableRam: String? = null,
    val ramSpeed: String? = null,
    val gpuName: String? = null,
    val gpuMemory: String? = null,
    val storageTotal: String? = null,
    val storageUsed: String? = null,
    val storageFree: String? = null,
    val storageBreakdownJson: String? = null,
    val systemType: String? = null,
    val osName: String? = null,
    val osEdition: String? = null,
    val osVersion: String? = null,
    val osBuild: String? = null,
    val installedOn: String? = null,
    val domainOrWorkgroup: String? = null,
    val deviceUuid: String? = null,
    val productId: String? = null,
    val penTouchSupport: String? = null,
    val ipAddress: String? = null,
    val macAddress: String? = null,
    val connectionSource: String? = null,
    val status: String,
    val assignedDepartment: String? = null,
    val assignedUser: String? = null,
    val notes: String? = null,
    val lastSeenAt: String? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class InventoryUpsertDetectedRequest(
    val assetTag: String? = null,
    val deviceName: String? = null,
    val hostname: String? = null,
    val fullDeviceName: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val processorName: String? = null,
    val processorSpeed: String? = null,
    val installedRam: String? = null,
    val usableRam: String? = null,
    val ramSpeed: String? = null,
    val gpuName: String? = null,
    val gpuMemory: String? = null,
    val storageTotal: String? = null,
    val storageUsed: String? = null,
    val storageFree: String? = null,
    val storageBreakdownJson: String? = null,
    val systemType: String? = null,
    val osName: String? = null,
    val osEdition: String? = null,
    val osVersion: String? = null,
    val osBuild: String? = null,
    val installedOn: String? = null,
    val domainOrWorkgroup: String? = null,
    val deviceUuid: String? = null,
    val productId: String? = null,
    val penTouchSupport: String? = null,
    val ipAddress: String? = null,
    val macAddress: String? = null,
    val connectionSource: String? = null,
    val status: String? = null,
    val assignedDepartment: String? = null,
    val assignedUser: String? = null,
    val notes: String? = null,
    val lastSeenAt: String? = null
)

@Serializable
data class InventoryFilters(
    val search: String? = null,
    val status: String? = null,
    val department: String? = null,
    val assignedUser: String? = null,
    val connectionSource: String? = null,
    val sortBy: String? = null,
    val sortOrder: String? = null,
    val page: Int = 1,
    val pageSize: Int = 20
)

@Serializable
data class InventoryStatsResponse(
    val total: Int,
    val online: Int,
    val offline: Int,
    val stale: Int,
    val bySource: Map<String, Int>
)

@Serializable
data class InventoryAssignRequest(
    val assignedDepartment: String? = null,
    val assignedUser: String? = null
)

@Serializable
data class InventoryNotesRequest(
    val notes: String
)

@Serializable
data class InventoryStatusRequest(
    val status: String
)

@Serializable
data class InventoryStatusResponse(
    val id: Int,
    val status: String,
    val lastSeenAt: String? = null,
    val updatedAt: String
)

@Serializable
data class InventoryUpsertResponse(
    val id: Int,
    val created: Boolean,
    val status: String,
    val lastSeenAt: String? = null,
    val message: String
)
