import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Módulo :core — Kotlin puro (sem Android). Contém modelos de domínio, contratos,
// parser M3U, cliente Xtream Codes e regras de negócio. É a referência para a
// futura implementação Roku (ver docs/ROKU-CONTRACTS.md).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
