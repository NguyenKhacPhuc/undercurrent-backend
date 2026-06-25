package dev.undercurrent.backend.prompt

import dev.undercurrent.backend.common.BaseErrorResponse
import dev.undercurrent.backend.common.BaseResponse
import dev.undercurrent.backend.sessions.requireAuth
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class PromptConfigDto(
    val preamble: String,
    val revision: String,
    val updatedAtMs: Long,
)

fun Route.promptConfigRoute(prompts: PromptConfigRepository) {
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
}
