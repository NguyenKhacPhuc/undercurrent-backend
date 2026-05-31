package dev.undercurrent.backend.db

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.sql.DriverManager
import java.util.UUID

class MigrationRunnerClasspathTest : BehaviorSpec({

    Given("the bundled db/migrations manifest") {
        When("MigrationRunner.fromClasspath().migrate() runs against a fresh DB") {
            Then("V001__baseline.sql is applied and recorded") {
                val dbName = "tc_${UUID.randomUUID().toString().replace("-", "")}"
                val conn = DriverManager.getConnection(
                    "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                )
                val applied = MigrationRunner.fromClasspath(conn).migrate()
                applied shouldContainExactly listOf("V001__baseline.sql")

                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("SELECT name FROM schema_migrations ORDER BY name")
                    rs.next()
                    rs.getString(1) shouldBe "V001__baseline.sql"
                }
            }
        }
    }
})
