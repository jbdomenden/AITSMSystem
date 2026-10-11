package backend.security

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutingRegressionTest {
    @Test fun `page asset and api keep distinct response types`() = testApplication {
        application {
            routing {
                get("/dashboard-admin") { call.respondText("<!doctype html>", ContentType.Text.Html) }
                get("/dashboard-admin/js/dashboard.js") { call.respondText("const ok = true;", ContentType.Application.JavaScript) }
                get("/api/tickets") { call.respondText("{\"status\":401,\"message\":\"Authentication required\"}", ContentType.Application.Json, HttpStatusCode.Unauthorized) }
            }
        }
        val page = client.get("/dashboard-admin")
        assertTrue(page.headers["Content-Type"]?.startsWith("text/html") == true)
        val script = client.get("/dashboard-admin/js/dashboard.js")
        assertTrue(script.headers["Content-Type"]?.contains("javascript") == true)
        val api = client.get("/api/tickets")
        assertEquals(HttpStatusCode.Unauthorized, api.status)
        assertTrue(api.headers["Content-Type"]?.startsWith("application/json") == true)
        assertTrue(api.bodyAsText().contains("Authentication required"))
    }
}
