package dev.undercurrent.backend.db

import org.postgresql.ds.PGSimpleDataSource
import java.net.URI
import java.net.URLEncoder
import java.sql.Connection
import java.sql.DriverManager
import javax.sql.DataSource

object Db {
    /**
     * Normalize `DATABASE_URL` (libpq-style `postgresql://user:pass@host:port/db`,
     * which is what Railway hands out) into a JDBC URL the Postgres JDBC driver
     * understands. The driver does NOT parse the `user:pass@` portion and would
     * otherwise treat it as part of the hostname, causing an `UnknownHostException`.
     *
     * Idempotent: a URL that already starts with `jdbc:` passes through unchanged.
     */
    fun toJdbcUrl(databaseUrl: String): String {
        if (databaseUrl.startsWith("jdbc:")) return databaseUrl

        val uri = URI(databaseUrl)
        val userInfo = uri.userInfo ?: return "jdbc:$databaseUrl"

        val parts = userInfo.split(":", limit = 2)
        val user = parts[0]
        val password = parts.getOrNull(1) ?: ""

        return buildString {
            append("jdbc:").append(uri.scheme).append("://").append(uri.host)
            if (uri.port != -1) append(":").append(uri.port)
            uri.rawPath?.takeIf { it.isNotEmpty() }?.let { append(it) }
            append(if (uri.rawQuery == null) "?" else "?${uri.rawQuery}&")
            append("user=").append(URLEncoder.encode(user, Charsets.UTF_8))
            append("&password=").append(URLEncoder.encode(password, Charsets.UTF_8))
        }
    }

    fun connect(databaseUrl: String? = System.getenv("DATABASE_URL")): Connection {
        val url = databaseUrl ?: error("DATABASE_URL env var is not set")
        return DriverManager.getConnection(toJdbcUrl(url))
    }

    /**
     * Non-pooled Postgres [DataSource] built from `DATABASE_URL`. Each
     * `getConnection()` opens a fresh JDBC connection. Fine for v1 traffic;
     * HikariCP enters when request throughput justifies it.
     */
    fun dataSource(databaseUrl: String? = System.getenv("DATABASE_URL")): DataSource {
        val url = databaseUrl ?: error("DATABASE_URL env var is not set")
        return PGSimpleDataSource().apply { setURL(toJdbcUrl(url)) }
    }
}
