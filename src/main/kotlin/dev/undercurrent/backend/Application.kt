package dev.undercurrent.backend

import dev.undercurrent.backend.db.Db
import dev.undercurrent.backend.db.MigrationRunner
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    // Fail fast if the DB is unreachable — Db.connect() throws on missing
    // DATABASE_URL; DriverManager throws if the URL is unreachable.
    val connection = Db.connect()
    val migrationRunner = MigrationRunner.fromClasspath(connection)

    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        module(migrationRunner = migrationRunner)
    }.start(wait = true)
}

fun Application.module(migrationRunner: MigrationRunner? = null) {
    install(ContentNegotiation) { json() }
    migrationRunner?.migrate()
    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok"))
        }
    }
}

@Serializable
data class HealthResponse(val status: String)
