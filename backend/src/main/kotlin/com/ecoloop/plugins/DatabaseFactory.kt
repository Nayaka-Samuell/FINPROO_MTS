package com.ecoloop.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import java.io.File
import java.net.URI

class DatabaseHandle(val source: HikariDataSource, val database: Database) : AutoCloseable {
    override fun close() = source.close()
}
object DatabaseFactory {
    fun connect(env: Map<String, String> = System.getenv()): DatabaseHandle {
        val raw = env["JDBC_DATABASE_URL"] ?: env["DB_URL"] ?: "jdbc:h2:./data/ecoloop;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
        var user = env["DB_USER"]
        var password = env["DB_PASSWORD"]
        val url = if (raw.startsWith("postgres://") || raw.startsWith("postgresql://")) {
            val uri = URI(raw)
            uri.userInfo?.split(":", limit = 2)?.let {
                user = user ?: java.net.URLDecoder.decode(it[0], Charsets.UTF_8)
                password = password ?: java.net.URLDecoder.decode(it.getOrElse(1) { "" }, Charsets.UTF_8)
            }
            "jdbc:postgresql://${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}${uri.rawPath}${uri.rawQuery?.let { "?$it" } ?: ""}"
        } else raw
        require(url.startsWith("jdbc:postgresql:") || url.startsWith("jdbc:h2:")) { "Gunakan PostgreSQL atau H2" }
        if (url.startsWith("jdbc:h2:./data/")) File("data").mkdirs()
        val config = HikariConfig().apply {
            jdbcUrl = url
            username = user ?: if (url.startsWith("jdbc:h2:")) "sa" else "postgres"
            this.password = password ?: ""
            maximumPoolSize = 10
            minimumIdle = 1
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            connectionTimeout = 10000
        }
        val source = HikariDataSource(config)
        try {
            Flyway.configure().dataSource(source).load().migrate()
            return DatabaseHandle(source, Database.connect(source))
        } catch (error: Exception) { source.close(); throw error }
    }
}
