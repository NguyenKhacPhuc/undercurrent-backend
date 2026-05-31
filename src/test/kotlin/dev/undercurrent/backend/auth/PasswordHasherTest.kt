package dev.undercurrent.backend.auth

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith

class PasswordHasherTest : BehaviorSpec({

    Given("PasswordHasher.hash()") {
        When("called with a password") {
            Then("returns an argon2id PHC-string") {
                val hash = PasswordHasher.hash("hunter2-correct-horse-battery-staple")
                hash shouldStartWith "\$argon2id\$"
            }
        }

        When("called twice with the same password") {
            Then("produces two different hashes (salts differ)") {
                val h1 = PasswordHasher.hash("same-password")
                val h2 = PasswordHasher.hash("same-password")
                h1 shouldNotBe h2
            }
        }
    }

    Given("PasswordHasher.verify()") {
        When("the password matches the hash") {
            Then("returns true") {
                val hash = PasswordHasher.hash("correct-password")
                PasswordHasher.verify("correct-password", hash) shouldBe true
            }
        }

        When("the password does not match the hash") {
            Then("returns false") {
                val hash = PasswordHasher.hash("correct-password")
                PasswordHasher.verify("wrong-password", hash) shouldBe false
            }
        }

        When("the stored hash is malformed") {
            Then("returns false (does not throw)") {
                PasswordHasher.verify("any-password", "not-an-argon2-hash") shouldBe false
            }
        }
    }
})
