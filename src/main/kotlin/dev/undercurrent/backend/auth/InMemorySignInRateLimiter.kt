package dev.undercurrent.backend.auth

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-process sliding-window rate limiter for sign-in attempts.
 *
 * Acceptable for the v1 single-Railway-dyno deploy: state is in-memory,
 * lost on restart. Scaling out to multiple dynos requires moving the
 * counter to Postgres (a `sign_in_failures` table) — out of scope for v1.
 *
 * Per Inception D6: defaults are 10 failures / 15-minute sliding window
 * per email. Per-IP limiting is intentionally NOT here — see Inception's
 * `out-of-scope.md`.
 */
class InMemorySignInRateLimiter(
    private val clock: Clock = Clock.systemUTC(),
    private val maxFailures: Int = DEFAULT_MAX_FAILURES,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
) : SignInRateLimiter {

    private val failures = ConcurrentHashMap<String, MutableList<Long>>()

    override fun shouldThrottle(email: String): Boolean {
        val timestamps = failures[email] ?: return false
        val cutoffMs = clock.millis() - windowMs
        synchronized(timestamps) {
            timestamps.removeAll { it < cutoffMs }
            if (timestamps.isEmpty()) failures.remove(email, timestamps)
            return timestamps.size >= maxFailures
        }
    }

    override fun recordFailedAttempt(email: String) {
        val nowMs = clock.millis()
        val list = failures.computeIfAbsent(email) { mutableListOf() }
        synchronized(list) { list.add(nowMs) }
    }

    override fun recordSuccessfulAttempt(email: String) {
        failures.remove(email)
    }

    companion object {
        // Inception D6: 10 failures per email per 15 minutes.
        const val DEFAULT_MAX_FAILURES: Int = 10
        const val DEFAULT_WINDOW_MS: Long = 15L * 60L * 1000L
    }
}
