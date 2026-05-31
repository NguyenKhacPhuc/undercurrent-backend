package dev.undercurrent.backend.sessions

import dev.undercurrent.backend.db.MigrationRunner
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.h2.jdbcx.JdbcDataSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

private class MutableClock(initialMs: Long) : Clock() {
    private var now: Instant = Instant.ofEpochMilli(initialMs)
    fun advanceMs(delta: Long) { now = now.plusMillis(delta) }
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId) = this
    override fun instant(): Instant = now
}

class JdbcSessionsRepositoryTest : BehaviorSpec({

    fun freshDataSource(): DataSource {
        val dbName = "tc_${UUID.randomUUID().toString().replace("-", "")}"
        val ds = JdbcDataSource()
        ds.setURL("jdbc:h2:mem:$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
        ds.connection.use { conn -> MigrationRunner.fromClasspath(conn).migrate() }
        return ds
    }

    Given("a fresh DB with the sessions table") {
        When("issue() is called for an account") {
            Then("returns a raw token + accountId + expiresAtMs (~30 days out)") {
                val nowMs = 1_700_000_000_000L
                val repo = JdbcSessionsRepository(freshDataSource(), MutableClock(nowMs))

                val issued = repo.issue(accountId = "acct.abc")
                issued.accountId shouldBe "acct.abc"
                issued.token.length shouldBe 43 // 256 bits base64url no padding
                issued.expiresAtMs shouldBe nowMs + SessionsRepository.DEFAULT_TTL_MS
            }
        }

        When("validate() is called with a token that was just issued") {
            Then("returns the corresponding ValidatedSession") {
                val nowMs = 1_700_000_000_000L
                val repo = JdbcSessionsRepository(freshDataSource(), MutableClock(nowMs))
                val issued = repo.issue("acct.abc")

                val validated = repo.validate(issued.token)
                validated.shouldNotBeNull()
                validated.accountId shouldBe "acct.abc"
                validated.expiresAtMs shouldBe issued.expiresAtMs
            }
        }

        When("validate() is called with an unknown token") {
            Then("returns null") {
                val repo = JdbcSessionsRepository(freshDataSource(), MutableClock(1L))
                repo.validate("definitely-not-a-real-token") shouldBe null
            }
        }

        When("validate() is called after the session expired") {
            Then("returns null") {
                val clock = MutableClock(1_700_000_000_000L)
                val repo = JdbcSessionsRepository(freshDataSource(), clock)
                val issued = repo.issue("acct.abc", ttlMs = 60_000L) // 1 minute
                clock.advanceMs(120_000L) // 2 minutes — past expiry

                repo.validate(issued.token) shouldBe null
            }
        }

        When("validate() is called after revoke()") {
            Then("returns null") {
                val repo = JdbcSessionsRepository(freshDataSource(), MutableClock(1L))
                val issued = repo.issue("acct.abc")
                repo.revoke(issued.token)
                repo.validate(issued.token) shouldBe null
            }
        }

        When("revoke() is called with an unknown token") {
            Then("it does not throw (idempotent)") {
                val repo = JdbcSessionsRepository(freshDataSource(), MutableClock(1L))
                repo.revoke("never-existed") // should just return
            }
        }

        When("an account has two issued sessions and only one is revoked") {
            Then("the other still validates") {
                val repo = JdbcSessionsRepository(freshDataSource(), MutableClock(1L))
                val s1 = repo.issue("acct.abc")
                val s2 = repo.issue("acct.abc")
                s1.token shouldNotBe s2.token

                repo.revoke(s1.token)

                repo.validate(s1.token) shouldBe null
                repo.validate(s2.token).shouldNotBeNull().accountId shouldBe "acct.abc"
            }
        }
    }
})
