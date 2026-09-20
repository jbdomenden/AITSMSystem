package backend.routes

import backend.models.ProfilePhotoReviewRequest
import backend.models.UserRole
import backend.security.requireRole
import backend.security.userId
import backend.services.ProfilePhotoService
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining

fun Route.profilePhotoRoutes(service: ProfilePhotoService) {
    route("/api/profile-photo-requests") {
        post {
            val actor = call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val multipart = call.receiveMultipart()
            var filename: String? = null
            var contentType: io.ktor.http.ContentType? = null
            var bytes: ByteArray? = null
            multipart.forEachPart { part ->
                if (part is PartData.FileItem && part.name == "photo") {
                    filename = part.originalFileName
                    contentType = part.contentType
                    bytes = part.provider().readRemaining().readBytes()
                }
                part.dispose()
            }
            val submitted = service.submit(actor, filename, contentType, bytes ?: byteArrayOf())
            call.respond(HttpStatusCode.Created, submitted)
        }

        get("/pending") {
            if (!call.requireRole(UserRole.ADMIN)) return@get
            call.respond(service.pending())
        }

        get("/mine") {
            val actor = call.userId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            call.respond(service.pendingFor(actor) ?: mapOf<String, String?>())
        }

        post("/{id}/review") {
            if (!call.requireRole(UserRole.ADMIN)) return@post
            val actor = call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val id = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
            val review = call.receive<ProfilePhotoReviewRequest>()
            call.respond(service.review(id, actor, review.approved))
        }
    }
}
