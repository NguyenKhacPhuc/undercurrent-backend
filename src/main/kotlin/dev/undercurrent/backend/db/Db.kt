package dev.undercurrent.backend.db

import org.postgresql.ds.PGSimpleDataSource
import java.sql.Connection
import java.sql.DriverManager
import javax.sql.DataSource

object Db {
    fun toJdbcUrl(databaseUrl: String): String =
        if (databaseUrl.startsWith("jdbc:")) databaseUrl else "jdbc:$databaseUrl"

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
