val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val exposed_version: String by project
val h2_version: String by project

plugins {
    kotlin("jvm") version "2.2.21"
    id("io.ktor.plugin") version "3.3.2"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
}

repositories {
    mavenCentral()
}

group = "com.github.evp2"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("ch.qos.logback:logback-classic:$logback_version")

    implementation("org.jetbrains.exposed:exposed-core:${exposed_version}")
    implementation("org.jetbrains.exposed:exposed-dao:${exposed_version}")
    implementation("org.jetbrains.exposed:exposed-jdbc:${exposed_version}")
    implementation("com.h2database:h2:${h2_version}")

    implementation("io.ktor:ktor-server-html-builder:${ktor_version}")
    implementation("io.ktor:ktor-server-sessions:${ktor_version}")
    implementation("io.ktor:ktor-server-auth:${ktor_version}")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:${kotlin_version}")
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}
