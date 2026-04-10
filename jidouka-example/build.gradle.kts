plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

group = "dev.jidouka"
version = "0.0.1"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xexplicit-backing-fields")
        freeCompilerArgs.add("-Xname-based-destructuring=only-syntax")
    }
}

application {
    mainClass = "dev.jidouka.example.ApplicationKt"
}

dependencies {
    // Depend on the jidouka library
    implementation(project(":jidouka"))

    // Coroutines for runBlocking
    implementation(libs.kotlinx.coroutines.core)

    // Serialization for JsonObject usage in ExampleAutomations
    implementation(libs.kotlinx.serialization.json)

    // Logging (transitive via jidouka, but explicit for clarity)
    implementation(libs.kotlin.logging)
    implementation(libs.logback.classic)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
