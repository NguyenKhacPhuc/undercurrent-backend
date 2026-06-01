package dev.undercurrent.backend.common

import kotlinx.serialization.Serializable

/**
 * Standard success envelope for every JSON endpoint. Wraps the
 * endpoint-specific payload in `{ success, data, message, code }` so
 * clients have one shape to parse across the whole BE.
 *
 * Convention: success responses set `success=true` and populate
 * `data` (or `null` for endpoints with no payload like sign-out).
 * `message` and `code` are optional and used only when the BE wants
 * to surface a non-failing piece of information alongside the data
 * (currently unused — both null in v1).
 *
 * Mirrors the client-side `BaseResponse<T>` in
 * `data/network/common/BaseResponse.kt`.
 */
@Serializable
data class BaseResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String? = null,
    val code: String? = null,
) {
    companion object {
        fun <T> ok(data: T): BaseResponse<T> = BaseResponse(success = true, data = data)
        fun empty(): BaseResponse<Unit?> = BaseResponse(success = true, data = null)
    }
}

/**
 * Standard error envelope for every non-2xx response. Flat shape —
 * clients branch on [code] for behavior and surface [message] in UI.
 * [details] is optional and used for per-field validation errors
 * (`{ "email": "must be a valid email address" }`, …).
 *
 * Standard codes documented in api-contract.md:
 * `invalid_request`, `unauthenticated`, `email_already_registered`,
 * `rate_limited`.
 *
 * Mirrors the client-side `BaseErrorResponse` in
 * `data/network/common/BaseResponse.kt`.
 */
@Serializable
data class BaseErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null,
)
