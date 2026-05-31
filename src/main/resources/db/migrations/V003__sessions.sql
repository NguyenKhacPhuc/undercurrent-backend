-- V003: sessions table.
-- token_hash is SHA-256 hex of the raw bearer token; the raw token never
-- touches the DB. account_id FK is enforced. revoked_at NULL = live; non-NULL
-- = revoked (soft-revoke so audit history survives).
CREATE TABLE sessions (
    token_hash VARCHAR(64) PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL
);

-- Lookup paths used by validate() (token_hash + expires_at filter) and by
-- any future "list active sessions for account" admin/UX feature.
CREATE INDEX sessions_account_id_idx ON sessions (account_id);
