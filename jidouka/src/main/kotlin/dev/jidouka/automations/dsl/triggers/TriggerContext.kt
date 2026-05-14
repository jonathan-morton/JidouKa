package dev.jidouka.automations.dsl.triggers

import dev.jidouka.aliases.WebhookId
import dev.jidouka.components.StateTransition
import dev.jidouka.components.event.BaseEvent
import kotlin.time.Instant

/**
 * The context information of the trigger that started the automation
 */
public sealed class TriggerContext {

    /**
     * Context for triggers started by one or more state changes
     */
    public sealed class StateContext : TriggerContext() {
        /**
         * Context for a trigger started by a state change
         */
        public data class State(
            val transition: StateTransition
        ) : StateContext()

        /**
         * Context for a trigger started by multiple state changes
         */
        public data class States(
            val transitions: List<StateTransition>
        ) : StateContext()
    }

    /**
     * Context for a trigger started by an event firing
     */
    public data class Event(
        val eventType: String,
        val data: BaseEvent
    ) : TriggerContext()

    /**
     * Context for a trigger started by a time change
     */
    public data class Time(
        val triggeredAt: Instant
    ) : TriggerContext()

    /**
     * Context for a trigger fired by a flow source
     *
     * @property label Optional label used to identify the flow that triggered the automation
     * @property data The emitted value from the flow. Can also be extracted data from the flow.
     * Type erased, so needs to be safe casts
     */
    public data class Flow(
        val label: String? = null,
        val data: Any? = null
    ) : TriggerContext()

    public data class Webhook(
        val webhookId: WebhookId,
        val jsonData: Map<String, Any?>?,
        val formDataRepresentation: String?,
        val queryRepresentation: String?
    ) : TriggerContext()
}