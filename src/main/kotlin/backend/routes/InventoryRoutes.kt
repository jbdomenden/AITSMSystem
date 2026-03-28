package backend.routes

import backend.models.InventoryAssignRequest
import backend.models.InventoryFilters
import backend.models.InventoryNotesRequest
import backend.models.InventoryStatusRequest
import backend.models.InventoryUpsertDetectedRequest
import backend.models.UserRole
import backend.security.requireRole
import backend.services.InventoryService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.inventoryRoutes(inventoryService: InventoryService) {
    route("/api/inventory") {
        get {
            if (!call.requireRole(UserRole.ADMIN)) return@get
            val filters = InventoryFilters(
                search = call.request.queryParameters["search"],
                status = call.request.queryParameters["status"],
                department = call.request.queryParameters["department"],
                assignedUser = call.request.queryParameters["assignedUser"],
                connectionSource = call.request.queryParameters["connectionSource"],
                sortBy = call.request.queryParameters["sortBy"],
                sortOrder = call.request.queryParameters["sortOrder"],
                page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1,
                pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20
            )
            call.respond(inventoryService.listAssets(filters))
        }

        get("/stats") {
            if (!call.requireRole(UserRole.ADMIN)) return@get
            call.respond(inventoryService.getInventoryStats())
        }

        get("/export") {
            if (!call.requireRole(UserRole.ADMIN)) return@get
            val filters = InventoryFilters(
                search = call.request.queryParameters["search"],
                status = call.request.queryParameters["status"],
                department = call.request.queryParameters["department"],
                assignedUser = call.request.queryParameters["assignedUser"],
                connectionSource = call.request.queryParameters["connectionSource"],
                sortBy = call.request.queryParameters["sortBy"],
                sortOrder = call.request.queryParameters["sortOrder"],
                page = 1,
                pageSize = 100
            )
            val csv = inventoryService.exportAssetsCsv(filters)
            call.respondText(csv, ContentType.Text.CSV)
        }

        post("/upsert-detected") {
            if (!call.requireRole(UserRole.ADMIN)) return@post
            val payload = call.receive<InventoryUpsertDetectedRequest>()
            val saved = inventoryService.upsertDetectedAsset(payload)
            call.respond(if (saved.created) HttpStatusCode.Created else HttpStatusCode.OK, saved)
        }

        get("/{id}") {
            if (!call.requireRole(UserRole.ADMIN)) return@get
            val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid asset id"))
            val details = inventoryService.getAssetDetails(id) ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Asset not found"))
            call.respond(details)
        }

        patch("/{id}/assign") {
            if (!call.requireRole(UserRole.ADMIN)) return@patch
            val id = call.parameters["id"]?.toIntOrNull() ?: return@patch call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid asset id"))
            val payload = call.receive<InventoryAssignRequest>()
            val updated = inventoryService.updateAssignment(id, payload.assignedDepartment, payload.assignedUser)
                ?: return@patch call.respond(HttpStatusCode.NotFound, mapOf("error" to "Asset not found"))
            call.respond(updated)
        }

        patch("/{id}/notes") {
            if (!call.requireRole(UserRole.ADMIN)) return@patch
            val id = call.parameters["id"]?.toIntOrNull() ?: return@patch call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid asset id"))
            val payload = call.receive<InventoryNotesRequest>()
            val updated = inventoryService.updateNotes(id, payload.notes)
                ?: return@patch call.respond(HttpStatusCode.NotFound, mapOf("error" to "Asset not found"))
            call.respond(updated)
        }

        patch("/{id}/status") {
            if (!call.requireRole(UserRole.ADMIN)) return@patch
            val id = call.parameters["id"]?.toIntOrNull() ?: return@patch call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid asset id"))
            val payload = call.receive<InventoryStatusRequest>()
            val updated = inventoryService.updateStatus(id, payload.status)
                ?: return@patch call.respond(HttpStatusCode.NotFound, mapOf("error" to "Asset not found"))
            call.respond(updated)
        }
    }
}
