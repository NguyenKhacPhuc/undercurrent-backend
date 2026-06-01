package dev.undercurrent.backend.auth

import dev.undercurrent.backend.accounts.AccountsRepository
import dev.undercurrent.backend.common.BaseErrorResponse
import dev.undercurrent.backend.common.BaseResponse
import dev.undercurrent.backend.sessions.requireAuth
import io.ktor.http.CacheControl
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.cacheControl
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class MeResponse(val account: AccountDto)

fun Route.meRoute(accounts: AccountsRepository) {
    get("/v1/me") {
        requireAuth { accountId ->
            val account = accounts.findById(accountId)
            if (account == null) {
                // Session pointed at an account that no longer exists. Treat
                // as unauthenticated — defensive: v1 has no account deletion
                // but the assumption may not hold forever.
                call.respond(
                    HttpStatusCode.Unauthorized,
                    BaseErrorResponse("unauthenticated", "Session is no longer valid"),
                )
                return@requireAuth
            }
            // Identity is per-user — keep it out of any intermediary cache.
            call.response.cacheControl(CacheControl.NoStore(visibility = CacheControl.Visibility.Private))
            call.respond(
                HttpStatusCode.OK,
                BaseResponse.ok(
                    MeResponse(
                        account = AccountDto(
                            id = account.id,
                            displayName = account.displayName,
                            email = account.email,
                            createdAtMs = account.createdAtMs,
                        ),
                    ),
                ),
            )
        }
    }
}
