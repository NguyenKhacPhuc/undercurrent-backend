package dev.undercurrent.backend.auth

import dev.undercurrent.backend.accounts.AccountsRepository
import dev.undercurrent.backend.accounts.EmailAlreadyRegisteredException
import dev.undercurrent.backend.accounts.NewAccount
import dev.undercurrent.backend.common.BaseErrorResponse
import dev.undercurrent.backend.common.BaseResponse
import dev.undercurrent.backend.sessions.SessionsRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

@Serializable
data class SignUpRequest(
    val displayName: String,
    val email: String,
    val password: String,
)

fun Route.signUpRoute(
    accounts: AccountsRepository,
    sessions: SessionsRepository,
) {
    post("/v1/auth/sign-up") {
        val request = try {
            call.receive<SignUpRequest>()
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                BaseErrorResponse("invalid_request", "Request body is missing or malformed"),
            )
            return@post
        }

        val displayName = request.displayName.trim()
        val email = request.email.trim().lowercase()
        val password = request.password

        val violations = mutableMapOf<String, String>()
        if (displayName.isEmpty()) violations["displayName"] = "must not be empty"
        if (displayName.length > 40) violations["displayName"] = "must be 40 characters or fewer"
        if (!isLooselyValidEmail(email)) violations["email"] = "must be a valid email address"
        if (password.length < 8) violations["password"] = "must be at least 8 characters"

        if (violations.isNotEmpty()) {
            call.respond(
                HttpStatusCode.BadRequest,
                BaseErrorResponse("invalid_request", "One or more fields are invalid", violations),
            )
            return@post
        }

        val account = try {
            accounts.insert(
                NewAccount(
                    displayName = displayName,
                    email = email,
                    passwordHash = PasswordHasher.hash(password),
                ),
            )
        } catch (e: EmailAlreadyRegisteredException) {
            call.respond(
                HttpStatusCode.Conflict,
                BaseErrorResponse("email_already_registered", "An account with this email already exists"),
            )
            return@post
        }

        val session = sessions.issue(account.id)

        call.respond(
            HttpStatusCode.Created,
            BaseResponse.ok(
                AuthResponse(
                    account = AccountDto(
                        id = account.id,
                        displayName = account.displayName,
                        email = account.email,
                        createdAtMs = account.createdAtMs,
                    ),
                    session = SessionDto(
                        token = session.token,
                        expiresAtMs = session.expiresAtMs,
                    ),
                ),
            ),
        )
    }
}

/** Loose email check per Inception Q2: contains `@`, contains `.` after `@`, no whitespace. */
private fun isLooselyValidEmail(value: String): Boolean {
    if (value.any { it.isWhitespace() }) return false
    val at = value.indexOf('@')
    if (at <= 0 || at == value.lastIndex) return false
    val dot = value.indexOf('.', startIndex = at + 1)
    return dot != -1 && dot != value.lastIndex
}
