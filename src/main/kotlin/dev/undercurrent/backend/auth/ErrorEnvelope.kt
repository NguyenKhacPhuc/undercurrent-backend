package dev.undercurrent.backend.auth

import kotlinx.serialization.Serializable

@Serializable
data class ErrorEnvelope(val error: ErrorBody) {
    companion object {
        fun of(code: String, message: String, details: Map<String, String>? = null) =
            ErrorEnvelope(ErrorBody(code, message, details))
    }
}

@Serializable
data class ErrorBody(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null,
)
