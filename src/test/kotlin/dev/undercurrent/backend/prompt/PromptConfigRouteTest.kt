package dev.undercurrent.backend.prompt

import dev.undercurrent.backend.accounts.JdbcAccountsRepository
import dev.undercurrent.backend.accounts.NewAccount
import dev.undercurrent.backend.auth.PasswordHasher
import dev.undercurrent.backend.db.MigrationRunner
import dev.undercurrent.backend.module
import dev.undercurrent.backend.sessions.JdbcSessionsRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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

    val operatorSecret = "s3cr3t-operator-value"
    val validPreamble = "You are Undercurrent — a fresh operator-authored base prompt."

    Given("PUT /v1/prompt-config") {
        When("a request carries the configured operator secret and a valid preamble") {
            Then("responds 200, and a subsequent get() returns the new text with a changed revision") {
                val s = freshSetup()
                s.prompts.seedIfEmpty("the original seeded prompt — long enough to pass")
                val originalRevision = s.prompts.get()!!.revision

                testApplication {
                    application {
                        module(
                            accountsRepository = s.accounts,
                            sessionsRepository = s.sessions,
                            promptConfigRepository = s.prompts,
                            operatorSecret = operatorSecret,
                        )
                    }
                    val res = client.put("/v1/prompt-config") {
                        header("X-Operator-Secret", operatorSecret)
                        contentType(ContentType.Application.Json)
                        setBody("""{"preamble":"$validPreamble"}""")
                    }
                    res.status shouldBe HttpStatusCode.OK
                    val body = res.bodyAsText()
                    body shouldContain "\"revision\":\"rev."
                    body shouldContain "\"updatedAtMs\":"
                }

                val after = s.prompts.get()!!
                after.preamble shouldBe validPreamble
                after.revision shouldNotBe originalRevision
            }
        }

        When("the operator secret is wrong or missing") {
            Then("responds 403 forbidden and the prompt is unchanged") {
                val s = freshSetup()
                s.prompts.seedIfEmpty("the original seeded prompt — long enough to pass")

                testApplication {
                    application {
                        module(
                            accountsRepository = s.accounts,
                            sessionsRepository = s.sessions,
                            promptConfigRepository = s.prompts,
                            operatorSecret = operatorSecret,
                        )
                    }
                    val wrong = client.put("/v1/prompt-config") {
                        header("X-Operator-Secret", "not-the-secret")
                        contentType(ContentType.Application.Json)
                        setBody("""{"preamble":"$validPreamble"}""")
                    }
                    wrong.status shouldBe HttpStatusCode.Forbidden
                    wrong.bodyAsText() shouldContain "\"code\":\"forbidden\""

                    val missing = client.put("/v1/prompt-config") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"preamble":"$validPreamble"}""")
                    }
                    missing.status shouldBe HttpStatusCode.Forbidden
                }

                s.prompts.get()!!.preamble shouldBe "the original seeded prompt — long enough to pass"
            }
        }

        When("the preamble is empty, whitespace, or too short") {
            Then("responds 400 invalid_request and the prompt is unchanged") {
                val s = freshSetup()
                s.prompts.seedIfEmpty("the original seeded prompt — long enough to pass")

                testApplication {
                    application {
                        module(
                            accountsRepository = s.accounts,
                            sessionsRepository = s.sessions,
                            promptConfigRepository = s.prompts,
                            operatorSecret = operatorSecret,
                        )
                    }
                    listOf("", "   ", "too short").forEach { bad ->
                        val res = client.put("/v1/prompt-config") {
                            header("X-Operator-Secret", operatorSecret)
                            contentType(ContentType.Application.Json)
                            setBody("""{"preamble":"$bad"}""")
                        }
                        res.status shouldBe HttpStatusCode.BadRequest
                        res.bodyAsText() shouldContain "\"code\":\"invalid_request\""
                    }
                }

                s.prompts.get()!!.preamble shouldBe "the original seeded prompt — long enough to pass"
            }
        }

        When("no operator secret is configured on the server (env var unset)") {
            Then("responds 403 fail-closed even with a correct-looking request, prompt unchanged") {
                val s = freshSetup()
                s.prompts.seedIfEmpty("the original seeded prompt — long enough to pass")

                testApplication {
                    application {
                        module(
                            accountsRepository = s.accounts,
                            sessionsRepository = s.sessions,
                            promptConfigRepository = s.prompts,
                            operatorSecret = null,
                        )
                    }
                    val res = client.put("/v1/prompt-config") {
                        header("X-Operator-Secret", "anything")
                        contentType(ContentType.Application.Json)
                        setBody("""{"preamble":"$validPreamble"}""")
                    }
                    res.status shouldBe HttpStatusCode.Forbidden
                }

                s.prompts.get()!!.preamble shouldBe "the original seeded prompt — long enough to pass"
            }
        }
    }
})
