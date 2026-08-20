package dev.jidouka.registry

import dev.jidouka.BaseUnitTest
import dev.jidouka.components.Domain
import dev.jidouka.components.Entity
import dev.jidouka.components.GenericState
import dev.jidouka.components.StateObject
import dev.jidouka.state.TestState
import dev.jidouka.test.usecases.TestEnsureEntitySubscribedAndCurrentUseCase
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.TestScope
import org.junit.After
import kotlin.test.Test
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

    private fun TestScope.newRegistry(stateRegistry: StateRegistry): EntityRegistry {
        return EntityRegistry(
            stateRegistry = stateRegistry,
            ensureSubscribedUseCase = TestEnsureEntitySubscribedAndCurrentUseCase(),
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

}