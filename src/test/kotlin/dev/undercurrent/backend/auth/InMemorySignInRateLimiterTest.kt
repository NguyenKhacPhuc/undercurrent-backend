package dev.undercurrent.backend.auth

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private class MutableClock(initialMs: Long) : Clock() {
    private var now: Instant = Instant.ofEpochMilli(initialMs)
    fun advanceMs(delta: Long) { now = now.plusMillis(delta) }
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId) = this
    override fun instant(): Instant = now
}

class InMemorySignInRateLimiterTest : BehaviorSpec({

    Given("a fresh limiter (10 failures / 15 min window)") {
        When("no failures have been recorded") {
            Then("shouldThrottle returns false") {
                val limiter = InMemorySignInRateLimiter(MutableClock(1_700_000_000_000L))
                limiter.shouldThrottle("phuc@example.com") shouldBe false
            }
        }

        When("9 failures have been recorded within the window") {
            Then("shouldThrottle still returns false") {
                val limiter = InMemorySignInRateLimiter(MutableClock(1_700_000_000_000L))
                repeat(9) { limiter.recordFailedAttempt("phuc@example.com") }
                limiter.shouldThrottle("phuc@example.com") shouldBe false
            }
        }

        When("10 failures have been recorded within the window") {
            Then("shouldThrottle returns true (the 11th attempt is blocked)") {
                val limiter = InMemorySignInRateLimiter(MutableClock(1_700_000_000_000L))
                repeat(10) { limiter.recordFailedAttempt("phuc@example.com") }
                limiter.shouldThrottle("phuc@example.com") shouldBe true
            }
        }

        When("the window elapses without further failures") {
            Then("shouldThrottle returns false again (sliding window)") {
                val clock = MutableClock(1_700_000_000_000L)
                val limiter = InMemorySignInRateLimiter(clock)
                repeat(10) { limiter.recordFailedAttempt("phuc@example.com") }
                limiter.shouldThrottle("phuc@example.com") shouldBe true

                clock.advanceMs(15 * 60 * 1000L + 1) // 15min + 1ms
                limiter.shouldThrottle("phuc@example.com") shouldBe false
            }
        }

        When("recordSuccessfulAttempt is called after failures") {
            Then("the failure counter resets immediately") {
                val limiter = InMemorySignInRateLimiter(MutableClock(1_700_000_000_000L))
                repeat(10) { limiter.recordFailedAttempt("phuc@example.com") }
                limiter.shouldThrottle("phuc@example.com") shouldBe true

                limiter.recordSuccessfulAttempt("phuc@example.com")
                limiter.shouldThrottle("phuc@example.com") shouldBe false
            }
        }

        When("two different emails each accumulate failures") {
            Then("they are tracked independently — throttling one does not affect the other") {
                val limiter = InMemorySignInRateLimiter(MutableClock(1_700_000_000_000L))
                repeat(10) { limiter.recordFailedAttempt("phuc@example.com") }
                repeat(3) { limiter.recordFailedAttempt("other@example.com") }

                limiter.shouldThrottle("phuc@example.com") shouldBe true
                limiter.shouldThrottle("other@example.com") shouldBe false
            }
        }

        When("old failures fall outside the window but recent ones do not") {
            Then("only the recent ones count (true sliding window)") {
                val clock = MutableClock(1_700_000_000_000L)
                val limiter = InMemorySignInRateLimiter(clock)

                // 5 failures at t=0
                repeat(5) { limiter.recordFailedAttempt("phuc@example.com") }

                // Jump forward 16 minutes — old failures expire
                clock.advanceMs(16 * 60 * 1000L)

                // 5 more failures at t=16min — only these should count
                repeat(5) { limiter.recordFailedAttempt("phuc@example.com") }

                // Only 5 recent failures — well under the 10 threshold
                limiter.shouldThrottle("phuc@example.com") shouldBe false
            }
        }
    }

    Given("a custom-threshold limiter (3 failures / 60s)") {
        When("3 failures land within 60s") {
            Then("shouldThrottle returns true") {
                val limiter = InMemorySignInRateLimiter(
                    clock = MutableClock(1L),
                    maxFailures = 3,
                    windowMs = 60_000L,
                )
                repeat(3) { limiter.recordFailedAttempt("x@y.com") }
                limiter.shouldThrottle("x@y.com") shouldBe true
            }
        }
    }
})
