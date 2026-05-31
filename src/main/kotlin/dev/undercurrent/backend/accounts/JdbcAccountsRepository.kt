package dev.undercurrent.backend.accounts

import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

class JdbcAccountsRepository(private val dataSource: DataSource) : AccountsRepository {

    override fun insert(new: NewAccount): Account {
        val id = "acct.${randomUuid12()}"
        val normalizedEmail = new.email.trim().lowercase()
        val nowMs = System.currentTimeMillis()

        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(
                    """
                    INSERT INTO accounts(id, email, display_name, password_hash, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { ps ->
                    ps.setString(1, id)
                    ps.setString(2, normalizedEmail)
                    ps.setString(3, new.displayName)
                    ps.setString(4, new.passwordHash)
                    ps.setTimestamp(5, Timestamp(nowMs))
                    ps.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            // 23505 = unique constraint violation (Postgres + H2-PG-mode).
            if (e.sqlState == "23505") throw EmailAlreadyRegisteredException(normalizedEmail)
            throw e
        }

        return Account(
            id = id,
            email = normalizedEmail,
            displayName = new.displayName,
            passwordHash = new.passwordHash,
            createdAtMs = nowMs,
        )
    }

    override fun findById(id: String): Account? = findOne("id", id)

    override fun findByEmail(email: String): Account? = findOne("email", email.trim().lowercase())

    private fun findOne(column: String, value: String): Account? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, email, display_name, password_hash, created_at FROM accounts WHERE $column = ?",
            ).use { ps ->
                ps.setString(1, value)
                ps.executeQuery().use { rs ->
                    return if (rs.next()) rs.toAccount() else null
                }
            }
        }
    }

    private fun ResultSet.toAccount() = Account(
        id = getString("id"),
        email = getString("email"),
        displayName = getString("display_name"),
        passwordHash = getString("password_hash"),
        createdAtMs = getTimestamp("created_at").time,
    )

    private fun randomUuid12(): String =
        UUID.randomUUID().toString().replace("-", "").take(12)
}
