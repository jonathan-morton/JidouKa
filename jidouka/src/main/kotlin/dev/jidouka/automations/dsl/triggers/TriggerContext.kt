package dev.jidouka.automations.dsl.triggers

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
}