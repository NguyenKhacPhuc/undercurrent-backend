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

    Given("a Postgres URL with no userinfo") {
        When("Db.toJdbcUrl is called") {
            Then("it just prepends jdbc:") {
                Db.toJdbcUrl("postgresql://host:5432/db") shouldBe
                    "jdbc:postgresql://host:5432/db"
            }
        }
    }

    Given("a libpq-style Postgres URL with user:password@") {
        When("Db.toJdbcUrl is called") {
            Then("the userinfo is moved into JDBC query parameters (so the JDBC driver doesn't treat user:pass@host as the hostname)") {
                Db.toJdbcUrl("postgresql://alice:s3cret@host:5432/db") shouldBe
                    "jdbc:postgresql://host:5432/db?user=alice&password=s3cret"
            }
        }
    }

    Given("a libpq-style URL that already has query params") {
        When("Db.toJdbcUrl is called") {
            Then("user + password are appended to the existing query string") {
                Db.toJdbcUrl("postgresql://alice:s3cret@host:5432/db?sslmode=require") shouldBe
                    "jdbc:postgresql://host:5432/db?sslmode=require&user=alice&password=s3cret"
            }
        }
    }

    Given("a libpq-style URL with reserved characters in the password") {
        When("Db.toJdbcUrl is called") {
            Then("the password is URL-encoded in the query string") {
                Db.toJdbcUrl("postgresql://alice:p%40ss%26word@host:5432/db") shouldBe
                    "jdbc:postgresql://host:5432/db?user=alice&password=p%40ss%26word"
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
