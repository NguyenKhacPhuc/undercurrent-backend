package dev.undercurrent.backend.db

import java.sql.Connection

class MigrationRunner(
    private val connection: Connection,
    private val migrations: List<Migration>,
) {
    fun migrate(): List<String> {
        ensureSchemaMigrationsTable()
        val applied = appliedNames()
        val pending = migrations.sortedBy { it.name }.filterNot { it.name in applied }
        for (m in pending) applyOne(m)
        return pending.map { it.name }
    }

    private fun ensureSchemaMigrationsTable() {
        // Portable across Postgres + H2-PostgreSQL-mode (used in tests).
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    name VARCHAR(255) PRIMARY KEY,
                    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """.trimIndent(),
            )
        }
    }

    private fun appliedNames(): Set<String> {
        val names = mutableSetOf<String>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT name FROM schema_migrations").use { rs ->
                while (rs.next()) names.add(rs.getString(1))
            }
        }
        return names
    }

    private fun applyOne(migration: Migration) {
        val originalAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            connection.createStatement().use { stmt -> stmt.execute(migration.sql) }
            connection.prepareStatement("INSERT INTO schema_migrations(name) VALUES (?)").use { ps ->
                ps.setString(1, migration.name)
                ps.executeUpdate()
            }
            connection.commit()
        } catch (e: Throwable) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = originalAutoCommit
        }
    }
}
