package dev.undercurrent.backend.accounts

data class Account(
    val id: String,
    val email: String,
    val displayName: String,
    val passwordHash: String,
    val createdAtMs: Long,
)

data class NewAccount(
    val displayName: String,
    val email: String,
    val passwordHash: String,
)

class EmailAlreadyRegisteredException(val email: String) :
    RuntimeException("An account with email '$email' already exists")

interface AccountsRepository {
    fun insert(new: NewAccount): Account
    fun findById(id: String): Account?
    fun findByEmail(email: String): Account?
}
