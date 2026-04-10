package dev.jidouka.automations.dsl.builders

import dev.jidouka.aliases.EntityId
import dev.jidouka.aliases.EventTypeId
import dev.jidouka.automations.dsl.AutomationDsl
import dev.jidouka.automations.dsl.providers.DefaultTimeExtensionsProvider
import dev.jidouka.automations.dsl.providers.EntityProvider
import dev.jidouka.automations.dsl.providers.RegistryEntityProvider
import dev.jidouka.automations.dsl.providers.TimeExtensionsProvider
import dev.jidouka.automations.dsl.scopes.EventsScope
import dev.jidouka.automations.dsl.scopes.LoggingScope
import dev.jidouka.automations.dsl.triggers.TimeTriggers
import dev.jidouka.automations.dsl.triggers.TriggerContext
import dev.jidouka.automations.dsl.triggers.TriggerMetadata
import dev.jidouka.components.BaseState
import dev.jidouka.components.Entity
import dev.jidouka.components.StateTransition
import dev.jidouka.registry.EntityRegistry
import dev.jidouka.registry.EventRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Builder block to define the triggers that will cause an automation's actions to run
 * @property time Namespace for creating time-based triggers
 * @property events Namespace for creating triggers based on events
 * @property log Namespace for logging
 */
@OptIn(ExperimentalTime::class)
@AutomationDsl
public class TriggersBuilder @OptIn(ExperimentalTime::class) internal constructor(
    private val clockFlow: SharedFlow<Instant>,
    private val clock: Clock,
    private val entityRegistry: EntityRegistry,
    eventRegistry: EventRegistry,
    automationId: String,
    override val timeZone: TimeZone = TimeZone.currentSystemDefault()
) : EntityProvider by RegistryEntityProvider(entityRegistry),
    TimeExtensionsProvider by DefaultTimeExtensionsProvider(timeZone) {
    private val triggers = mutableListOf<Flow<TriggerContext>>()
    private val logger = KotlinLogging.logger {}

    private val startupEvaluators = mutableListOf<suspend () -> TriggerContext?>()

    private val entityIdsSet = mutableSetOf<EntityId>()
    private val eventTypesSet = mutableSetOf<EventTypeId>()

    public val time: TimeTriggers = TimeTriggers(
        triggersBuilder = this,
        clockFlow = clockFlow,
        clock = clock,
        timeZone = timeZone
    )

    public val events: EventsScope =
        EventsScope(eventRegistry) { triggerContextFlow, eventTypeId ->
            triggers.add(triggerContextFlow)
            eventTypesSet.add(eventTypeId)
        }

    public val log: LoggingScope = LoggingScope(automationId)

    /**
     * Trigger when entity state matches predicate.
     *
     * @param entity The entity to monitor
     * @param distinctUntilChanged If true (default), only trigger when predicate result changes.
     *                            If false, trigger on every state change where predicate is true.
     * @param predicate Function returning true when automation should trigger
     */
    public fun <S : BaseState<S>> state(
        entity: Entity<S>,
        distinctUntilChanged: Boolean = true,
        predicate: suspend (S?) -> Boolean
    ) {
        val triggerFlow: Flow<TriggerContext.StateContext.State> = entity.changeFlow
            .mapNotNull { transition ->
                val state = entity.parseTransition(transition)

                val predicateIsTrue = predicate(state)

                if (predicateIsTrue) {
                    TriggerContext.StateContext.State(transition)
                } else {
                    null
                }
            }.let {
                if (distinctUntilChanged) {
                    it.distinctUntilChanged()
                } else {
                    it
                }
            }

        triggers.add(triggerFlow)
        addEntityId(entity)

        startupEvaluators.add {
            val current = entity.rawStateFlow.replayCache.firstOrNull() ?: return@add null
            val state = entity.parseTransition(current)
            if (predicate(state)) {
                TriggerContext.StateContext.State(current)
            } else {
                null
            }
        }
    }

    /**
     * Combine two entities with a predicate.
     *
     * @param distinctUntilChanged If true (default), only trigger when combined predicate result changes.
     *                            If false, trigger whenever any entity changes and predicate is true.
     * @param predicate Function combining both entity states, returning true when automation should trigger
     */
    public fun <S1 : BaseState<S1>, S2 : BaseState<S2>> combineState(
        entity1: Entity<S1>,
        entity2: Entity<S2>,
        distinctUntilChanged: Boolean = true,
        predicate: suspend (S1?, S2?) -> Boolean
    ) {
        combineStateArrays(
            entity1, entity2,
            distinctUntilChanged = distinctUntilChanged,
        ) { transitions ->
            predicate(
                entity1.parseTransition(transitions[0]),
                entity2.parseTransition(transitions[1]),
            )
        }
    }

    /**
     * Combine three entities with a predicate.
     *
     * @param entity1 First entity to monitor
     * @param entity2 Second entity to monitor
     * @param entity3 Third entity to monitor
     * @param distinctUntilChanged If true (default), only trigger when combined predicate result changes.
     *                            If false, trigger whenever any entity changes and predicate is true.
     * @param predicate Function combining all entity states, returning true when automation should trigger
     */
    public fun <S1 : BaseState<S1>, S2 : BaseState<S2>, S3 : BaseState<S3>> combineState(
        entity1: Entity<S1>,
        entity2: Entity<S2>,
        entity3: Entity<S3>,
        distinctUntilChanged: Boolean = true,
        predicate: suspend (S1?, S2?, S3?) -> Boolean
    ) {
        combineStateArrays(
            entity1, entity2, entity3,
            distinctUntilChanged = distinctUntilChanged,
        ) { transitions ->
            predicate(
                entity1.parseTransition(transitions[0]),
                entity2.parseTransition(transitions[1]),
                entity3.parseTransition(transitions[2]),
            )
        }
    }

    /**
     * Combine four entities with a predicate.
     *
     * @param distinctUntilChanged If true (default), only trigger when combined predicate result changes.
     *                            If false, trigger whenever any entity changes and predicate is true.
     * @param predicate Function combining all entity states, returning true when automation should trigger
     */
    public fun <S1 : BaseState<S1>, S2 : BaseState<S2>, S3 : BaseState<S3>, S4 : BaseState<S4>> combineState(
        entity1: Entity<S1>,
        entity2: Entity<S2>,
        entity3: Entity<S3>,
        entity4: Entity<S4>,
        distinctUntilChanged: Boolean = true,
        predicate: suspend (S1?, S2?, S3?, S4?) -> Boolean
    ) {
        combineStateArrays(
            entity1, entity2, entity3, entity4,
            distinctUntilChanged = distinctUntilChanged,
        ) { transitions ->
            predicate(
                entity1.parseTransition(transitions[0]),
                entity2.parseTransition(transitions[1]),
                entity3.parseTransition(transitions[2]),
                entity4.parseTransition(transitions[3]),
            )
        }
    }

    /**
     * Combine five entities with a predicate.
     *
     * @param distinctUntilChanged If true (default), only trigger when combined predicate result changes.
     *                            If false, trigger whenever any entity changes and predicate is true.
     * @param predicate Function combining all entity states, returning true when automation should trigger
     */
    public fun <S1 : BaseState<S1>, S2 : BaseState<S2>, S3 : BaseState<S3>, S4 : BaseState<S4>, S5 : BaseState<S5>> combineState(
        entity1: Entity<S1>,
        entity2: Entity<S2>,
        entity3: Entity<S3>,
        entity4: Entity<S4>,
        entity5: Entity<S5>,
        distinctUntilChanged: Boolean = true,
        predicate: suspend (S1?, S2?, S3?, S4?, S5?) -> Boolean
    ) {
        combineStateArrays(
            entity1, entity2, entity3, entity4, entity5,
            distinctUntilChanged = distinctUntilChanged,
        ) { transitions ->
            predicate(
                entity1.parseTransition(transitions[0]),
                entity2.parseTransition(transitions[1]),
                entity3.parseTransition(transitions[2]),
                entity4.parseTransition(transitions[3]),
                entity5.parseTransition(transitions[4]),
            )
        }
    }

    private fun combineStateArrays(
        vararg entities: Entity<*>,
        distinctUntilChanged: Boolean,
        predicate: suspend (Array<StateTransition>) -> Boolean
    ) {
        val triggerFlow: Flow<TriggerContext.StateContext.States> = merge(
            *entities.map {
                it.changeFlow
            }.toTypedArray()
        ).mapNotNull {
            val transitions = entities.map { entity ->
                entity.rawStateFlow.replayCache.firstOrNull()
                    ?: return@mapNotNull null
            }.toTypedArray()

            if (predicate(transitions)) {
                TriggerContext.StateContext.States(transitions.toList())
            } else {
                null
            }
        }.let {
            if (distinctUntilChanged) {
                it.distinctUntilChanged()
            } else {
                it
            }
        }

        triggers.add(triggerFlow)
        addEntityIds(entities.toList())

        startupEvaluators.add {
            val transitions = entities.map { entity ->
                entity.rawStateFlow.replayCache.firstOrNull()
                    ?: return@add null
            }.toTypedArray()

            if (predicate(transitions)) {
                TriggerContext.StateContext.States(transitions.toList())
            } else {
                null
            }
        }
    }

    internal fun addEntityIds(entities: List<Entity<*>>) {
        entities.forEach {
            addEntityId(it)
        }
    }

    internal fun addEntityId(entity: Entity<*>) {
        entityIdsSet.add(entity.entityId)
    }

    /**
     * Internal bridge for TimeTriggers to register clock-based boolean flows.
     */
    internal fun timeTriggeredFlow(
        flow: Flow<Boolean>,
    ) {
        triggers.add(
            flow.filter {
                it
            }.map {
                TriggerContext.Time(triggeredAt = clock.now())
            }
        )
    }

    internal fun buildFlow(): Flow<TriggerContext> {
        require(triggers.isNotEmpty()) {
            "At least one trigger must be defined in triggers block"
        }

        logger.debug { "Building trigger flow with ${triggers.size} trigger(s)" }

        return if (triggers.size == 1) {
            triggers.first()
        } else {
            merge(*triggers.toTypedArray())
        }
    }

    internal fun buildMetadata(): List<TriggerMetadata> {
        val metadata = mutableListOf<TriggerMetadata>()

        if (entityIdsSet.isNotEmpty()) {
            metadata.add(TriggerMetadata.StateTrigger(entityIdsSet))
        }

        if (eventTypesSet.isNotEmpty()) {
            metadata.add(TriggerMetadata.EventTrigger(eventTypesSet))
        }

        logger.debug { "Built trigger metadata: ${entityIdsSet.size} entity ID(s), ${eventTypesSet.size} event type(s)" }

        return metadata
    }

    internal suspend fun evaluateCurrentState(): TriggerContext? {
        return startupEvaluators.firstNotNullOfOrNull { it() }
    }
}