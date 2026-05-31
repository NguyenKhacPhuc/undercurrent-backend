package dev.undercurrent.backend.auth

import dev.undercurrent.backend.accounts.JdbcAccountsRepository
import dev.undercurrent.backend.accounts.NewAccount
import dev.undercurrent.backend.db.MigrationRunner
import dev.undercurrent.backend.module
import dev.undercurrent.backend.sessions.JdbcSessionsRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.h2.jdbcx.JdbcDataSource
import java.util.UUID
import javax.sql.DataSource

class SignInRateLimitE2ETest : BehaviorSpec({

    fun freshSetup(): Triple<JdbcAccountsRepository, JdbcSessionsRepository, DataSource> {
        val dbName = "tc_${UUID.randomUUID().toString().replace("-", "")}"
        val ds: DataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
        }
        ds.connection.use { MigrationRunner.fromClasspath(it).migrate() }
        return Triple(JdbcAccountsRepository(ds), JdbcSessionsRepository(ds), ds)
    }

    Given("the live rate limiter wired into module()") {
        When("11 wrong-password attempts are made against the same email") {
            Then("the first 10 return 401, the 11th returns 429 rate_limited") {
                val (accounts, sessions, _) = freshSetup()
                accounts.insert(NewAccount("Phuc", "phuc@example.com", PasswordHasher.hash("hunter2-correct")))
                val limiter = InMemorySignInRateLimiter()

                testApplication {
                    application {
                        module(
                            accountsRepository = accounts,
                            sessionsRepository = sessions,
                            signInRateLimiter = limiter,
                        )
                    }

                    repeat(10) { i ->
                        val res = client.post("/v1/auth/sign-in") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"email":"phuc@example.com","password":"wrong-$i"}""")
                        }
                        res.status shouldBe HttpStatusCode.Unauthorized
                    }

                    val throttled = client.post("/v1/auth/sign-in") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"email":"phuc@example.com","password":"wrong-final"}""")
                    }
                    throttled.status shouldBe HttpStatusCode.TooManyRequests
                    throttled.bodyAsText() shouldContain "\"code\":\"rate_limited\""
                }
            }
        }

        When("a wrong-password loop is followed by the correct password") {
            Then("the correct password clears the counter so the next wrong attempt is unaffected") {
                val (accounts, sessions, _) = freshSetup()
                accounts.insert(NewAccount("Phuc", "phuc@example.com", PasswordHasher.hash("hunter2-correct")))
                val limiter = InMemorySignInRateLimiter()

                testApplication {
                    application {
                        module(
                            accountsRepository = accounts,
                            sessionsRepository = sessions,
                            signInRateLimiter = limiter,
                        )
                    }

                    // 5 wrong attempts
                    repeat(5) {
                        client.post("/v1/auth/sign-in") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"email":"phuc@example.com","password":"wrong"}""")
                        }
                    }

                    // Correct attempt resets
                    val ok = client.post("/v1/auth/sign-in") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"email":"phuc@example.com","password":"hunter2-correct"}""")
                    }
                    ok.status shouldBe HttpStatusCode.OK

                    // 10 more wrong attempts allowed (only just-now-failed ones count)
                    repeat(10) {
                        val res = client.post("/v1/auth/sign-in") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"email":"phuc@example.com","password":"wrong-after-success"}""")
                        }
                        res.status shouldBe HttpStatusCode.Unauthorized
                    }
                }
            }
        }
    }
})
