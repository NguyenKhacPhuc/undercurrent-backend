package dev.undercurrent.backend.auth

import kotlinx.serialization.Serializable

@Serializable
data class AccountDto(
    val id: String,
    val displayName: String,
    val email: String,
    val createdAtMs: Long,
)

@Serializable
data class SessionDto(
    val token: String,
    val expiresAtMs: Long,
)

@Serializable
data class AuthResponse(
    val account: AccountDto,
    val session: SessionDto,
)
