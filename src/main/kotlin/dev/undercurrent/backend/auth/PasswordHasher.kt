package dev.undercurrent.backend.auth

import com.password4j.Password

/**
 * Argon2id password hashing, backed by password4j defaults (argon2id variant).
 * Encoded as a PHC-string (`$argon2id$v=19$m=...$t=...$p=...$salt$hash`) so
 * the parameters travel with the hash and a future parameter change can be
 * detected at verify-time without a schema change.
 */
object PasswordHasher {
    fun hash(plain: String): String =
        Password.hash(plain).addRandomSalt().withArgon2().result

    fun verify(plain: String, encoded: String): Boolean = try {
        Password.check(plain, encoded).withArgon2()
    } catch (e: Exception) {
        // Malformed hash, wrong type, etc. — treat as "no match" so callers can
        // surface a clean unauthenticated response without parsing exceptions.
        false
    }
}
