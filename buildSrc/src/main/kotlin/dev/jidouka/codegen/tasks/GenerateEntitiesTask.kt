package dev.jidouka.codegen.tasks

import dev.jidouka.codegen.di.CodegenBuildService
import dev.jidouka.common.configuration.HomeAssistantConfiguration
import dev.jidouka.common.configuration.HomeAssistantEnvironmentVariables
import dev.jidouka.common.network.models.hass.websocket.StateData
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File

public abstract class GenerateEntitiesTask : DefaultTask() {
    @get:Input
    public abstract val outputPackage: Property<String>

    @get:Input
    public abstract val excludedDomains: SetProperty<String>

    @get:Input
    public abstract val entityNameOverrides: MapProperty<String, String>

    @get:Input
    @get:Option(
        option = "domain",
        description = "Only regenerate these domains. Repeat for more: `--domain=light --domain=switch`"
    )
    public abstract val domainFilter: SetProperty<String>

    @get:Internal
    public abstract val haHost: Property<String>

    @get:Internal
    public abstract val haPort: Property<Int>

    @get:Internal
    public abstract val haAccessToken: Property<String>

    @get:ServiceReference(CodegenBuildService.NAME)
    public abstract val codegenBuildService: Property<CodegenBuildService>

    @get:OutputDirectory
    public abstract val outputDirectory: DirectoryProperty

    init {
        outputs.upToDateWhen { false } //forces a rerun when initiated
    }

    @TaskAction
    public fun generate() {
        val states = fetchStates()
        logger.lifecycle("Fetched ${states.size} entity states")

        val outputRoot = outputDirectory.get().asFile

        val domains = domainFilter.get()
        if (domains.isEmpty()) {
            outputRoot.deleteRecursively()
            writePlaceholder(outputRoot, outputPackage.get())
        } else {
            domains.forEach { domain ->
                val domainPackage = "${outputPackage.get()}.$domain"
                outputRoot.resolve(domainPackage.replace('.', '/')).deleteRecursively()
                writePlaceholder(outputRoot, domainPackage)
            }
        }
    }

    private fun fetchStates(): List<StateData> {
        val accessToken = haAccessToken.orNull
            ?: throw GradleException(
                "${HomeAssistantEnvironmentVariables.ACCESS_TOKEN} is not set. Export a Home Assistant long-lived access token."
            )
        val configuration = HomeAssistantConfiguration(
            host = haHost.get(),
            port = haPort.get(),
            accessToken = accessToken
        )

        val fetchEntityStatesUseCase = codegenBuildService.get().fetchEntityStatesUseCase

        return runBlocking {
            fetchEntityStatesUseCase.fetchStates(configuration)
        }.getOrElse { exception ->
            throw GradleException(
                "Fetching entity states from ${configuration.host}:${configuration.port} failed: " +
                        (exception.message ?: exception::class.simpleName),
                exception
            )
        }
    }

    private fun writePlaceholder(outputRoot: File, packageName: String) {
        val packageDirectory = outputRoot.resolve(packageName.replace('.', '/'))
        packageDirectory.mkdirs()
        packageDirectory.resolve("GeneratedPlaceholder.kt").writeText(
            """
                package $packageName

                public object GeneratedPlaceholder
            """.trimIndent()
        )

        logger.lifecycle("Wrote placeholder to $packageDirectory")
    }
}
