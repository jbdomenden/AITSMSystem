package backend.security

import backend.models.UserRole

enum class Permission {
    TICKET_CREATE, TICKET_READ_ALL, TICKET_MANAGE,
    USER_READ, USER_MANAGE, USER_INVITE, ROLE_MANAGE,
    ASSET_READ, ASSET_MANAGE, KNOWLEDGE_MANAGE,
    AUDIT_READ, SECURITY_MANAGE, AI_CONFIGURE
}

object PermissionMatrix {
    private val endUser = setOf(Permission.TICKET_CREATE)
    private val support = endUser + setOf(Permission.TICKET_READ_ALL, Permission.TICKET_MANAGE, Permission.USER_READ)
    private val asset = setOf(Permission.ASSET_READ, Permission.ASSET_MANAGE)
    private val admin = Permission.entries.toSet() - Permission.AUDIT_READ

    fun permissions(role: UserRole): Set<Permission> = when (role) {
        UserRole.SUPERADMIN -> Permission.entries.toSet()
        UserRole.ADMIN, UserRole.IT_ADMIN -> admin
        UserRole.SUPPORT_AGENT -> support
        UserRole.ASSET_MANAGER -> asset
        UserRole.AUDITOR -> setOf(Permission.AUDIT_READ, Permission.ASSET_READ, Permission.TICKET_READ_ALL, Permission.USER_READ)
        UserRole.END_USER -> endUser
    }
}
