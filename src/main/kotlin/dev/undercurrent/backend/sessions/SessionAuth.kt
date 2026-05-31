package dev.undercurrent.backend.sessions

import dev.undercurrent.backend.auth.ErrorEnvelope
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import io.ktor.util.AttributeKey

/**
 * Reads `Authorization: Bearer <token>` from incoming requests; if the token
 * validates against the provided repository, stamps the call's attributes
 * with the resolved account id so route handlers can pull it via [requireAuth].
 *
 * Routes that need auth call [requireAuth] inside their handler. This is
 * deliberately handler-side rather than a route nesting wrapper — keeps the
 * dependency surface tiny (no `ktor-server-auth` plugin) and the API explicit.
 */
fun Application.installSessionAuth(repository: SessionsRepository) {
    intercept(ApplicationCallPipeline.Plugins) {
        val authHeader = call.request.headers["Authorization"] ?: return@intercept
        if (!authHeader.startsWith("Bearer ", ignoreCase = true)) return@intercept
        val token = authHeader.substring("Bearer ".length).trim()
        if (token.isEmpty()) return@intercept
        val session = repository.validate(token) ?: return@intercept
        call.attributes.put(AuthenticatedAccountIdKey, session.accountId)
    }
}

/** Key under which the authenticated account id is stored on the call. */
val AuthenticatedAccountIdKey: AttributeKey<String> = AttributeKey("undercurrent.AuthenticatedAccountId")

/**
 * Wraps a handler so that it only runs when the request carried a valid
 * session token; otherwise responds 401 with the standard error envelope.
 *
 * Usage:
 * ```
 * get("/v1/me") {
 *   requireAuth { accountId -> call.respond(...) }
 * }
 * ```
 */
suspend fun RoutingContext.requireAuth(
    block: suspend (accountId: String) -> Unit,
) {
    val accountId = call.attributes.getOrNull(AuthenticatedAccountIdKey)
    if (accountId == null) {
        call.respond(
            HttpStatusCode.Unauthorized,
            ErrorEnvelope.of("unauthenticated", "Missing or invalid session token"),
        )
        return
    }
    block(accountId)
}
