package dev.undercurrent.backend

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class HealthRouteTest : BehaviorSpec({
    Given("the app is running") {
        When("GET /health is called") {
            Then("returns 200 with body {\"status\":\"ok\"}") {
                testApplication {
                    application { module() }
                    val response = client.get("/health")
                    response.status shouldBe HttpStatusCode.OK
                    response.bodyAsText() shouldBe """{"status":"ok"}"""
                }
            }
        }
    }
})
