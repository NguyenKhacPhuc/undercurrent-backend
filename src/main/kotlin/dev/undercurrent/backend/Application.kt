package dev.undercurrent.backend

import dev.undercurrent.backend.accounts.AccountsRepository
import dev.undercurrent.backend.accounts.JdbcAccountsRepository
import dev.undercurrent.backend.auth.NoopSignInRateLimiter
import dev.undercurrent.backend.auth.SignInRateLimiter
import dev.undercurrent.backend.auth.signInRoute
import dev.undercurrent.backend.auth.signOutRoute
import dev.undercurrent.backend.auth.signUpRoute
import dev.undercurrent.backend.db.Db
import dev.undercurrent.backend.db.MigrationRunner
import dev.undercurrent.backend.sessions.JdbcSessionsRepository
import dev.undercurrent.backend.sessions.SessionsRepository
import dev.undercurrent.backend.sessions.installSessionAuth
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

    // Fail fast if the DB is unreachable — Db.dataSource() throws on missing
    // DATABASE_URL; the first getConnection() throws if the URL is unreachable.
    val dataSource = Db.dataSource()
    dataSource.connection.use { MigrationRunner.fromClasspath(it).migrate() }
    val accountsRepository = JdbcAccountsRepository(dataSource)
    val sessionsRepository = JdbcSessionsRepository(dataSource)

    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        module(
            accountsRepository = accountsRepository,
            sessionsRepository = sessionsRepository,
        )
    }.start(wait = true)
}

fun Application.module(
    migrationRunner: MigrationRunner? = null,
    accountsRepository: AccountsRepository? = null,
    sessionsRepository: SessionsRepository? = null,
    signInRateLimiter: SignInRateLimiter = NoopSignInRateLimiter,
) {
    install(ContentNegotiation) { json() }
    migrationRunner?.migrate()
    sessionsRepository?.let { installSessionAuth(it) }
    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok"))
        }
        if (accountsRepository != null && sessionsRepository != null) {
            signUpRoute(accountsRepository, sessionsRepository)
            signInRoute(accountsRepository, sessionsRepository, signInRateLimiter)
        }
        // Sign-out only needs sessions; kept in its own block to (a) reflect
        // that minimal dep accurately and (b) reduce merge-conflict surface
        // when sibling stories add routes to the joint block above.
        sessionsRepository?.let { signOutRoute(it) }
    }
}

@Serializable
data class HealthResponse(val status: String)
