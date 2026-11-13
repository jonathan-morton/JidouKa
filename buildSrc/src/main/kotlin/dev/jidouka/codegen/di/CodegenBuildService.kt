package dev.jidouka.codegen.di

import dev.jidouka.codegen.usecases.FetchEntityStatesUseCase
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.koin.dsl.koinApplication

public abstract class CodegenBuildService : BuildService<BuildServiceParameters.None>, AutoCloseable {
    private val koinApplication = koinApplication { modules(CodegenModule) }

    internal val fetchEntityStatesUseCase: FetchEntityStatesUseCase
        get() = koinApplication.koin.get()

    override fun close() {
        koinApplication.close()
    }

    public companion object {
        public const val NAME: String = "jidoukaCodegenBuildService"
    }
}
