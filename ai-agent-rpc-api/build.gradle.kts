import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${rootProject.extra["serializationVersion"]}")

    // Coroutines for Flow
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${rootProject.extra["coroutinesVersion"]}")

    // Claude Agent SDK - AgentDefinition 类型由 SDK 统一提供
    api(project(":claude-agent-sdk"))

    // Proto 定义 - 逐步迁移到直接使用 Proto 类
    api(project(":ai-agent-proto"))
}


kotlin {
    jvmToolchain(17)
}




