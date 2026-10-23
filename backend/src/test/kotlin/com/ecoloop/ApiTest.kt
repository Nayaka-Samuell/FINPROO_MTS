package com.ecoloop

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.test.*

class ApiTest {
    private fun env() = mapOf("DB_URL" to "jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_UPPER=false", "JWT_SECRET" to "test-only-ephemeral-secret-at-least-32-characters")
    private suspend fun HttpResponse.json() = Json.parseToJsonElement(bodyAsText()).jsonObject
    private suspend fun HttpResponse.data() = json().getValue("data").jsonObject
    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
    private suspend fun ApplicationTestBuilder.register(email: String): Pair<String, String> {
        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Eco User","email":"$email","password":"password123","city":"DKI Jakarta","district":"Jakarta Selatan"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        val data = response.data()
        return data.string("token") to data.getValue("user").jsonObject.string("id")
    }

    @Test fun `authentication profile and standard errors`() = testApplication {
        application { module(env()) }
        val (token, _) = register("SUPPLIER@example.com")
        val profile = client.get("/api/v1/me") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, profile.status)
        assertEquals("supplier@example.com", profile.data().string("email"))
        assertFalse(profile.bodyAsText().contains("password"))
        val changed = client.patch("/api/v1/me") {
            bearerAuth(token); contentType(ContentType.Application.Json); setBody("""{"name":"Nama Baru","district":"Jakarta Barat"}""")
        }
        assertEquals("Nama Baru", changed.data().string("name"))
        val login = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json); setBody("""{"email":"supplier@example.com","password":"password123"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status)
        val wrong = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json); setBody("""{"email":"supplier@example.com","password":"wrong"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        assertEquals("INVALID_CREDENTIALS", wrong.json().getValue("error").jsonObject.string("code"))
        val duplicate = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json); setBody("""{"name":"Again","email":"supplier@example.com","password":"password123","city":"DKI Jakarta","district":"Jakarta Selatan"}""")
        }
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me") { bearerAuth("invalid") }.status)
        val malformed = client.post("/api/v1/auth/login") { contentType(ContentType.Application.Json); setBody("{") }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
        assertNotNull(malformed.json()["error"])
        val missing = client.get("/does-not-exist")
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertNotNull(missing.json()["error"])
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/listings/not-a-uuid").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/listings?page=0").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/listings?limit=101").status)
    }

    @Test fun `full API flow returns inventory transfers feedback and impact`() = testApplication {
        application { module(env()) }
        val (supplier, _) = register("supplier@example.com")
        val (receiver, _) = register("receiver@example.com")
        val categoryResponse = client.get("/api/v1/categories")
        assertEquals(4, categoryResponse.json().getValue("data").jsonArray.size)
        val listing = client.post("/api/v1/listings") {
            bearerAuth(supplier); contentType(ContentType.Application.Json)
            setBody("""{"categoryId":"00000000-0000-0000-0000-000000000001","title":"Kain perca bersih","description":"Untuk kerajinan non-B3","totalKg":10.00,"district":"Jakarta Selatan","photos":["https://example.com/material.jpg"]}""")
        }
        assertEquals(HttpStatusCode.Created, listing.status, listing.bodyAsText())
        val listingId = listing.data().string("id")
        val reservation = client.post("/api/v1/reservations") {
            bearerAuth(receiver); contentType(ContentType.Application.Json); setBody("""{"listingId":"$listingId","requestedKg":4.00}""")
        }
        assertEquals(HttpStatusCode.Created, reservation.status, reservation.bodyAsText())
        val reservationId = reservation.data().string("id")
        assertEquals("REQUESTED", reservation.data().string("status"))
        val denied = client.post("/api/v1/reservations/$reservationId/approve") { bearerAuth(receiver) }
        assertEquals(HttpStatusCode.Forbidden, denied.status)
        assertEquals(HttpStatusCode.OK, client.post("/api/v1/reservations/$reservationId/approve") { bearerAuth(supplier) }.status)
        val incoming = client.get("/api/v1/reservations?view=incoming") { bearerAuth(supplier) }
        assertEquals(1, incoming.json().getValue("data").jsonArray.size)
        val tokenResponse = client.post("/api/v1/reservations/$reservationId/handover-token") { bearerAuth(receiver) }
        val handoverToken = tokenResponse.data().string("token")
        val complete = client.post("/api/v1/reservations/$reservationId/complete") {
            bearerAuth(supplier); contentType(ContentType.Application.Json); setBody("""{"handoverToken":"$handoverToken","actualKg":3.80}""")
        }
        assertEquals(HttpStatusCode.OK, complete.status, complete.bodyAsText())
        val transferId = complete.data().string("id")
        val stock = client.get("/api/v1/listings/$listingId").data().getValue("availableKg").jsonPrimitive.content.toBigDecimal()
        assertEquals("6.20".toBigDecimal(), stock)
        val impact = client.get("/api/v1/impact/me") { bearerAuth(supplier) }
        assertEquals("3.80".toBigDecimal(), impact.data().string("totalKg").toBigDecimal())
        assertEquals("1", impact.data().string("transferCount"))
        val feedback = client.post("/api/v1/transfers/$transferId/feedback") {
            bearerAuth(receiver); contentType(ContentType.Application.Json); setBody("""{"status":"USED"}""")
        }
        assertEquals(HttpStatusCode.OK, feedback.status, feedback.bodyAsText())
        assertEquals("USED", feedback.data().string("status"))
        assertEquals(HttpStatusCode.OK, client.delete("/api/v1/listings/$listingId") { bearerAuth(supplier) }.status)
    }

    @Test fun `auth endpoints have IP rate limiting and CORS uses allowlist`() = testApplication {
        application { module(env()) }
        repeat(10) {
            val result = client.post("/api/v1/auth/login") { contentType(ContentType.Application.Json); setBody("{}") }
            assertEquals(HttpStatusCode.BadRequest, result.status)
        }
        val limited = client.post("/api/v1/auth/login") { contentType(ContentType.Application.Json); setBody("{}") }
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertEquals("RATE_LIMITED", limited.json().getValue("error").jsonObject.string("code"))
        val allowed = client.get("/api/v1/categories") { header(HttpHeaders.Origin, "http://localhost:3000") }
        assertEquals("http://localhost:3000", allowed.headers[HttpHeaders.AccessControlAllowOrigin])
        val denied = client.get("/api/v1/categories") { header(HttpHeaders.Origin, "https://untrusted.example") }
        assertNull(denied.headers[HttpHeaders.AccessControlAllowOrigin])
    }
}
