package com.ecoloop.routes

import com.ecoloop.dtos.*
import com.ecoloop.services.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

private fun ApplicationCall.userId(): UUID = uuid(principal<JWTPrincipal>()!!.payload.subject)
private fun ApplicationCall.resourceId(): UUID = uuid(parameters["id"] ?: invalid("ID wajib diisi"))

fun Application.configureRoutes(auth: AuthService, catalog: CatalogService, reservations: ReservationService) {
    routing {
        route("/api/v1") {
            rateLimit(RateLimitName("auth")) {
                route("/auth") {
                    post("/register") { call.respond(HttpStatusCode.Created, DataResponse(auth.register(call.receive<RegisterRequest>()))) }
                    post("/login") { call.respond(DataResponse(auth.login(call.receive<LoginRequest>()))) }
                }
            }
            get("/categories") { call.respond(DataResponse(catalog.categories())) }
            get("/listings") {
                val query = call.request.queryParameters
                call.respond(DataResponse(catalog.browse(query["categoryId"], query["district"], query["q"], query["page"], query["limit"])))
            }
            get("/listings/{id}") { call.respond(DataResponse(catalog.detail(call.resourceId()))) }
            authenticate("jwt") {
                get("/me") { call.respond(DataResponse(auth.me(call.userId()))) }
                patch("/me") { call.respond(DataResponse(auth.patch(call.userId(), call.receive<ProfilePatch>()))) }
                post("/listings") { call.respond(HttpStatusCode.Created, DataResponse(catalog.create(call.userId(), call.receive<ListingCreate>()))) }
                patch("/listings/{id}") { call.respond(DataResponse(catalog.patch(call.userId(), call.resourceId(), call.receive<ListingPatch>()))) }
                delete("/listings/{id}") { call.respond(DataResponse(catalog.delete(call.userId(), call.resourceId()))) }
                post("/reservations") { call.respond(HttpStatusCode.Created, DataResponse(reservations.create(call.userId(), call.receive<ReservationCreate>()))) }
                get("/reservations") { call.respond(DataResponse(reservations.list(call.userId(), call.request.queryParameters["view"]))) }
                post("/reservations/{id}/approve") { call.respond(DataResponse(reservations.approve(call.userId(), call.resourceId()))) }
                post("/reservations/{id}/reject") { call.respond(DataResponse(reservations.reject(call.userId(), call.resourceId()))) }
                post("/reservations/{id}/cancel") { call.respond(DataResponse(reservations.cancel(call.userId(), call.resourceId()))) }
                post("/reservations/{id}/handover-token") { call.respond(DataResponse(reservations.handover(call.userId(), call.resourceId()))) }
                post("/reservations/{id}/complete") { call.respond(DataResponse(reservations.complete(call.userId(), call.resourceId(), call.receive<CompleteRequest>()))) }
                get("/impact/me") { call.respond(DataResponse(reservations.impact(call.userId()))) }
                post("/transfers/{id}/feedback") { call.respond(DataResponse(reservations.feedback(call.userId(), call.resourceId(), call.receive<FeedbackRequest>()))) }
            }
        }
    }
}
