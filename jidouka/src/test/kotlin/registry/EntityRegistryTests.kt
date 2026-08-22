package dev.jidouka.registry

import dev.jidouka.BaseUnitTest
import dev.jidouka.aliases.EntityId
import dev.jidouka.components.Domain
import dev.jidouka.components.Entity
import dev.jidouka.components.GenericState
import dev.jidouka.components.StateObject
import dev.jidouka.state.TestState
import dev.jidouka.test.usecases.TestEnsureEntitySubscribedAndCurrentUseCase
import dev.jidouka.usecases.EnsureEntitySubscribedAndCurrentUseCase
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.TestScope
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.Instant

class EntityRegistryTests : BaseUnitTest() {

    override fun setUp() {
        super.setUp()
        DomainParserRegistry.reset()
    }

    @After
    override fun tearDown() {
        super.tearDown()
        DomainParserRegistry.reset()
    }

    private fun TestScope.newRegistry(
        stateRegistry: StateRegistry,
        ensureSubscribedUseCase: EnsureEntitySubscribedAndCurrentUseCase = TestEnsureEntitySubscribedAndCurrentUseCase()
    ): EntityRegistry {
        return EntityRegistry(
            stateRegistry = stateRegistry,
            ensureSubscribedUseCase = ensureSubscribedUseCase,
            scope = backgroundScope
        )
    }

    private fun stateObject(entityId: String, state: String) = StateObject(
        entityId = entityId,
        state = state,
        attributesRaw = emptyMap(),
        lastChanged = Instant.fromEpochMilliseconds(0),
        lastUpdated = Instant.fromEpochMilliseconds(0),
        lastReported = null,
        context = null,
    )

    @Test
    fun `generic then typed lookup share the same cached instance and both parse as the registered type`() =
        runTestScope {
            DomainParserRegistry.register(Domain(TestState.TEST_DOMAIN, TestState.parser))
            val stateRegistry = HomeAssistantStateRegistry()
            val registry = newRegistry(stateRegistry)

            val generic = registry.get("${TestState.TEST_DOMAIN}.x")
            val typed = registry.get("${TestState.TEST_DOMAIN}.x", Domain<TestState>(TestState.TEST_DOMAIN))

            assertSame<Entity<*>>(generic, typed)

            stateRegistry.setCurrentState("${TestState.TEST_DOMAIN}.x", stateObject("${TestState.TEST_DOMAIN}.x", "on"))

            assertTrue(generic.cachedState is TestState)
            assertTrue(typed.cachedState is TestState)
        }

    @Test
    fun `registry wins over an explicitly-passed parser for the same domain id`() = runTestScope {
        DomainParserRegistry.register(Domain(TestState.TEST_DOMAIN, TestState.parser))
        val stateRegistry = HomeAssistantStateRegistry()
        val registry = newRegistry(stateRegistry)

        val entity = registry.get(
            "${TestState.TEST_DOMAIN}.x",
            Domain(TestState.TEST_DOMAIN, GenericState.parser),
        )

        stateRegistry.setCurrentState("${TestState.TEST_DOMAIN}.x", stateObject("${TestState.TEST_DOMAIN}.x", "on"))

        assertTrue(entity.cachedState is TestState)
    }

    @Test
    fun `unregistered domain falls back to GenericState`() = runTestScope {
        val stateRegistry = HomeAssistantStateRegistry()
        val registry = newRegistry(stateRegistry)

        val entity = registry.get("unregistered_domain.x")

        stateRegistry.setCurrentState("unregistered_domain.x", stateObject("unregistered_domain.x", "off"))

        assertTrue(entity.cachedState is GenericState)
    }

    //region enumeration
    @Test
    fun `allEntityIds returns every entity known to the state registry`() = runTestScope {
        val stateRegistry = HomeAssistantStateRegistry()
        val registry = newRegistry(stateRegistry)

        stateRegistry.setInitialState("light.kitchen", stateObject("light.kitchen", "on"))
        stateRegistry.setInitialState("sensor.hallway_temp", stateObject("sensor.hallway_temp", "70.0"))
        stateRegistry.setInitialState("switch.fan", stateObject("switch.fan", "off"))

        assertEquals(setOf("light.kitchen", "sensor.hallway_temp", "switch.fan"), registry.getAllEntityIds())
    }

    @Test
    fun `entityIdsForDomain returns exactly the matching entity IDs`() = runTestScope {
        val stateRegistry = HomeAssistantStateRegistry()
        val registry = newRegistry(stateRegistry)

        stateRegistry.setInitialState("light.kitchen", stateObject("light.kitchen", "on"))
        stateRegistry.setInitialState("light.hallway", stateObject("light.hallway", "off"))
        stateRegistry.setInitialState("sensor.hallway_temp", stateObject("sensor.hallway_temp", "70.0"))

        assertEquals(setOf("light.kitchen", "light.hallway"), registry.getEntityIdsForDomain("light"))
    }

    @Test
    fun `allEntityIds and entityIdsForDomain never trigger a subscription`() = runTestScope {
        val stateRegistry = HomeAssistantStateRegistry()
        val registry = newRegistry(
            stateRegistry,
            ensureSubscribedUseCase = object : EnsureEntitySubscribedAndCurrentUseCase {
                override suspend fun ensure(entityId: EntityId, automationId: String) {
                    error("ensure() must never be called by enumeration alone (entity: $entityId)")
                }
            })

        stateRegistry.setInitialState("sensor.battery_1", stateObject("sensor.battery_1", "15"))
        stateRegistry.setInitialState("sensor.battery_2", stateObject("sensor.battery_2", "80"))

        assertTrue(registry.getAllEntityIds().isNotEmpty())
        assertTrue(registry.getEntityIdsForDomain("sensor").isNotEmpty())
    }
    //endregion

}