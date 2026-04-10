package dev.jidouka.registry

import dev.jidouka.aliases.EntityId
import dev.jidouka.components.StateObject
import dev.jidouka.components.StateTransition
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

internal interface StateRegistry {
    /**
     * Called when subscribe-on-read
     * Populates stateFlow - does NOT signal a change
     */
    fun setCurrentState(
        entityId: EntityId,
        stateObject: StateObject,
        willLog: Boolean = true
    )

    /**
     * Called during start up when fetching all states are initially being subscribed.
     * Populates stateFlow - does NOT signal a change
     */
    fun setInitialState(entityId: EntityId, stateObject: StateObject)

    /**
     * Updates state from actual Home Assistant state change triggers
     */
    fun updateState(
        entityId: EntityId,
        newState: StateObject,
        previousState: StateObject? = null
    )

    fun getStateFlow(entityId: EntityId): SharedFlow<StateTransition>
    fun getChangeFlow(entityId: EntityId): SharedFlow<StateTransition>
    fun hasState(entityId: EntityId): Boolean
    fun getAllEntityIds(): Set<EntityId>

    /**
     * Mark all cached states as stale
     */
    fun markAllStatesStale()
    fun isStateStale(entityId: EntityId): Boolean
}

internal abstract class AbstractStateRegistry : StateRegistry {
    protected val logger = KotlinLogging.logger {}

    /**
     * Container for types of flows that make up an entity
     * @param stateFlow Used for reads, set by get_states, real changes and rest fetches. Setting does NOT trigger anything
     * @param changeFlow Real update changes from Home Assistant. Used for triggers.
     */
    protected class EntityFlows(
        val stateFlow: MutableSharedFlow<StateTransition> = MutableSharedFlow(
            replay = 1,
            extraBufferCapacity = ENTITY_FLOW_EXTRA_BUFFER_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        ),


        val changeFlow: MutableSharedFlow<StateTransition> = MutableSharedFlow(
            replay = 0,
            extraBufferCapacity = ENTITY_FLOW_EXTRA_BUFFER_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    )

    protected val entityFlows = ConcurrentHashMap<EntityId, EntityFlows>()

    override fun setInitialState(entityId: EntityId, stateObject: StateObject) {
        logger.trace { "Set initial state for entity '$entityId' (state: ${stateObject.state})" }
        setCurrentState(entityId, stateObject, willLog = false)
    }

    override fun getStateFlow(entityId: EntityId): SharedFlow<StateTransition> =
        getOrCreate(entityId).stateFlow

    override fun getChangeFlow(entityId: EntityId): SharedFlow<StateTransition> =
        getOrCreate(entityId).changeFlow

    override fun hasState(entityId: EntityId): Boolean =
        entityFlows[entityId]?.stateFlow?.replayCache?.isNotEmpty() ?: false

    override fun getAllEntityIds(): Set<EntityId> = entityFlows.keys.toSet()

    protected fun getOrCreate(entityId: EntityId): EntityFlows {
        var wasCreated = false
        val flows = entityFlows.computeIfAbsent(entityId) {
            wasCreated = true
            EntityFlows()
        }
        if (wasCreated) {
            logger.debug { "Created state flows for entity '$entityId'" }
        }
        return flows
    }

    companion object {
        protected const val ENTITY_FLOW_EXTRA_BUFFER_CAPACITY = 64
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [StateRegistry::class])
internal class HomeAssistantStateRegistry : AbstractStateRegistry() {
    private val staleEntities = ConcurrentHashMap.newKeySet<EntityId>()

    override fun setCurrentState(
        entityId: EntityId,
        stateObject: StateObject,
        willLog: Boolean
    ) {
        val flows = getOrCreate(entityId)
        flows.stateFlow.tryEmit(
            StateTransition(
                toState = stateObject,
                fromState = null
            )
        )

        staleEntities.remove(entityId)

        if (willLog) {
            logger.debug { "Set current state for entity '$entityId' (state: ${stateObject.state})" }
        }
    }

    override fun updateState(
        entityId: EntityId,
        newState: StateObject,
        previousState: StateObject?
    ) {
        val flows = getOrCreate(entityId)

        val transition = StateTransition(
            toState = newState,
            fromState = previousState
        )

        flows.stateFlow.tryEmit(transition)
        flows.changeFlow.tryEmit(transition)

        staleEntities.remove(entityId)

        logger.debug { "Updated state for entity '$entityId' (${previousState?.state} → ${newState.state})" }
    }

    override fun markAllStatesStale() {
        logger.info { "Marking ${entityFlows.size} cached entity states as stale" }

        staleEntities.addAll(entityFlows.keys)

        entityFlows.values.forEach { entityFlows ->
            entityFlows.stateFlow.resetReplayCache()
        }

        logger.debug { "State cache marked as stale" }
    }

    override fun isStateStale(entityId: EntityId): Boolean {
        return staleEntities.contains(entityId)
    }
}