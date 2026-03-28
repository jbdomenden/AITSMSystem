package backend.routes

import backend.models.AssetIpPrefixesRequest
import backend.models.AssetIpPrefixesResponse
import backend.models.UserRole
import backend.security.requireRole
import backend.services.AssetDetectionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun Route.settingsRoutes(assetDetectionService: AssetDetectionService) {
    route("/settings") {
        get("/asset-ip-prefixes") {
            if (!call.requireRole(UserRole.ADMIN)) return@get
            call.respond(AssetIpPrefixesResponse(prefixes = assetDetectionService.getPrefixes()))
        }

        post("/asset-ip-prefixes") {
            if (!call.requireRole(UserRole.ADMIN)) return@post
            val rawBody = call.receiveText()
            val payload = runCatching { Json.parseToJsonElement(rawBody).jsonObject }.getOrDefault(kotlinx.serialization.json.buildJsonObject { })
            val prefixes = when (val value = payload["prefixes"]) {
                is JsonArray -> value.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
                null -> emptyList()
                else -> listOf(value.jsonPrimitive.content)
                    .flatMap { it.split('\n', ',') }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }
            assetDetectionService.savePrefixes(AssetIpPrefixesRequest(prefixes = prefixes).prefixes)
            call.respond(
                HttpStatusCode.OK,
                AssetIpPrefixesResponse(
                    prefixes = assetDetectionService.getPrefixes(),
                    message = "Asset detection prefixes saved"
                )
            )
        }
    }
}
