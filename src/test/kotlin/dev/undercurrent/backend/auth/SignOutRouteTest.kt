package dev.undercurrent.backend.auth

import dev.undercurrent.backend.db.MigrationRunner
import dev.undercurrent.backend.module
import dev.undercurrent.backend.sessions.JdbcSessionsRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.h2.jdbcx.JdbcDataSource
import java.util.UUID
import javax.sql.DataSource

class SignOutRouteTest : BehaviorSpec({

    fun freshSessions(): Pair<JdbcSessionsRepository, DataSource> {
        val dbName = "tc_${UUID.randomUUID().toString().replace("-", "")}"
        val ds: DataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
        }
        ds.connection.use { MigrationRunner.fromClasspath(it).migrate() }
        return JdbcSessionsRepository(ds) to ds
    }

    Given("POST /v1/auth/sign-out") {
        When("a valid bearer token is presented") {
            Then("responds 204 and the token no longer validates") {
                val (sessions, _) = freshSessions()
                val issued = sessions.issue("acct.abc")

                testApplication {
                    application { module(sessionsRepository = sessions) }
                    val res = client.post("/v1/auth/sign-out") {
                        header("Authorization", "Bearer ${issued.token}")
                    }
                    res.status shouldBe HttpStatusCode.NoContent
                }

                sessions.validate(issued.token) shouldBe null
            }
        }

        When("no Authorization header is sent") {
            Then("responds 204 (idempotent — no information leak)") {
                val (sessions, _) = freshSessions()
                testApplication {
                    application { module(sessionsRepository = sessions) }
                    val res = client.post("/v1/auth/sign-out")
                    res.status shouldBe HttpStatusCode.NoContent
                }
            }
        }

        When("an unknown bearer token is sent") {
            Then("responds 204 (idempotent — no information leak)") {
                val (sessions, _) = freshSessions()
                testApplication {
                    application { module(sessionsRepository = sessions) }
                    val res = client.post("/v1/auth/sign-out") {
                        header("Authorization", "Bearer never-existed")
                    }
                    res.status shouldBe HttpStatusCode.NoContent
                }
            }
        }

        When("the bearer token was already revoked") {
            Then("responds 204 (still idempotent)") {
                val (sessions, _) = freshSessions()
                val issued = sessions.issue("acct.abc")
                sessions.revoke(issued.token)

                testApplication {
                    application { module(sessionsRepository = sessions) }
                    val res = client.post("/v1/auth/sign-out") {
                        header("Authorization", "Bearer ${issued.token}")
                    }
                    res.status shouldBe HttpStatusCode.NoContent
                }
            }
        }

        When("an account has two sessions and only one is signed out") {
            Then("the other session still validates") {
                val (sessions, _) = freshSessions()
                val first = sessions.issue("acct.abc")
                val second = sessions.issue("acct.abc")

                testApplication {
                    application { module(sessionsRepository = sessions) }
                    client.post("/v1/auth/sign-out") {
                        header("Authorization", "Bearer ${first.token}")
                    }
                }

                sessions.validate(first.token) shouldBe null
                sessions.validate(second.token).shouldNotBeNull()
            }
        }
    }
})
