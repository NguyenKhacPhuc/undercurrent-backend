package dev.undercurrent.backend.db

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class MigrationRunnerTest : BehaviorSpec({

    fun freshConnection(): Connection {
        // Each test gets its own in-memory H2 instance (Postgres compat mode).
        // DB_CLOSE_DELAY=-1 keeps the DB alive for the duration of the connection.
        val dbName = "tc_${UUID.randomUUID().toString().replace("-", "")}"
        return DriverManager.getConnection("jdbc:h2:mem:$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
    }

    Given("an empty migrations list") {
        When("migrate() runs") {
            Then("schema_migrations is created and no rows recorded") {
                val conn = freshConnection()
                val applied = MigrationRunner(conn, migrations = emptyList()).migrate()
                applied shouldBe emptyList()
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("SELECT count(*) FROM schema_migrations")
                    rs.next()
                    rs.getInt(1) shouldBe 0
                }
            }
        }
    }

    Given("one migration") {
        When("migrate() runs") {
            Then("it is applied and recorded") {
                val conn = freshConnection()
                val migrations = listOf(
                    Migration(name = "V001__hello.sql", sql = "CREATE TABLE hello (id INT)"),
                )
                MigrationRunner(conn, migrations).migrate() shouldContainExactly listOf("V001__hello.sql")
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("SELECT name FROM schema_migrations ORDER BY name")
                    rs.next()
                    rs.getString(1) shouldBe "V001__hello.sql"
                }
            }
        }
    }

    Given("a migration that was already applied") {
        When("migrate() runs again") {
            Then("the second run applies nothing new") {
                val conn = freshConnection()
                val migrations = listOf(
                    Migration("V001__hello.sql", "CREATE TABLE hello (id INT)"),
                )
                MigrationRunner(conn, migrations).migrate()
                MigrationRunner(conn, migrations).migrate() shouldBe emptyList()
            }
        }
    }

    Given("multiple unsorted migrations") {
        When("migrate() runs") {
            Then("they are applied in lexicographic order") {
                val conn = freshConnection()
                val migrations = listOf(
                    Migration("V002__b.sql", "CREATE TABLE table_b (id INT)"),
                    Migration("V001__a.sql", "CREATE TABLE table_a (id INT)"),
                    Migration("V003__c.sql", "CREATE TABLE table_c (id INT)"),
                )
                MigrationRunner(conn, migrations).migrate() shouldContainExactly listOf(
                    "V001__a.sql",
                    "V002__b.sql",
                    "V003__c.sql",
                )
            }
        }
    }
})
