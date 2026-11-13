package dev.jidouka.codegen.di

import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.test.Test

class CodegenModuleTest {
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `every definition's constructor dependencies are in the module`() {
        CodegenModule.verify()
    }
}
