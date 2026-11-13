plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.jidouka.common)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.koin.test)
}

gradlePlugin {
    plugins {
        create("jidoukaCodegen") {
            id = "dev.jidouka.codegen"
            implementationClass = "dev.jidouka.codegen.JidoukaCodegenPlugin"
        }
    }
}
