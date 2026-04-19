package dev.jidouka.automations.dsl.scopes

import dev.jidouka.aliases.EntityId
import dev.jidouka.automations.dsl.triggers.TriggerContext
import dev.jidouka.components.BaseState
import dev.jidouka.components.Domain
import dev.jidouka.components.Entity
import dev.jidouka.components.GenericState
import dev.jidouka.components.StateTransition
import dev.jidouka.components.event.BaseEvent
import dev.jidouka.components.event.EventType
import dev.jidouka.components.event.GenericEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Instant

public class TriggeredScope internal constructor(
    private val context: TriggerContext
) {
    private val logger = KotlinLogging.logger {}

    //region type triggers
    //region State
    public fun byState(): Boolean = context is TriggerContext.StateContext

    public fun byState(entity: Entity<*>): Boolean {
        return resolveStateTransition(entity) != null
    }

    /**
     * Automation was triggered by a state change in the domain
     * @return true if the state change was in the domain
     */
    public fun byState(domain: Domain<*>): Boolean {
        val stateContext = context as? TriggerContext.StateContext ?: return false

        return when (stateContext) {
            is TriggerContext.StateContext.State -> {
                domainMatchesEntity(stateContext.transition.toState.entityId, domain)
            }

            is TriggerContext.StateContext.States -> {
                stateContext.transitions.any { domainMatchesEntity(it.toState.entityId, domain) }
            }
        }
    }

    //endregion
    //region Event
    public fun byEvent(): Boolean = context is TriggerContext.Event

    public fun byEvent(eventType: EventType<*>): Boolean {
        val triggerContext = context as? TriggerContext.Event ?: return false
        return triggerContext.data.eventTypeId == eventType.id
    }

    public fun byEvent(eventTypeId: String): Boolean {
        val triggerContext = context as? TriggerContext.Event ?: return false
        return triggerContext.data.eventTypeId == eventTypeId
    }
    //endregion

    //region Time
    public fun byTime(): Boolean = context is TriggerContext.Time
    //endregion

    //region Flow
    public fun byFlow(): Boolean {
        return context is TriggerContext.Flow
    }

    public fun byFlow(label: String): Boolean {
        val flowContext = context as? TriggerContext.Flow ?: return false
        return flowContext.label == label
    }
    //endregion
    //endregion

    //region data triggers
    //region state
    /**
     * Result of parsing a state trigger with typed states.
     */
    public data class StateTriggerResult<S : BaseState<S>>(
        val entityId: EntityId,
        val state: S,
        val previousState: S?
    )

    /**
     * Returns a generic state if the automation was triggered by a **SINGLE** state
     * If the automation was triggered by combined states this function will return null. Multiple states should be retrieved with
     * the type safe `state(entity: Entity<S>)` function
     */
    public fun state(): StateTriggerResult<GenericState>? {
        val transition = resolveSingleStateTransition(context) ?: return null

        val state = GenericState.parser.parse(transition.toState) ?: return null
        val previousState = transition.fromState?.let {
            GenericState.parser.parse(it)
        }

        return StateTriggerResult(
            entityId = transition.toState.entityId,
            state = state,
            previousState = previousState

        )
    }

    public fun <S : BaseState<S>> state(domain: Domain<S>): StateTriggerResult<S>? {
        val transition = resolveSingleStateTransition(context) ?: return null
        val entityId = transition.toState.entityId

        if (domainMatchesEntity(entityId, domain).not()) {
            return null
        }

        val state = domain.entityParser?.parse(transition.toState) ?: return null
        val previousState = transition.fromState?.let {
            domain.entityParser.parse(it)
        }

        return StateTriggerResult(
            entityId = entityId,
            state = state,
            previousState = previousState

        )
    }

    public fun <S : BaseState<S>> state(entity: Entity<S>): StateTriggerResult<S>? {
        val transition = resolveStateTransition(entity) ?: return null

        val state = entity.parseTransition(transition) ?: return null

        return StateTriggerResult(
            entityId = entity.entityId,
            state = state,
            previousState = state.previous
        )
    }

    //endregion
    //region Events
    public fun event(): GenericEvent? {
        val triggerContext = context as? TriggerContext.Event ?: return null
        return GenericEvent.asGenericEvent(triggerContext.data)
    }

    public fun event(eventTypeId: String): GenericEvent? {
        val triggerContext = context as? TriggerContext.Event ?: return null

        if (eventTypeId != triggerContext.eventType) {
            return null
        }

        return GenericEvent.asGenericEvent(triggerContext.data)
    }

    public fun <E : BaseEvent> event(eventType: EventType<E>): E? {
        val triggerContext = context as? TriggerContext.Event ?: return null

        if (eventType.id != triggerContext.eventType) {
            return null
        }

        @Suppress("UNCHECKED_CAST")
        return triggerContext.data as? E
    }

    //endregion
    public fun time(): Instant? {
        return (context as? TriggerContext.Time)?.triggeredAt
    }

    //region Flow
    /**
     * Returns the flow trigger context if the automation was triggered by a flow trigger.
     */
    public fun flow(): TriggerContext.Flow? {
        return context as? TriggerContext.Flow
    }

    /**
     * Returns the flow trigger context if the automation was triggered by a flow trigger and the label matches.
     */
    public fun flow(label: String): TriggerContext.Flow? {
        val flowContext = flow() ?: return null

        return if (flowContext.label == label) {
            flowContext
        } else {
            null
        }
    }
    //endregion
    //endregion

    private fun resolveStateTransition(entity: Entity<*>): StateTransition? = when (context) {
        is TriggerContext.StateContext.State -> {
            if (context.transition.toState.entityId == entity.entityId) {
                context.transition
            } else {
                null
            }
        }

        is TriggerContext.StateContext.States -> {
            context.transitions.firstOrNull {
                it.toState.entityId == entity.entityId
            }
        }

        is TriggerContext.Event,
        is TriggerContext.Time,
        is TriggerContext.Flow -> null
    }

    private fun resolveSingleStateTransition(triggerContext: TriggerContext): StateTransition? {
        val stateContext = triggerContext as? TriggerContext.StateContext ?: return null
        return when (stateContext) {
            is TriggerContext.StateContext.State -> stateContext.transition
            is TriggerContext.StateContext.States -> {
                logger.debug { "Cannot resolve single state from combined states trigger. Use triggered.state(entity) instead." }
                null
            }
        }
    }

    private fun domainMatchesEntity(entityId: EntityId, domain: Domain<*>): Boolean {
        return entityId.startsWith(domain.id + ".")
    }
}