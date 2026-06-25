package dev.undercurrent.backend.prompt

/**
 * The current base prompt the assistant runs on — one global record.
 * [revision] is an opaque marker derived from [preamble] that changes
 * whenever the text changes, so a client can cheaply tell whether what it
 * has is current. [updatedAtMs] is the epoch-ms of the last change.
 */
data class PromptConfig(
    val preamble: String,
    val revision: String,
    val updatedAtMs: Long,
)

interface PromptConfigRepository {

    /** The current config, or null if it has not been seeded yet. */
    fun get(): PromptConfig?

    /**
     * Seed the singleton config with [preamble] if (and only if) it is not
     * already present. Idempotent across boots — re-running never overwrites
     * an existing prompt.
     */
    fun seedIfEmpty(preamble: String)
}
