package dev.jidouka.test

import dev.jidouka.automations.registry.subscription.AutomationId
import dev.jidouka.network.models.hass.websocket.ActionTarget

/**
 * Events recorded during automation execution in test environments.
 */
public sealed class RecordedEvent {
    /**
     * Recorded when an automation's trigger fires.
     * @property automationId The automation that was triggered
     */
    public data class TriggerFired(val automationId: AutomationId) : RecordedEvent()

    /**
     * Recorded when an automation's conditions evaluate to false.
     * @property automationId The automation that was triggered
     */
    public data class ConditionFailed(val automationId: AutomationId) : RecordedEvent()

    /**
     * Recorded when an automation calls a Home Assistant service action
     * @property automationId The automation that called the action
     * @property domainId The Home Assistant domain (e.g., "light", "switch")
     * @property action The service action name (e.g., "turn_on", "set_temperature")
     * @property target The action target
     * @property data Service data parameters
     */
    public data class Action(
        val automationId: AutomationId,
        val domainId: String,
        val action: String,
        val target: ActionTarget? = null,
        val data: Map<String, Any?>
    ) : RecordedEvent()

    /**
     * Recorded when an automation finishes executing all actions.
     *
     * @property automationId The automation that completed
     * @property cancelled true if the automation was cancelled, false if it completed naturally
     */
    public data class AutomationCompleted(
        val automationId: AutomationId,
        val cancelled: Boolean
    ) : RecordedEvent()

    /**
     * Recorded when an automation's action block throws an exception.
     *
     * @property automationId The automation that failed
     * @property exception The exception thrown during action execution
     */
    public data class AutomationFailed(
        val automationId: AutomationId,
        val exception: Throwable
    ) : RecordedEvent()
}