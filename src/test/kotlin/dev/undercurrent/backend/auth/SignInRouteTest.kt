package dev.undercurrent.backend.auth

import dev.undercurrent.backend.accounts.JdbcAccountsRepository
import dev.undercurrent.backend.accounts.NewAccount
import dev.undercurrent.backend.db.MigrationRunner
import dev.undercurrent.backend.module
import dev.undercurrent.backend.sessions.JdbcSessionsRepository
import dev.undercurrent.backend.sessions.requireAuth
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.h2.jdbcx.JdbcDataSource
import java.util.UUID
import javax.sql.DataSource
import java.util.concurrent.atomic.AtomicBoolean

private class RecordingRateLimiter(
    var throttleEverything: Boolean = false,
    val failures: MutableList<String> = mutableListOf(),
    val successes: MutableList<String> = mutableListOf(),
) : SignInRateLimiter {
    override fun shouldThrottle(email: String): Boolean = throttleEverything
    override fun recordFailedAttempt(email: String) { failures.add(email) }
    override fun recordSuccessfulAttempt(email: String) { successes.add(email) }
}

class SignInRouteTest : BehaviorSpec({

    fun freshSetup(): Triple<JdbcAccountsRepository, JdbcSessionsRepository, DataSource> {
        val dbName = "tc_${UUID.randomUUID().toString().replace("-", "")}"
        val ds: DataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
        }
        ds.connection.use { MigrationRunner.fromClasspath(it).migrate() }
        return Triple(JdbcAccountsRepository(ds), JdbcSessionsRepository(ds), ds)
    }

    Given("POST /v1/auth/sign-in") {
        When("the credentials are correct") {
            Then("responds 200 with account + session; token works on a protected route") {
                val (accountsRepo, sessionsRepo, _) = freshSetup()
                val passwordHash = PasswordHasher.hash("hunter2-correct")
                accountsRepo.insert(NewAccount("Phuc", "phuc@example.com", passwordHash))

                testApplication {
                    application {
                        module(accountsRepository = accountsRepo, sessionsRepository = sessionsRepo)
                        routing { get("/whoami") { requireAuth { id -> call.respond(id) } } }
                    }

                    val res = client.post("/v1/auth/sign-in") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"email":"phuc@example.com","password":"hunter2-correct"}""")
                    }
                    res.status shouldBe HttpStatusCode.OK
                    val body = res.bodyAsText()
                    body shouldContain "\"email\":\"phuc@example.com\""
                    body shouldContain "\"token\":\""
                    body shouldNotContain "hunter2"

                    val token = Regex("\"token\":\"([^\"]+)\"").find(body)!!.groupValues[1]
                    val whoami = client.get("/whoami") { header("Authorization", "Bearer $token") }
                    whoami.status shouldBe HttpStatusCode.OK
                }
            }
        }

        When("the email is unknown") {
            Then("responds 401 with the same message as wrong-password") {
                val (accountsRepo, sessionsRepo, _) = freshSetup()
                testApplication {
                    application { module(accountsRepository = accountsRepo, sessionsRepository = sessionsRepo) }
                    val res = client.post("/v1/auth/sign-in") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"email":"never@registered.com","password":"hunter2-correct"}""")
                    }
                    res.status shouldBe HttpStatusCode.Unauthorized
                    res.bodyAsText() shouldContain "\"code\":\"unauthenticated\""
                    res.bodyAsText() shouldContain "Invalid email or password"
                }
            }
        }

        When("the password is wrong") {
            Then("responds 401 with the same message as unknown-email") {
                val (accountsRepo, sessionsRepo, _) = freshSetup()
                accountsRepo.insert(NewAccount("Phuc", "phuc@example.com", PasswordHasher.hash("hunter2-correct")))

                testApplication {
                    application { module(accountsRepository = accountsRepo, sessionsRepository = sessionsRepo) }
                    val res = client.post("/v1/auth/sign-in") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"email":"phuc@example.com","password":"wrong-password"}""")
                    }
                    res.status shouldBe HttpStatusCode.Unauthorized
                    res.bodyAsText() shouldContain "\"code\":\"unauthenticated\""
                    res.bodyAsText() shouldContain "Invalid email or password"
                }
            }
        }

        When("the email is mixed-case") {
            Then("matching is case-insensitive — sign-in succeeds") {
                val (accountsRepo, sessionsRepo, _) = freshSetup()
                accountsRepo.insert(NewAccount("Phuc", "phuc@example.com", PasswordHasher.hash("hunter2-correct")))

                testApplication {
                    application { module(accountsRepository = accountsRepo, sessionsRepository = sessionsRepo) }
                    val res = client.post("/v1/auth/sign-in") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"email":"PHUC@Example.COM","password":"hunter2-correct"}""")
                    }
                    res.status shouldBe HttpStatusCode.OK
                }
            }
        }

        When("the request is missing fields") {
            Then("responds 400 invalid_request") {
                val (accountsRepo, sessionsRepo, _) = freshSetup()
                testApplication {
                    application { module(accountsRepository = accountsRepo, sessionsRepository = sessionsRepo) }
                    val res = client.post("/v1/auth/sign-in") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"email":"","password":""}""")
                    }
                    res.status shouldBe HttpStatusCode.BadRequest
                    res.bodyAsText() shouldContain "\"code\":\"invalid_request\""
                }
            }
        }

        When("the rate limiter is throttling") {
            Then("responds 429 rate_limited and does NOT hit the password check") {
                val (accountsRepo, sessionsRepo, _) = freshSetup()
                val limiter = RecordingRateLimiter(throttleEverything = true)
                testApplication {
                    application {
                        module(
                            accountsRepository = accountsRepo,
                            sessionsRepository = sessionsRepo,
                            signInRateLimiter = limiter,
                        )
                    }
                    val res = client.post("/v1/auth/sign-in") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"email":"phuc@example.com","password":"whatever"}""")
                    }
                    res.status shouldBe HttpStatusCode.TooManyRequests
                    res.bodyAsText() shouldContain "\"code\":\"rate_limited\""
                    limiter.failures shouldContainExactly emptyList() // not recorded — throttled before check
                }
            }
        }

        When("a wrong-password attempt is made") {
            Then("the rate limiter records a failed attempt for that email") {
                val (accountsRepo, sessionsRepo, _) = freshSetup()
                accountsRepo.insert(NewAccount("Phuc", "phuc@example.com", PasswordHasher.hash("hunter2-correct")))
                val limiter = RecordingRateLimiter()

                testApplication {
                    application {
                        module(
                            accountsRepository = accountsRepo,
                            sessionsRepository = sessionsRepo,
                            signInRateLimiter = limiter,
                        )
                    }
                    client.post("/v1/auth/sign-in") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"email":"phuc@example.com","password":"wrong"}""")
                    }
                }

                limiter.failures shouldContainExactly listOf("phuc@example.com")
                limiter.successes shouldContainExactly emptyList()
            }
        }

        When("a correct-password attempt is made") {
            Then("the rate limiter records a success (resets counter for that email)") {
                val (accountsRepo, sessionsRepo, _) = freshSetup()
                accountsRepo.insert(NewAccount("Phuc", "phuc@example.com", PasswordHasher.hash("hunter2-correct")))
                val limiter = RecordingRateLimiter()

                testApplication {
                    application {
                        module(
                            accountsRepository = accountsRepo,
                            sessionsRepository = sessionsRepo,
                            signInRateLimiter = limiter,
                        )
                    }
                    client.post("/v1/auth/sign-in") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"email":"phuc@example.com","password":"hunter2-correct"}""")
                    }
                }

                limiter.successes shouldContainExactly listOf("phuc@example.com")
                limiter.failures shouldContainExactly emptyList()
            }
        }
    }
})
