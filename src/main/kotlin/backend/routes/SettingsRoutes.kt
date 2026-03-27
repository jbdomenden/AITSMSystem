package backend.routes

import backend.models.AssetIpPrefixesRequest
import backend.models.UserRole
import backend.security.requireRole
import backend.services.AssetDetectionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.settingsRoutes(assetDetectionService: AssetDetectionService) {
    route("/settings") {
        get("/asset-ip-prefixes") {
            if (!call.requireRole(UserRole.ADMIN)) return@get
            call.respond(mapOf("prefixes" to assetDetectionService.getPrefixes()))
        }

        post("/asset-ip-prefixes") {
            if (!call.requireRole(UserRole.ADMIN)) return@post
            val req = call.receive<AssetIpPrefixesRequest>()
            assetDetectionService.savePrefixes(req.prefixes)
            call.respond(HttpStatusCode.OK, mapOf("prefixes" to assetDetectionService.getPrefixes(), "message" to "Asset detection prefixes saved"))
        }
    }
}
