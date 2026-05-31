package dev.undercurrent.backend.auth

import dev.undercurrent.backend.accounts.JdbcAccountsRepository
import dev.undercurrent.backend.accounts.NewAccount
import dev.undercurrent.backend.db.MigrationRunner
import dev.undercurrent.backend.module
import dev.undercurrent.backend.sessions.JdbcSessionsRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.h2.jdbcx.JdbcDataSource
import java.util.UUID
import javax.sql.DataSource

class MeRouteTest : BehaviorSpec({

    fun freshSetup(): Triple<JdbcAccountsRepository, JdbcSessionsRepository, DataSource> {
        val dbName = "tc_${UUID.randomUUID().toString().replace("-", "")}"
        val ds: DataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
        }
        ds.connection.use { MigrationRunner.fromClasspath(it).migrate() }
        return Triple(JdbcAccountsRepository(ds), JdbcSessionsRepository(ds), ds)
    }

    Given("GET /v1/me") {
        When("a valid session token is presented") {
            Then("responds 200 with the account; password hash is never included") {
                val (accounts, sessions, _) = freshSetup()
                val account = accounts.insert(NewAccount("Phuc", "phuc@example.com", PasswordHasher.hash("hunter2-correct")))
                val token = sessions.issue(account.id).token

                testApplication {
                    application { module(accountsRepository = accounts, sessionsRepository = sessions) }

                    val res = client.get("/v1/me") { header("Authorization", "Bearer $token") }
                    res.status shouldBe HttpStatusCode.OK
                    val body = res.bodyAsText()
                    body shouldContain "\"id\":\"${account.id}\""
                    body shouldContain "\"displayName\":\"Phuc\""
                    body shouldContain "\"email\":\"phuc@example.com\""
                    body shouldNotContain "passwordHash"
                    body shouldNotContain "hunter2"

                    // Cache-Control header keeps identity out of intermediaries
                    val cc = res.headers["Cache-Control"]
                    cc shouldContain "no-store"
                }
            }
        }

        When("no Authorization header is sent") {
            Then("responds 401 unauthenticated") {
                val (accounts, sessions, _) = freshSetup()
                testApplication {
                    application { module(accountsRepository = accounts, sessionsRepository = sessions) }
                    val res = client.get("/v1/me")
                    res.status shouldBe HttpStatusCode.Unauthorized
                    res.bodyAsText() shouldContain "\"code\":\"unauthenticated\""
                }
            }
        }

        When("the bearer token is unknown") {
            Then("responds 401 unauthenticated") {
                val (accounts, sessions, _) = freshSetup()
                testApplication {
                    application { module(accountsRepository = accounts, sessionsRepository = sessions) }
                    val res = client.get("/v1/me") { header("Authorization", "Bearer not-a-real-token") }
                    res.status shouldBe HttpStatusCode.Unauthorized
                }
            }
        }

        When("the session was revoked") {
            Then("responds 401 unauthenticated") {
                val (accounts, sessions, _) = freshSetup()
                val account = accounts.insert(NewAccount("Phuc", "phuc@example.com", PasswordHasher.hash("hunter2-correct")))
                val issued = sessions.issue(account.id)
                sessions.revoke(issued.token)

                testApplication {
                    application { module(accountsRepository = accounts, sessionsRepository = sessions) }
                    val res = client.get("/v1/me") { header("Authorization", "Bearer ${issued.token}") }
                    res.status shouldBe HttpStatusCode.Unauthorized
                }
            }
        }
    }
})
