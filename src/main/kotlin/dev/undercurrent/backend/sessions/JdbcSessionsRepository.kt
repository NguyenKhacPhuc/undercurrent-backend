package dev.undercurrent.backend.sessions

import java.sql.Timestamp
import java.time.Clock
import javax.sql.DataSource

class JdbcSessionsRepository(
    private val dataSource: DataSource,
    private val clock: Clock = Clock.systemUTC(),
) : SessionsRepository {

    override fun issue(accountId: String, ttlMs: Long): IssuedSession {
        val rawToken = SessionTokens.generateRawToken()
        val tokenHash = SessionTokens.hash(rawToken)
        val nowMs = clock.millis()
        val expiresAtMs = nowMs + ttlMs

        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO sessions(token_hash, account_id, issued_at, expires_at)
                VALUES (?, ?, ?, ?)
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, tokenHash)
                ps.setString(2, accountId)
                ps.setTimestamp(3, Timestamp(nowMs))
                ps.setTimestamp(4, Timestamp(expiresAtMs))
                ps.executeUpdate()
            }
        }

        return IssuedSession(token = rawToken, accountId = accountId, expiresAtMs = expiresAtMs)
    }

    override fun validate(token: String): ValidatedSession? {
        val tokenHash = SessionTokens.hash(token)
        val nowMs = clock.millis()

        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT account_id, expires_at FROM sessions
                WHERE token_hash = ? AND revoked_at IS NULL AND expires_at > ?
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, tokenHash)
                ps.setTimestamp(2, Timestamp(nowMs))
                ps.executeQuery().use { rs ->
                    return if (rs.next()) {
                        ValidatedSession(
                            accountId = rs.getString("account_id"),
                            expiresAtMs = rs.getTimestamp("expires_at").time,
                        )
                    } else null
                }
            }
        }
    }

    override fun revoke(token: String) {
        val tokenHash = SessionTokens.hash(token)
        val nowMs = clock.millis()

        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE sessions SET revoked_at = ? WHERE token_hash = ? AND revoked_at IS NULL",
            ).use { ps ->
                ps.setTimestamp(1, Timestamp(nowMs))
                ps.setString(2, tokenHash)
                ps.executeUpdate() // no-op if nothing matched (idempotent)
            }
        }
    }
}
