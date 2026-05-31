plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    application
}

group = "dev.undercurrent"
version = "0.1.0"

repositories {
    mavenCentral()
}

application {
    mainClass.set("dev.undercurrent.backend.ApplicationKt")
}

dependencies {
    val ktorVersion = "3.0.3"
    val kotestVersion = "5.9.1"

    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:1.5.13")

    // Postgres + JDBC for the migration runner (story 02).
    implementation("org.postgresql:postgresql:42.7.4")

    // Argon2id password hashing for sign-up/sign-in (stories 05/06).
    // Pure Java; no native bindings.
    implementation("com.password4j:password4j:1.8.2")

    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")

    // H2 in Postgres-compat mode for DB-roundtrip tests.
    // Was Testcontainers Postgres; pivoted during Story 02 Construction due to
    // a Docker Desktop helper-socket compat issue. See workspace decisions D10/D11.
    testImplementation("com.h2database:h2:2.3.232")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
