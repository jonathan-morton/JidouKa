package dev.jidouka.codegen.di

import dev.jidouka.codegen.JidoukaCodegenPlugin
import dev.jidouka.codegen.tasks.GenerateEntitiesTask
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CodegenBuildServiceTest {
    private lateinit var project: Project

    @BeforeTest
    fun setUp() {
        project = ProjectBuilder.builder().build()
        project.pluginManager.apply(JidoukaCodegenPlugin::class.java)
    }

    @Test
    fun `the entities task is wired to the registered service`() {
        val task = project.tasks.named(
            JidoukaCodegenPlugin.GENERATE_ENTITIES_TASK_NAME,
            GenerateEntitiesTask::class.java
        ).get()

        assertTrue(task.codegenBuildService.isPresent)
    }

    @Test
    fun `resolves every use case the tasks call`() {
        registeredService().use { service ->
            service.fetchEntityStatesUseCase
        }
    }

    @Test
    fun `close closes the koin application`() {
        val service = registeredService()
        service.close()

        assertFails { service.fetchEntityStatesUseCase }
    }

    private fun registeredService(): CodegenBuildService {
        val registration = project.gradle.sharedServices.registrations.getByName(CodegenBuildService.NAME)
        return assertIs<CodegenBuildService>(registration.service.get())
    }
}
