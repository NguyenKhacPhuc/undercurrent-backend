package dev.undercurrent.backend.sessions

import java.security.MessageDigest
import java.security.SecureRandom

data class IssuedSession(
    val token: String,
    val accountId: String,
    val expiresAtMs: Long,
)

data class ValidatedSession(
    val accountId: String,
    val expiresAtMs: Long,
)

interface SessionsRepository {
    fun issue(accountId: String, ttlMs: Long = DEFAULT_TTL_MS): IssuedSession
    fun validate(token: String): ValidatedSession?
    fun revoke(token: String)

    companion object {
        // 30 days from issuance, per Inception D3.
        const val DEFAULT_TTL_MS: Long = 30L * 24L * 60L * 60L * 1000L
    }
}

internal object SessionTokens {
    private val random = SecureRandom()

    /** 256 random bits, base64url-encoded (~43 chars, no padding). */
    fun generateRawToken(): String {
        val buf = ByteArray(32)
        random.nextBytes(buf)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(buf)
    }

    /** SHA-256 of the token's UTF-8 bytes, hex-encoded (64 chars). Stored at rest. */
    fun hash(token: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(token.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
