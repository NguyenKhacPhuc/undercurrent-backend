-- V002: accounts table.
-- email is stored lowercased + trimmed; uniqueness enforced via column constraint.
-- password_hash is opaque bytes (argon2id encoded form); never queried by content.
-- created_at uses portable TIMESTAMP (Postgres + H2-PG-mode); ms-precision held in the Kotlin layer.
CREATE TABLE accounts (
    id VARCHAR(255) PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
