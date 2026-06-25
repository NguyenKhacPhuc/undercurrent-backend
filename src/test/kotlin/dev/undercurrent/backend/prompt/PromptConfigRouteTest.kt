package dev.undercurrent.backend.prompt

import dev.undercurrent.backend.accounts.JdbcAccountsRepository
import dev.undercurrent.backend.accounts.NewAccount
import dev.undercurrent.backend.auth.PasswordHasher
import dev.undercurrent.backend.db.MigrationRunner
import dev.undercurrent.backend.module
import dev.undercurrent.backend.sessions.JdbcSessionsRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.h2.jdbcx.JdbcDataSource
import java.util.UUID
import javax.sql.DataSource

class PromptConfigRouteTest : BehaviorSpec({

    data class Setup(
        val accounts: JdbcAccountsRepository,
        val sessions: JdbcSessionsRepository,
        val prompts: JdbcPromptConfigRepository,
    )

    fun freshSetup(): Setup {
        val dbName = "tc_${UUID.randomUUID().toString().replace("-", "")}"
        val ds: DataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
        }
        ds.connection.use { MigrationRunner.fromClasspath(it).migrate() }
        return Setup(JdbcAccountsRepository(ds), JdbcSessionsRepository(ds), JdbcPromptConfigRepository(ds))
    }

    fun Setup.tokenFor(name: String = "Phuc"): String {
        val account = accounts.insert(NewAccount(name, "$name@example.com", PasswordHasher.hash("hunter2-correct")))
        return sessions.issue(account.id).token
    }

    Given("GET /v1/prompt-config") {
        When("a signed-in client fetches a seeded config") {
            Then("responds 200 with the preamble, revision, and updated-at") {
                val s = freshSetup()
                s.prompts.seedIfEmpty("You are Undercurrent's assistant…")
                val token = s.tokenFor()

                testApplication {
                    application {
                        module(
                            accountsRepository = s.accounts,
                            sessionsRepository = s.sessions,
                            promptConfigRepository = s.prompts,
                        )
                    }
                    val res = client.get("/v1/prompt-config") { header("Authorization", "Bearer $token") }
                    res.status shouldBe HttpStatusCode.OK
                    val body = res.bodyAsText()
                    body shouldContain "You are Undercurrent's assistant"
                    body shouldContain "\"revision\":\"rev."
                    body shouldContain "\"updatedAtMs\":"
                }
            }
        }

        When("no Authorization header is sent") {
            Then("responds 401 unauthenticated") {
                val s = freshSetup()
                s.prompts.seedIfEmpty("seeded")
                testApplication {
                    application {
                        module(
                            accountsRepository = s.accounts,
                            sessionsRepository = s.sessions,
                            promptConfigRepository = s.prompts,
                        )
                    }
                    val res = client.get("/v1/prompt-config")
                    res.status shouldBe HttpStatusCode.Unauthorized
                    res.bodyAsText() shouldContain "\"code\":\"unauthenticated\""
                }
            }
        }

        When("the config has not been seeded") {
            Then("responds 503 unavailable rather than an empty prompt") {
                val s = freshSetup()
                val token = s.tokenFor()
                testApplication {
                    application {
                        module(
                            accountsRepository = s.accounts,
                            sessionsRepository = s.sessions,
                            promptConfigRepository = s.prompts,
                        )
                    }
                    val res = client.get("/v1/prompt-config") { header("Authorization", "Bearer $token") }
                    res.status shouldBe HttpStatusCode.ServiceUnavailable
                    res.bodyAsText() shouldContain "\"code\":\"unavailable\""
                }
            }
        }
    }
})
