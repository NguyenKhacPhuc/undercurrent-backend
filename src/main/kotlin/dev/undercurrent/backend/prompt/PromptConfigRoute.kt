package dev.undercurrent.backend.prompt

import dev.undercurrent.backend.common.BaseErrorResponse
import dev.undercurrent.backend.common.BaseResponse
import dev.undercurrent.backend.sessions.requireAuth
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable

@Serializable
data class PromptConfigDto(
    val preamble: String,
    val revision: String,
    val updatedAtMs: Long,
)

@Serializable
data class UpdatePromptConfigRequest(
    val preamble: String,
)

@Serializable
data class UpdatePromptConfigDto(
    val revision: String,
    val updatedAtMs: Long,
)

/**
 * Minimum accepted preamble length (Q2 driver-guess). The no-fallback design
 * means a broken/empty prompt must never reach a client, so we reject anything
 * trivially short. This threshold is a placeholder the mob still owes a call on.
 */
const val MIN_PREAMBLE_LENGTH = 20

/**
 * @param operatorSecret the configured operator secret (from the
 *   `PROMPT_OPERATOR_SECRET` env var). When `null` (env var unset), the PUT
 *   endpoint fails closed — no update is ever accepted. The exact operator-auth
 *   mechanism is a parked decision (Q3 driver-guess: a shared server secret in
 *   an `X-Operator-Secret` header).
 */
fun Route.promptConfigRoute(
    prompts: PromptConfigRepository,
    operatorSecret: String? = null,
) {
    get("/v1/prompt-config") {
        requireAuth { _ ->
            val config = prompts.get()
            if (config == null) {
                // No fallback by design — a missing/unseeded config is a clear
                // "temporarily unavailable", never an empty or broken prompt.
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    BaseErrorResponse("unavailable", "The prompt configuration is not available yet"),
                )
                return@requireAuth
            }
            call.respond(
                HttpStatusCode.OK,
                BaseResponse.ok(
                    PromptConfigDto(
                        preamble = config.preamble,
                        revision = config.revision,
                        updatedAtMs = config.updatedAtMs,
                    ),
                ),
            )
        }
    }

    put("/v1/prompt-config") {
        // Operator auth (Q3 driver-guess): a shared secret supplied in a header,
        // compared against a server-configured value. Fail closed when no secret
        // is configured — an unset env var must never leave the endpoint open.
        val presented = call.request.headers["X-Operator-Secret"]
        if (operatorSecret == null || presented == null || presented != operatorSecret) {
            call.respond(
                HttpStatusCode.Forbidden,
                BaseErrorResponse("forbidden", "Operator authorization required"),
            )
            return@put
        }

        val request = try {
            call.receive<UpdatePromptConfigRequest>()
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                BaseErrorResponse("invalid_request", "Request body is missing or malformed"),
            )
            return@put
        }

        // Invalid-prompt guard (Q2 driver-guess): no client fallback exists, so a
        // blank/whitespace/too-short prompt must never be stored.
        val preamble = request.preamble.trim()
        if (preamble.length < MIN_PREAMBLE_LENGTH) {
            call.respond(
                HttpStatusCode.BadRequest,
                BaseErrorResponse(
                    "invalid_request",
                    "preamble must be non-empty and at least $MIN_PREAMBLE_LENGTH characters",
                ),
            )
            return@put
        }

        val updated = prompts.update(preamble)
        call.respond(
            HttpStatusCode.OK,
            BaseResponse.ok(
                UpdatePromptConfigDto(
                    revision = updated.revision,
                    updatedAtMs = updated.updatedAtMs,
                ),
            ),
        )
    }
}
