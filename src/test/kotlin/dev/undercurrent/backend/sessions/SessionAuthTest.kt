package dev.undercurrent.backend.sessions

import dev.undercurrent.backend.db.MigrationRunner
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.application.install
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.h2.jdbcx.JdbcDataSource
import java.util.UUID
import javax.sql.DataSource

class SessionAuthTest : BehaviorSpec({

    fun freshRepo(): SessionsRepository {
        val dbName = "tc_${UUID.randomUUID().toString().replace("-", "")}"
        val ds: DataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
        }
        ds.connection.use { MigrationRunner.fromClasspath(it).migrate() }
        return JdbcSessionsRepository(ds)
    }

    Given("a route protected by requireAuth") {
        When("no Authorization header is sent") {
            Then("responds 401 with the unauthenticated error envelope") {
                val repo = freshRepo()
                testApplication {
                    application {
                        install(ContentNegotiation) { json() }
                        installSessionAuth(repo)
                        routing { get("/protected") { requireAuth { id -> call.respond("ok:$id") } } }
                    }

                    val res = client.get("/protected")
                    res.status shouldBe HttpStatusCode.Unauthorized
                    res.bodyAsText() shouldContain "\"code\":\"unauthenticated\""
                }
            }
        }

        When("a malformed Authorization header is sent") {
            Then("responds 401") {
                val repo = freshRepo()
                testApplication {
                    application {
                        install(ContentNegotiation) { json() }
                        installSessionAuth(repo)
                        routing { get("/protected") { requireAuth { id -> call.respond("ok:$id") } } }
                    }

                    val res = client.get("/protected") { header("Authorization", "Basic abc") }
                    res.status shouldBe HttpStatusCode.Unauthorized
                }
            }
        }

        When("an unknown bearer token is sent") {
            Then("responds 401") {
                val repo = freshRepo()
                testApplication {
                    application {
                        install(ContentNegotiation) { json() }
                        installSessionAuth(repo)
                        routing { get("/protected") { requireAuth { id -> call.respond("ok:$id") } } }
                    }

                    val res = client.get("/protected") { header("Authorization", "Bearer not-a-real-token") }
                    res.status shouldBe HttpStatusCode.Unauthorized
                }
            }
        }

        When("a valid bearer token is sent") {
            Then("responds 200 and the handler sees the account id") {
                val repo = freshRepo()
                val issued = repo.issue("acct.abc")

                testApplication {
                    application {
                        install(ContentNegotiation) { json() }
                        installSessionAuth(repo)
                        routing { get("/protected") { requireAuth { id -> call.respond("ok:$id") } } }
                    }

                    val res = client.get("/protected") { header("Authorization", "Bearer ${issued.token}") }
                    res.status shouldBe HttpStatusCode.OK
                    res.bodyAsText() shouldBe "ok:acct.abc"
                }
            }
        }

        When("a previously-valid token is revoked") {
            Then("subsequent requests with it respond 401") {
                val repo = freshRepo()
                val issued = repo.issue("acct.abc")
                repo.revoke(issued.token)

                testApplication {
                    application {
                        install(ContentNegotiation) { json() }
                        installSessionAuth(repo)
                        routing { get("/protected") { requireAuth { id -> call.respond("ok:$id") } } }
                    }

                    val res = client.get("/protected") { header("Authorization", "Bearer ${issued.token}") }
                    res.status shouldBe HttpStatusCode.Unauthorized
                }
            }
        }
    }
})
