-- V004: prompt_config — single-row store for the backend-driven assistant base prompt.
-- Exactly one row (id = 1, a singleton). preamble holds the full app-owned base
-- prompt text; revision is a content-derived opaque marker that changes whenever
-- the text changes (the update story relies on it); updated_at is portable
-- TIMESTAMP (Postgres + H2-PG-mode), ms-precision held in the Kotlin layer.
CREATE TABLE prompt_config (
    id INT PRIMARY KEY,
    preamble TEXT NOT NULL,
    revision VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
