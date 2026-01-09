plugins {
    kotlin("jvm")
}

dependencies {
    // SLF4J API - 日志门面
    api("org.slf4j:slf4j-api:2.0.9")

    // Logback - Standalone 环境的默认实现（可选）
    // IDEA 插件环境会使用 IDEA 内置的 SLF4J 实现
    compileOnly("ch.qos.logback:logback-classic:1.4.14")
}

kotlin {
    jvmToolchain(17)
}
