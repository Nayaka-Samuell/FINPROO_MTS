package com.ecoloop

import com.ecoloop.plugins.*
import com.ecoloop.repositories.ExposedEcoRepository
import com.ecoloop.routes.configureRoutes
import com.ecoloop.services.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module() }.start(wait = true)
}
fun Application.module(env: Map<String, String> = System.getenv()) {
    val jwt = JwtConfig(env)
    val database = DatabaseFactory.connect(env)
    monitor.subscribe(ApplicationStopped) { database.close() }
    val repository = ExposedEcoRepository(database.database)
    configureHttp(jwt, repository, env)
    configureRoutes(AuthService(repository, jwt), CatalogService(repository), ReservationService(repository))
}
