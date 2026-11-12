plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.jidouka.common)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}

gradlePlugin {
    plugins {
        create("jidoukaCodegen") {
            id = "dev.jidouka.codegen"
            implementationClass = "dev.jidouka.codegen.JidoukaCodegenPlugin"
        }
    }
}
