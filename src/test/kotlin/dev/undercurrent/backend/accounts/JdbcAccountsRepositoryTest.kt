package dev.undercurrent.backend.accounts

import dev.undercurrent.backend.db.MigrationRunner
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.h2.jdbcx.JdbcDataSource
import java.util.UUID
import javax.sql.DataSource

class JdbcAccountsRepositoryTest : BehaviorSpec({

    fun freshDataSource(): DataSource {
        val dbName = "tc_${UUID.randomUUID().toString().replace("-", "")}"
        val ds = JdbcDataSource()
        ds.setURL("jdbc:h2:mem:$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
        ds.connection.use { conn -> MigrationRunner.fromClasspath(conn).migrate() }
        return ds
    }

    Given("a fresh DB with the accounts table") {
        When("an account is inserted and looked up by id") {
            Then("the same display name, email, password hash, and createdAt come back") {
                val repo = JdbcAccountsRepository(freshDataSource())
                val account = repo.insert(
                    NewAccount(
                        displayName = "Phuc",
                        email = "phuc@example.com",
                        passwordHash = "argon2:fakehash",
                    ),
                )

                account.id shouldStartWith "acct."

                val found = repo.findById(account.id)
                found.shouldNotBeNull()
                found.email shouldBe "phuc@example.com"
                found.displayName shouldBe "Phuc"
                found.passwordHash shouldBe "argon2:fakehash"
                found.createdAtMs shouldBe account.createdAtMs
            }
        }

        When("an account is inserted with a mixed-case email") {
            Then("the stored email is lowercased and lookup is case-insensitive") {
                val repo = JdbcAccountsRepository(freshDataSource())
                val account = repo.insert(
                    NewAccount(displayName = "Phuc", email = "Phuc@Example.COM", passwordHash = "h"),
                )
                account.email shouldBe "phuc@example.com"

                repo.findByEmail("phuc@example.com")?.id shouldBe account.id
                repo.findByEmail("PHUC@example.COM")?.id shouldBe account.id
            }
        }

        When("findById is called with an unknown id") {
            Then("it returns null") {
                val repo = JdbcAccountsRepository(freshDataSource())
                repo.findById("acct.doesnotexist") shouldBe null
            }
        }

        When("findByEmail is called with an unknown email") {
            Then("it returns null") {
                val repo = JdbcAccountsRepository(freshDataSource())
                repo.findByEmail("unknown@example.com") shouldBe null
            }
        }

        When("a second account is inserted with the same email (case-insensitive)") {
            Then("EmailAlreadyRegisteredException is thrown") {
                val repo = JdbcAccountsRepository(freshDataSource())
                repo.insert(NewAccount("Phuc", "phuc@example.com", "h1"))
                shouldThrow<EmailAlreadyRegisteredException> {
                    repo.insert(NewAccount("Phuc2", "Phuc@Example.COM", "h2"))
                }
            }
        }
    }
})
