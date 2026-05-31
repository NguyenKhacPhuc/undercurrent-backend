package dev.undercurrent.backend.db

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.sql.DriverManager
import java.util.UUID

class MigrationRunnerClasspathTest : BehaviorSpec({

    Given("the bundled db/migrations manifest") {
        When("MigrationRunner.fromClasspath().migrate() runs against a fresh DB") {
            Then("V001 baseline is applied and recorded (newer migrations also apply but assertions stay focused)") {
                val dbName = "tc_${UUID.randomUUID().toString().replace("-", "")}"
                val conn = DriverManager.getConnection(
                    "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                )
                val applied = MigrationRunner.fromClasspath(conn).migrate()
                applied shouldContain "V001__baseline.sql"

                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery(
                        "SELECT count(*) FROM schema_migrations WHERE name = 'V001__baseline.sql'",
                    )
                    rs.next()
                    rs.getInt(1) shouldBe 1
                }
            }
        }

        When("migrate() runs a second time on the same DB") {
            Then("no migrations are re-applied") {
                val dbName = "tc_${UUID.randomUUID().toString().replace("-", "")}"
                val conn = DriverManager.getConnection(
                    "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                )
                MigrationRunner.fromClasspath(conn).migrate()
                val secondRun = MigrationRunner.fromClasspath(conn).migrate()
                secondRun shouldBe emptyList()
            }
        }
    }
})
