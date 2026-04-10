package dev.jidouka.test.registry

import dev.jidouka.aliases.EntityId
import dev.jidouka.components.StateObject
import dev.jidouka.components.StateTransition
import dev.jidouka.registry.AbstractStateRegistry
import java.util.concurrent.ConcurrentHashMap

internal class InMemoryStateRegistry : AbstractStateRegistry() {
    private val currentStates = ConcurrentHashMap<EntityId, StateObject>()

    override fun setCurrentState(
        entityId: EntityId,
        stateObject: StateObject,
        willLog: Boolean
    ) {
        currentStates[entityId] = stateObject

        val flows = getOrCreate(entityId)
        flows.stateFlow.tryEmit(
            StateTransition(
                toState = stateObject,
                fromState = null
            )
        )

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

        val actualPreviousState = previousState ?: currentStates[entityId]

        currentStates[entityId] = newState

        val transition = StateTransition(
            toState = newState,
            fromState = actualPreviousState
        )

        flows.stateFlow.tryEmit(transition)
        flows.changeFlow.tryEmit(transition)
        logger.debug { "Updated state for entity '$entityId' (${previousState?.state} → ${newState.state})" }
    }

    override fun markAllStatesStale() = Unit
    override fun isStateStale(entityId: EntityId): Boolean = false

    fun getCurrentState(entityId: EntityId): StateObject? = currentStates[entityId]
}