package dev.undercurrent.backend.auth

import dev.undercurrent.backend.common.BaseResponse
import dev.undercurrent.backend.sessions.SessionsRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

/**
 * Always responds 200 with `{ success: true, data: null, message: null, code: null }` —
 * per api-contract this endpoint is idempotent and leaks no information about whether
 * the presented token existed. We read the bearer manually (not via `requireAuth`)
 * precisely so that "no header" and "garbage header" both produce the success
 * envelope, not 401.
 */
fun Route.signOutRoute(sessions: SessionsRepository) {
    post("/v1/auth/sign-out") {
        val authHeader = call.request.headers["Authorization"]
        if (authHeader != null && authHeader.startsWith("Bearer ", ignoreCase = true)) {
            val token = authHeader.substring("Bearer ".length).trim()
            if (token.isNotEmpty()) sessions.revoke(token)
        }
        call.respond(HttpStatusCode.OK, BaseResponse.empty())
    }
}
