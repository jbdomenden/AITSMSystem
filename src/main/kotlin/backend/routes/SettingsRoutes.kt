package backend.routes

import backend.models.AssetIpPrefixesRequest
import backend.models.AssetIpPrefixesResponse
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
            call.respond(AssetIpPrefixesResponse(prefixes = assetDetectionService.getPrefixes()))
        }

        post("/asset-ip-prefixes") {
            if (!call.requireRole(UserRole.ADMIN)) return@post
            val req = call.receive<AssetIpPrefixesRequest>()
            assetDetectionService.savePrefixes(req.prefixes)
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
