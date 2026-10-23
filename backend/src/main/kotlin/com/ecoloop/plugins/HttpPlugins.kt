package com.ecoloop.plugins

import com.auth0.jwt.JWT
import com.ecoloop.dtos.*
import com.ecoloop.repositories.EcoRepository
import com.ecoloop.services.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.exceptions.ExposedSQLException
import java.net.URI
import kotlin.time.Duration.Companion.minutes

fun Application.configureHttp(jwt: JwtConfig, repository: EcoRepository, env: Map<String, String>) {
    install(ContentNegotiation) { json(Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }) }
    install(CallLogging) {
        format { call -> "${call.request.httpMethod.value} ${call.request.path()} ${call.response.status()?.value}" }
    }
    install(CORS) {
        allowMethod(HttpMethod.Get); allowMethod(HttpMethod.Post); allowMethod(HttpMethod.Patch); allowMethod(HttpMethod.Delete); allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.Authorization); allowHeader(HttpHeaders.ContentType)
        val configured = env["CORS_ORIGINS"] ?: env["FRONTEND_URL"]
        require(env["APP_ENV"] != "production" || !configured.isNullOrBlank()) { "CORS_ORIGINS wajib di production" }
        (configured ?: "http://localhost:3000,http://localhost:5173").split(",").forEach {
            val uri = URI(it.trim())
            require(uri.scheme in setOf("http", "https") && uri.host != null && uri.userInfo == null && (uri.path.isNullOrEmpty() || uri.path == "/")) { "Origin CORS tidak valid" }
            allowHost(uri.rawAuthority, schemes = listOf(uri.scheme))
        }
    }
    install(RateLimit) {
        global {
            rateLimiter(limit = 120, refillPeriod = 1.minutes)
            requestKey { it.request.local.remoteHost }
        }
        register(RateLimitName("auth")) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { it.request.local.remoteHost }
        }
    }
    install(StatusPages) {
        exception<ApiException> { call, cause -> call.respond(cause.status, ErrorResponse(ErrorBody(cause.code, cause.message))) }
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorBody("INVALID_REQUEST", "Format permintaan tidak valid"))) }
        exception<SerializationException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorBody("INVALID_REQUEST", "Format JSON tidak valid"))) }
        exception<UnsupportedMediaTypeException> { call, _ -> call.respond(HttpStatusCode.UnsupportedMediaType, ErrorResponse(ErrorBody("UNSUPPORTED_MEDIA_TYPE", "Gunakan Content-Type application/json"))) }
        exception<ExposedSQLException> { call, cause ->
            if (cause.sqlState == "23505") call.respond(HttpStatusCode.Conflict, ErrorResponse(ErrorBody("DUPLICATE_RESOURCE", "Data sudah tercatat")))
            else {
                // Do not log SQL bindings, credentials, password hashes or handover tokens.
                call.application.log.error("Database request failed: SQLState={}", cause.sqlState)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(ErrorBody("INTERNAL_ERROR", "Terjadi kesalahan server")))
            }
        }
        exception<Throwable> { call, cause ->
            if (cause is kotlinx.coroutines.CancellationException) throw cause
            call.application.log.error("Request failed: {}", cause.javaClass.simpleName)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(ErrorBody("INTERNAL_ERROR", "Terjadi kesalahan server")))
        }
        status(HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed, HttpStatusCode.TooManyRequests, HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden, HttpStatusCode.PayloadTooLarge) { call, status ->
            val (code, message) = when (status) {
                HttpStatusCode.NotFound -> "NOT_FOUND" to "Endpoint tidak ditemukan"
                HttpStatusCode.MethodNotAllowed -> "METHOD_NOT_ALLOWED" to "Metode tidak diizinkan"
                HttpStatusCode.TooManyRequests -> "RATE_LIMITED" to "Terlalu banyak permintaan. Silakan coba lagi nanti"
                HttpStatusCode.Unauthorized -> "UNAUTHORIZED" to "Silakan login dengan token yang valid"
                HttpStatusCode.Forbidden -> "FORBIDDEN" to "Akses tidak diizinkan"
                else -> "PAYLOAD_TOO_LARGE" to "Permintaan terlalu besar"
            }
            call.respond(status, ErrorResponse(ErrorBody(code, message)))
        }
    }
    install(Authentication) {
        jwt("jwt") {
            realm = "EcoLoop"
            verifier(JWT.require(jwt.algorithm).withIssuer(jwt.issuer).withAudience(jwt.audience).build())
            validate { credential ->
                val id = credential.payload.subject?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                if (id != null && repository.transaction { user(id) != null }) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ -> call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorBody("UNAUTHORIZED", "Silakan login dengan token yang valid"))) }
        }
    }
}
