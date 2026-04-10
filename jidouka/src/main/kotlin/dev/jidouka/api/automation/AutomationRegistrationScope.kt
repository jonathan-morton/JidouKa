package dev.jidouka.api.automation

import dev.jidouka.automations.Automation
import dev.jidouka.automations.AutomationMode
import dev.jidouka.automations.DeferredBuilderAutomation
import dev.jidouka.automations.dsl.builders.AutomationBuilder
import dev.jidouka.automations.registry.AutomationRegistry
import org.koin.core.annotation.Single

public interface AutomationRegistrationScope {

    /**
     * Create and register an automation
     * @param id automation ID
     * @param mode Execution mode
     * @param block Configuration block
     */
    public suspend fun automation(
        id: String,
        mode: AutomationMode = AutomationMode.Single,
        block: AutomationBuilder.() -> Unit
    )

    /**
     * Register an automation with Jidouka
     */
    public suspend fun register(automation: Automation)

    /**
     * Register multiple automations with Jidouka
     */
    public suspend fun register(vararg automations: Automation)

    /**
     * Register multiple automations with Jidouka
     */
    public suspend fun register(automations: List<Automation>)
}

@Single
internal class AutomationRegistrationScopeInternal(
    private val registry: AutomationRegistry
) : AutomationRegistrationScope {
    override suspend fun automation(
        id: String,
        mode: AutomationMode,
        block: AutomationBuilder.() -> Unit
    ) {
        registry.register(
            DeferredBuilderAutomation(
                id = id,
                mode = mode,
                builderBlock = block
            )
        )
    }

    override suspend fun register(automation: Automation) {
        registry.register(automation)
    }

    override suspend fun register(vararg automations: Automation) {
        automations.forEach {
            register(it)
        }
    }

    override suspend fun register(automations: List<Automation>) {
        register(*automations.toTypedArray())
    }
}