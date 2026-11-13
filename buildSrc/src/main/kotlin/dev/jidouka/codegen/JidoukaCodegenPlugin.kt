package dev.jidouka.codegen

import dev.jidouka.codegen.di.CodegenBuildService
import dev.jidouka.codegen.tasks.GenerateEntitiesTask
import dev.jidouka.common.configuration.HomeAssistantConfiguration
import dev.jidouka.common.configuration.HomeAssistantEnvironmentVariables
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer

public abstract class JidoukaCodegenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(EXTENSION_NAME, JidoukaCodegenExtension::class.java)
        extension.outputPackage.convention(DEFAULT_OUTPUT_PACKAGE)

        extension.haHost.convention(
            project.providers.environmentVariable(HomeAssistantEnvironmentVariables.HOST)
                .orElse(HomeAssistantConfiguration.DEFAULT_HOST)
        )

        extension.haPort.convention(
            project.providers.environmentVariable(HomeAssistantEnvironmentVariables.PORT)
                .map {
                    it.toIntOrNull()
                        ?: throw GradleException("${HomeAssistantEnvironmentVariables.PORT} must be a number, got '$it'")
                }
                .orElse(HomeAssistantConfiguration.DEFAULT_PORT)
        )

        extension.haAccessToken.convention(
            project.providers.environmentVariable(HomeAssistantEnvironmentVariables.ACCESS_TOKEN)
        )

        extension.entities.enabled.convention(true)
        extension.entities.excludedDomains.convention(extension.excludedDomains)

        val generatedDirectory = project.layout.buildDirectory.dir(GENERATED_SOURCES_PATH)
        val entitiesEnabled = extension.entities.enabled

        project.gradle.sharedServices.registerIfAbsent(CodegenBuildService.NAME, CodegenBuildService::class.java)

        val generateEntities = project.tasks.register(GENERATE_ENTITIES_TASK_NAME, GenerateEntitiesTask::class.java) {
            group = TASK_GROUP
            description = "Generates typed Home Assistant entity code"

            outputPackage.set(extension.outputPackage)

            excludedDomains.set(extension.entities.excludedDomains)
            entityNameOverrides.set(extension.entities.entityNameOverrides)

            haHost.set(extension.haHost)
            haPort.set(extension.haPort)
            haAccessToken.set(extension.haAccessToken)

            outputDirectory.set(generatedDirectory)

            onlyIf("entities generation is enabled") {
                entitiesEnabled.get()
            }
        }

        project.tasks.register(GENERATE_TASK_NAME) {
            group = TASK_GROUP
            description = "Runs all enabled Jidouka code generators"
            dependsOn(generateEntities)
        }

        project.pluginManager.withPlugin(KOTLIN_JVM_PLUGIN_ID) {
            val main: SourceSet = project.extensions.getByType(SourceSetContainer::class.java).getByName("main")
            (main.extensions.getByName("kotlin") as SourceDirectorySet).srcDir(generatedDirectory)

            project.tasks.named(COMPILE_KOTLIN_TASK_NAME).configure {
                doFirst {
                    if (entitiesEnabled.get() && generatedDirectory.get().asFile.walk().none { it.isFile }) {
                        logger.lifecycle("Jidouka: no generated entities found — run ./gradlew $GENERATE_TASK_NAME")
                    }
                }
            }
        }
    }

    public companion object {
        public const val EXTENSION_NAME: String = "jidoukaCodegen"
        public const val GENERATE_TASK_NAME: String = "jidoukaGenerate"
        public const val GENERATE_ENTITIES_TASK_NAME: String = "jidoukaGenerateEntities"

        private const val TASK_GROUP = "jidouka"
        private const val DEFAULT_OUTPUT_PACKAGE = "dev.jidouka.generated"
        private const val GENERATED_SOURCES_PATH = "generated/jidouka/main/kotlin"
        private const val KOTLIN_JVM_PLUGIN_ID = "org.jetbrains.kotlin.jvm"
        private const val COMPILE_KOTLIN_TASK_NAME = "compileKotlin"
    }
}
