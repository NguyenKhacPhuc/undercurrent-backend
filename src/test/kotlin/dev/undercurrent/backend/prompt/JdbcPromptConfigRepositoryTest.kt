package dev.undercurrent.backend.prompt

import dev.undercurrent.backend.db.MigrationRunner
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import org.h2.jdbcx.JdbcDataSource
import java.util.UUID
import javax.sql.DataSource

class JdbcPromptConfigRepositoryTest : BehaviorSpec({

    fun freshDataSource(): DataSource {
        val dbName = "tc_${UUID.randomUUID().toString().replace("-", "")}"
        val ds = JdbcDataSource()
        ds.setURL("jdbc:h2:mem:$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
        ds.connection.use { conn -> MigrationRunner.fromClasspath(conn).migrate() }
        return ds
    }

    Given("a fresh DB with the prompt_config table") {
        When("nothing is seeded yet") {
            Then("get() returns null (the route turns this into 'temporarily unavailable')") {
                JdbcPromptConfigRepository(freshDataSource()).get().shouldBeNull()
            }
        }

        When("the config is seeded then read") {
            Then("the preamble, a revision marker, and an updated-at come back") {
                val repo = JdbcPromptConfigRepository(freshDataSource())
                repo.seedIfEmpty("You are Undercurrent's assistant…")

                val config = repo.get()
                config.shouldNotBeNull()
                config.preamble shouldBe "You are Undercurrent's assistant…"
                config.revision shouldStartWith "rev."
                config.updatedAtMs shouldNotBe 0L
            }
        }

        When("seedIfEmpty runs again over an already-seeded config") {
            Then("it is a no-op — the original preamble is preserved") {
                val repo = JdbcPromptConfigRepository(freshDataSource())
                repo.seedIfEmpty("first prompt")
                repo.seedIfEmpty("second prompt")

                repo.get()!!.preamble shouldBe "first prompt"
            }
        }
    }

    Given("an already-seeded config") {
        When("update() replaces the preamble") {
            Then("the new text, a changed revision, and a fresh updated-at are stored and returned") {
                val repo = JdbcPromptConfigRepository(freshDataSource())
                repo.seedIfEmpty("the original prompt — long enough to pass the guard")
                val before = repo.get()!!

                val returned = repo.update("a brand new operator-set base prompt, plenty long")

                returned.preamble shouldBe "a brand new operator-set base prompt, plenty long"
                returned.revision shouldNotBe before.revision
                returned.revision shouldBe computeRevision("a brand new operator-set base prompt, plenty long")

                // The change is persisted: a fresh get() reflects it.
                val after = repo.get()!!
                after.preamble shouldBe "a brand new operator-set base prompt, plenty long"
                after.revision shouldBe returned.revision
                after.updatedAtMs shouldBe returned.updatedAtMs
            }
        }
    }

    Given("the revision marker") {
        Then("it is derived from the prompt text — same text same revision, changed text changed revision") {
            computeRevision("alpha") shouldBe computeRevision("alpha")
            computeRevision("alpha") shouldNotBe computeRevision("beta")
        }
    }
})
