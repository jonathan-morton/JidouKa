import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "dev.jidouka"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
    explicitApi = ExplicitApiMode.Strict
}

dependencies {
    implementation(libs.bundles.ktor.client)
    implementation(libs.kotlin.logging)

    api(libs.kotlinx.serialization.json)

    compileOnly(platform(libs.koin.bom))
    compileOnly(libs.koin.annotations)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

