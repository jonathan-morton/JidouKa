package dev.jidouka

import app.cash.turbine.test
import dev.jidouka.components.Domain
import dev.jidouka.components.Entity
import dev.jidouka.components.GenericState
import dev.jidouka.components.StateObject
import dev.jidouka.registry.HomeAssistantStateRegistry
import dev.jidouka.registry.StateRegistry
import dev.jidouka.test.usecases.TestEnsureEntitySubscribedAndCurrentUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EntityTest : BaseUnitTest() {

    private fun stateObject(
        entityId: String = "sensor.test",
        state: String = "42"
    ) = StateObject(
        entityId = entityId,
        state = state,
        attributesRaw = emptyMap(),
        lastChanged = Instant.fromEpochMilliseconds(0),
        lastUpdated = Instant.fromEpochMilliseconds(0),
        lastReported = null,
        context = null,
    )

    private fun TestScope.newEntity(
        entityId: String = "sensor.test",
        stateRegistry: StateRegistry = HomeAssistantStateRegistry(),
    ): Entity<GenericState> {
        val (domainId, objectId) = entityId.split(".")
        return Entity(
            objectId = objectId,
            domain = Domain(domainId, GenericState.parser),
            parser = GenericState.parser,
            stateRegistry = stateRegistry,
            ensureSubscribedUseCase = TestEnsureEntitySubscribedAndCurrentUseCase(),
            scope = backgroundScope,
        )
    }

    private fun StateRegistry.subscriptionCountFor(entityId: String) =
        (getStateFlow(entityId) as MutableSharedFlow<*>).subscriptionCount.value

    @Test
    fun `constructing an entity does not subscribe to the registry's flow`() = runTestScope {
        val registry = HomeAssistantStateRegistry()
        val entity = newEntity(stateRegistry = registry)

        advanceUntilIdle()

        assertEquals(
            0,
            registry.subscriptionCountFor(entity.entityId),
            "Entity construction must not subscribe to the registry's flow under SharingStarted.Lazily"
        )
    }

    @Test
    fun `cachedState reads current value with zero collectors ever attached`() = runTestScope {
        val registry = HomeAssistantStateRegistry()
        registry.setCurrentState("sensor.test", stateObject())

        val entity = newEntity(stateRegistry = registry)

        assertEquals("42", entity.cachedState?.stateRaw)
    }

    @Test
    fun `cachedState reflects registry updates that happen after construction`() = runTestScope {
        val registry = HomeAssistantStateRegistry()
        val entity = newEntity(stateRegistry = registry)

        registry.setCurrentState("sensor.test", stateObject())

        assertEquals("42", entity.cachedState?.stateRaw)
    }

    @Test
    fun `cachedState returns null after markAllStatesStale`() = runTestScope {
        val registry = HomeAssistantStateRegistry()
        registry.setCurrentState("sensor.test", stateObject())
        val entity = newEntity(stateRegistry = registry)

        assertNotNull(
            entity.cachedState,
            "sanity check: fixture should produce a non-null cachedState before staleness"
        )

        registry.markAllStatesStale()

        assertNull(entity.cachedState, "cachedState must respect registry staleness resets")
    }

    @Test
    fun `first stateFlow collector receives current state and subscribes to the registry's flow`() = runTestScope {
        val registry = HomeAssistantStateRegistry()
        registry.setCurrentState("sensor.test", stateObject())
        val entity = newEntity(stateRegistry = registry)

        entity.stateFlow.test {
            assertEquals("42", awaitItem()?.stateRaw)
            assertEquals(
                1,
                registry.subscriptionCountFor(entity.entityId),
                "Lazily should subscribe to the registry's flow once stateFlow has a real collector"
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state reads current value even when nothing ever collects stateFlow`() = runTestScope {
        val registry = HomeAssistantStateRegistry()
        registry.setCurrentState("sensor.test", stateObject())
        val entity = newEntity(stateRegistry = registry)

        assertEquals("42", entity.state()?.stateRaw)
    }
}