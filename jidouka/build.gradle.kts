import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.dokka)
    `maven-publish`
}

group = "dev.jidouka"
version = "0.3.0"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xexplicit-backing-fields") // https://kotlinlang.org/docs/whatsnew23.html#explicit-backing-fields
    }
    explicitApi = ExplicitApiMode.Strict
}

dependencies {
    // Ktor client modules
    implementation(libs.bundles.ktor.client)
    implementation(libs.ktor.serialization.kotlinx.json)
    api(libs.kotlinx.serialization.json)

    // Other libraries
    api(libs.kotlinx.datetime)
    implementation(libs.kotlin.logging)
    implementation(libs.logback.classic)

    //Koin
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.bundles.koin)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    api(libs.kotlinx.coroutines.test)
}

koinCompiler {
    userLogs = true
}

tasks.test {
    exclude("**/BaseUnitTest.class")
}

val dokkaHtmlJar by tasks.registering(Jar::class) {
    description = "A Javadoc JAR containing Dokka HTML"
    val htmlTask = tasks.named("dokkaGeneratePublicationHtml")
    dependsOn(htmlTask)
    from(htmlTask.map { it.outputs.files })
    archiveClassifier.set("javadoc")
}

val sourcesJar by tasks.registering(Jar::class) {
    description = "A sources JAR"
    from(sourceSets.main.get().allSource)
    archiveClassifier.set("sources")
}

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])
            artifact(dokkaHtmlJar)
            artifact(sourcesJar)

            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            pom {
                name.set("JidouKa")
                description.set("Kotlin DSL for creating Home Assistant automations")
            }
        }
    }
}
