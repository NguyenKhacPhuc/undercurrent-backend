package dev.undercurrent.backend.db

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class DbTest : BehaviorSpec({
    Given("a DATABASE_URL is missing") {
        When("Db.connect(null) is called") {
            Then("it throws IllegalStateException naming the env var") {
                val ex = shouldThrow<IllegalStateException> { Db.connect(databaseUrl = null) }
                ex.message shouldContain "DATABASE_URL"
            }
        }
    }

    Given("a Postgres-style URL without a jdbc: prefix") {
        When("Db.toJdbcUrl is called") {
            Then("it prepends jdbc:") {
                Db.toJdbcUrl("postgresql://user:pass@host:5432/db") shouldBe
                    "jdbc:postgresql://user:pass@host:5432/db"
            }
        }
    }

    Given("a URL that already starts with jdbc:") {
        When("Db.toJdbcUrl is called") {
            Then("it returns the URL unchanged") {
                Db.toJdbcUrl("jdbc:postgresql://user:pass@host:5432/db") shouldBe
                    "jdbc:postgresql://user:pass@host:5432/db"
            }
        }
    }
})
