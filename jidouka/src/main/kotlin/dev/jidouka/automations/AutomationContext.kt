package dev.jidouka.automations

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Internal coroutine context element that carries the automation ID through suspend call chains.
 *
 * This context is set by [AutomationExecutor] during action/condition execution and read by
 * [Entity.state()] to track which automation is requesting entity state.
 *
 * @property automationId The ID of the currently executing automation
 */
internal class AutomationContext(
    val automationId: String
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AutomationContext>
}