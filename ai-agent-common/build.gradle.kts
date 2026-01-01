plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-library`
}

group = "com.asakii"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    api("org.jetbrains.kotlin:kotlin-stdlib")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:${rootProject.extra["serializationVersion"]}")
    api("io.modelcontextprotocol.sdk:mcp-json:0.17.0")
    implementation("io.modelcontextprotocol.sdk:mcp-json-jackson2:0.17.0")
}
