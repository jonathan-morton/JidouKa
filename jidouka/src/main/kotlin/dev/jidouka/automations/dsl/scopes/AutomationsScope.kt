package dev.jidouka.automations.dsl.scopes

import dev.jidouka.automations.registry.AutomationRegistry
import dev.jidouka.automations.registry.subscription.AutomationId

public interface AutomationsQuery {
    public fun isRunning(automationId: AutomationId): Boolean
}

public class AutomationsScope internal constructor(
    private val automationRegistry: AutomationRegistry
) : AutomationsQuery {
    public fun cancel(automationId: AutomationId): Boolean {
        return automationRegistry.cancel(automationId)
    }

    override fun isRunning(automationId: AutomationId): Boolean {
        return automationRegistry.isRunning(automationId)
    }

    public suspend fun await(automationId: AutomationId) {
        automationRegistry.await(automationId)
    }
}