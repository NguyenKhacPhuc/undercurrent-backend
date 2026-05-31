package dev.undercurrent.backend.db

import java.sql.Connection
import java.sql.DriverManager

object Db {
    fun toJdbcUrl(databaseUrl: String): String =
        if (databaseUrl.startsWith("jdbc:")) databaseUrl else "jdbc:$databaseUrl"

    fun connect(databaseUrl: String? = System.getenv("DATABASE_URL")): Connection {
        val url = databaseUrl ?: error("DATABASE_URL env var is not set")
        return DriverManager.getConnection(toJdbcUrl(url))
    }
}
