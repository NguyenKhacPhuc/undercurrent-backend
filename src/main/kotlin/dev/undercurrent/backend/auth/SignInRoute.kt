package dev.undercurrent.backend.auth

import dev.undercurrent.backend.accounts.AccountsRepository
import dev.undercurrent.backend.sessions.SessionsRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

@Serializable
data class SignInRequest(
    val email: String,
    val password: String,
)

/**
 * Pre-computed argon2id hash of a single throwaway string. Used to keep
 * the response timing for "unknown email" similar to "wrong password" —
 * we always do one hash verify regardless of which side failed. Prevents
 * response-timing-based email enumeration. Loaded lazily so the cost is
 * paid once per process, not per request.
 */
private val DUMMY_HASH: String by lazy {
    PasswordHasher.hash("undercurrent-dummy-password-for-timing-safety")
}

fun Route.signInRoute(
    accounts: AccountsRepository,
    sessions: SessionsRepository,
    rateLimiter: SignInRateLimiter,
) {
    post("/v1/auth/sign-in") {
        val request = try {
            call.receive<SignInRequest>()
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorEnvelope.of("invalid_request", "Request body is missing or malformed"),
            )
            return@post
        }

        val email = request.email.trim().lowercase()
        val password = request.password

        if (email.isEmpty() || password.isEmpty()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorEnvelope.of("invalid_request", "email and password are required"),
            )
            return@post
        }

        if (rateLimiter.shouldThrottle(email)) {
            call.respond(
                HttpStatusCode.TooManyRequests,
                ErrorEnvelope.of("rate_limited", "Too many failed sign-in attempts. Try again later."),
            )
            return@post
        }

        val account = accounts.findByEmail(email)
        // Always run one hash verify so timing for "unknown email" matches
        // "wrong password" — closes a side-channel for email enumeration.
        val passwordMatches = if (account != null) {
            PasswordHasher.verify(password, account.passwordHash)
        } else {
            PasswordHasher.verify(password, DUMMY_HASH)
            false
        }

        if (account == null || !passwordMatches) {
            rateLimiter.recordFailedAttempt(email)
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorEnvelope.of("unauthenticated", "Invalid email or password"),
            )
            return@post
        }

        rateLimiter.recordSuccessfulAttempt(email)
        val session = sessions.issue(account.id)

        call.respond(
            HttpStatusCode.OK,
            AuthResponse(
                account = AccountDto(
                    id = account.id,
                    displayName = account.displayName,
                    email = account.email,
                    createdAtMs = account.createdAtMs,
                ),
                session = SessionDto(token = session.token, expiresAtMs = session.expiresAtMs),
            ),
        )
    }
}
