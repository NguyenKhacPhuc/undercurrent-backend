package dev.undercurrent.backend.prompt

import java.security.MessageDigest
import java.sql.Timestamp
import javax.sql.DataSource

/** The singleton config lives at this fixed primary key. */
private const val SINGLETON_ID = 1

/**
 * An opaque revision marker derived from the prompt text: same text → same
 * revision, any change → a different revision. Lets a client tell whether
 * what it has is current without comparing the full (large) preamble.
 */
fun computeRevision(preamble: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(preamble.toByteArray(Charsets.UTF_8))
    return "rev." + digest.joinToString("") { "%02x".format(it) }.take(12)
}

class JdbcPromptConfigRepository(private val dataSource: DataSource) : PromptConfigRepository {

    override fun get(): PromptConfig? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT preamble, revision, updated_at FROM prompt_config WHERE id = $SINGLETON_ID",
            ).use { ps ->
                ps.executeQuery().use { rs ->
                    return if (rs.next()) {
                        PromptConfig(
                            preamble = rs.getString("preamble"),
                            revision = rs.getString("revision"),
                            updatedAtMs = rs.getTimestamp("updated_at").time,
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }

    override fun seedIfEmpty(preamble: String) {
        dataSource.connection.use { conn ->
            // Insert the singleton only when it is absent — idempotent across
            // boots, and a no-op once an operator has changed the prompt.
            conn.prepareStatement(
                """
                INSERT INTO prompt_config(id, preamble, revision, updated_at)
                SELECT ?, ?, ?, ?
                WHERE NOT EXISTS (SELECT 1 FROM prompt_config WHERE id = ?)
                """.trimIndent(),
            ).use { ps ->
                ps.setInt(1, SINGLETON_ID)
                ps.setString(2, preamble)
                ps.setString(3, computeRevision(preamble))
                ps.setTimestamp(4, Timestamp(System.currentTimeMillis()))
                ps.setInt(5, SINGLETON_ID)
                ps.executeUpdate()
            }
        }
    }

    override fun update(preamble: String): PromptConfig {
        val revision = computeRevision(preamble)
        val updatedAt = Timestamp(System.currentTimeMillis())
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE prompt_config SET preamble = ?, revision = ?, updated_at = ? WHERE id = ?",
            ).use { ps ->
                ps.setString(1, preamble)
                ps.setString(2, revision)
                ps.setTimestamp(3, updatedAt)
                ps.setInt(4, SINGLETON_ID)
                ps.executeUpdate()
            }
        }
        return PromptConfig(preamble = preamble, revision = revision, updatedAtMs = updatedAt.time)
    }
}
