package dev.undercurrent.backend.auth

/**
 * Hook the sign-in handler calls so a future story (09) can plug in
 * brute-force protection without re-opening the sign-in route. v1
 * ships [NoopSignInRateLimiter]; story 09 swaps the binding in
 * `Application.main()` for the real per-email/window implementation.
 */
interface SignInRateLimiter {
    /** True if subsequent attempts for [email] should be rejected with 429. */
    fun shouldThrottle(email: String): Boolean

    /** Called after a failed credential check so the limiter can count it. */
    fun recordFailedAttempt(email: String)

    /** Called after a successful sign-in so the limiter can reset counters. */
    fun recordSuccessfulAttempt(email: String)
}

/** No-op implementation — never throttles. Default until story 09 lands. */
object NoopSignInRateLimiter : SignInRateLimiter {
    override fun shouldThrottle(email: String): Boolean = false
    override fun recordFailedAttempt(email: String) = Unit
    override fun recordSuccessfulAttempt(email: String) = Unit
}
