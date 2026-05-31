package dev.undercurrent.backend

import dev.undercurrent.backend.db.MigrationRunner
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.sql.DriverManager
import java.util.UUID

class ApplicationStartupTest : BehaviorSpec({

    Given("the app is started with a migration runner") {
        When("the app boots") {
            Then("migrations from the classpath are applied AND /health still works") {
                val dbName = "tc_${UUID.randomUUID().toString().replace("-", "")}"
                val conn = DriverManager.getConnection(
                    "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                )
                val runner = MigrationRunner.fromClasspath(conn)

                testApplication {
                    application { module(migrationRunner = runner) }

                    // /health still works after migration
                    val response = client.get("/health")
                    response.status shouldBe HttpStatusCode.OK
                    response.bodyAsText() shouldBe """{"status":"ok"}"""
                }

                // V001 was recorded
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("SELECT count(*) FROM schema_migrations WHERE name = 'V001__baseline.sql'")
                    rs.next()
                    rs.getInt(1) shouldBe 1
                }
            }
        }
    }
})
