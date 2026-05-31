package dev.undercurrent.backend.auth

import dev.undercurrent.backend.accounts.JdbcAccountsRepository
import dev.undercurrent.backend.db.MigrationRunner
import dev.undercurrent.backend.module
import dev.undercurrent.backend.sessions.JdbcSessionsRepository
import dev.undercurrent.backend.sessions.requireAuth
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
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

class SignUpRouteTest : BehaviorSpec({

    fun freshSetup(): Triple<JdbcAccountsRepository, JdbcSessionsRepository, DataSource> {
        val dbName = "tc_${UUID.randomUUID().toString().replace("-", "")}"
        val ds: DataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
        }
        ds.connection.use { MigrationRunner.fromClasspath(it).migrate() }
        return Triple(JdbcAccountsRepository(ds), JdbcSessionsRepository(ds), ds)
    }

    Given("POST /v1/auth/sign-up") {
        When("the request is well-formed") {
            Then("creates an account, issues a session, responds 201 with the documented body") {
                val (accountsRepo, sessionsRepo, _) = freshSetup()

                testApplication {
                    application {
                        module(accountsRepository = accountsRepo, sessionsRepository = sessionsRepo)
                        routing { get("/whoami") { requireAuth { id -> call.respond(id) } } }
                    }

                    val res = client.post("/v1/auth/sign-up") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"displayName":"Phuc","email":"phuc@example.com","password":"hunter2-correct"}""")
                    }
                    res.status shouldBe HttpStatusCode.Created

                    val body = res.bodyAsText()
                    body shouldContain "\"id\":\"acct."
                    body shouldContain "\"displayName\":\"Phuc\""
                    body shouldContain "\"email\":\"phuc@example.com\""
                    body shouldContain "\"token\":\""
                    body shouldContain "\"expiresAtMs\":"
                    // Password must never echo back
                    body shouldNotContain "hunter2"

                    // The session token actually works on a protected route
                    val token = Regex("\"token\":\"([^\"]+)\"").find(body)!!.groupValues[1]
                    val protectedResponse = client.get("/whoami") { header("Authorization", "Bearer $token") }
                    protectedResponse.status shouldBe HttpStatusCode.OK
                    protectedResponse.bodyAsText() shouldStartWith "acct."
                }
            }
        }

        When("the email is malformed") {
            Then("responds 400 invalid_request with field details") {
                val (accountsRepo, sessionsRepo, _) = freshSetup()
                testApplication {
                    application { module(accountsRepository = accountsRepo, sessionsRepository = sessionsRepo) }

                    val res = client.post("/v1/auth/sign-up") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"displayName":"Phuc","email":"not-an-email","password":"hunter2-correct"}""")
                    }
                    res.status shouldBe HttpStatusCode.BadRequest
                    res.bodyAsText() shouldContain "\"code\":\"invalid_request\""
                    res.bodyAsText() shouldContain "\"email\""
                }
            }
        }

        When("the displayName is empty after trim") {
            Then("responds 400 invalid_request") {
                val (accountsRepo, sessionsRepo, _) = freshSetup()
                testApplication {
                    application { module(accountsRepository = accountsRepo, sessionsRepository = sessionsRepo) }

                    val res = client.post("/v1/auth/sign-up") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"displayName":"   ","email":"phuc@example.com","password":"hunter2-correct"}""")
                    }
                    res.status shouldBe HttpStatusCode.BadRequest
                    res.bodyAsText() shouldContain "\"displayName\""
                }
            }
        }

        When("the password is shorter than 8 characters") {
            Then("responds 400 invalid_request") {
                val (accountsRepo, sessionsRepo, _) = freshSetup()
                testApplication {
                    application { module(accountsRepository = accountsRepo, sessionsRepository = sessionsRepo) }

                    val res = client.post("/v1/auth/sign-up") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"displayName":"Phuc","email":"phuc@example.com","password":"short"}""")
                    }
                    res.status shouldBe HttpStatusCode.BadRequest
                    res.bodyAsText() shouldContain "\"password\""
                }
            }
        }

        When("the email is already registered (case-insensitive)") {
            Then("responds 409 email_already_registered without creating a duplicate") {
                val (accountsRepo, sessionsRepo, _) = freshSetup()
                testApplication {
                    application { module(accountsRepository = accountsRepo, sessionsRepository = sessionsRepo) }

                    val first = client.post("/v1/auth/sign-up") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"displayName":"Phuc","email":"phuc@example.com","password":"hunter2-correct"}""")
                    }
                    first.status shouldBe HttpStatusCode.Created

                    val second = client.post("/v1/auth/sign-up") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"displayName":"Phuc2","email":"PHUC@example.COM","password":"different-password"}""")
                    }
                    second.status shouldBe HttpStatusCode.Conflict
                    second.bodyAsText() shouldContain "\"code\":\"email_already_registered\""
                }
            }
        }

        When("the request body is malformed JSON") {
            Then("responds 400 invalid_request") {
                val (accountsRepo, sessionsRepo, _) = freshSetup()
                testApplication {
                    application { module(accountsRepository = accountsRepo, sessionsRepository = sessionsRepo) }

                    val res = client.post("/v1/auth/sign-up") {
                        contentType(ContentType.Application.Json)
                        setBody("""{not even valid json""")
                    }
                    res.status shouldBe HttpStatusCode.BadRequest
                    res.bodyAsText() shouldContain "\"code\":\"invalid_request\""
                }
            }
        }
    }
})
