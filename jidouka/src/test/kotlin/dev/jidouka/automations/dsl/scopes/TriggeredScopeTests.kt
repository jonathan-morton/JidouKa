package dev.jidouka.automations.dsl.scopes

import dev.jidouka.BaseUnitTest
import dev.jidouka.aliases.EntityId
import dev.jidouka.automations.dsl.triggers.TriggerContext
import dev.jidouka.components.Domain
import dev.jidouka.components.Entity
import dev.jidouka.components.GenericState
import dev.jidouka.components.StateObject
import dev.jidouka.components.StateTransition
import dev.jidouka.components.TestState
import dev.jidouka.registry.DomainParserRegistry
import dev.jidouka.registry.EntityRegistry
import dev.jidouka.registry.HomeAssistantStateRegistry
import dev.jidouka.test.usecases.TestEnsureEntitySubscribedAndCurrentUseCase
import kotlinx.coroutines.test.TestScope
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class TriggeredScopeTests : BaseUnitTest() {

    private val testDomain = Domain<GenericState>(TestState.TEST_DOMAIN)
    private val typedTestDomain = Domain(TestState.TEST_DOMAIN, TestState.parser)

    override fun setUp() {
        super.setUp()
        DomainParserRegistry.reset()
    }

    @After
    override fun tearDown() {
        super.tearDown()
        DomainParserRegistry.reset()
    }

    private fun TestScope.newRegistry(): EntityRegistry {
        return EntityRegistry(
            stateRegistry = HomeAssistantStateRegistry(),
            ensureSubscribedUseCase = TestEnsureEntitySubscribedAndCurrentUseCase(),
            scope = backgroundScope
        )
    }

    private fun stateObject(entityId: EntityId, state: String) = StateObject(
        entityId = entityId,
        state = state,
        attributesRaw = emptyMap(),
        lastChanged = Instant.fromEpochMilliseconds(0),
        lastUpdated = Instant.fromEpochMilliseconds(0),
        lastReported = null,
        context = null,
    )

    private fun transition(entityId: EntityId, from: String?, to: String) = StateTransition(
        toState = stateObject(entityId, to),
        fromState = from?.let { stateObject(entityId, it) }
    )

    private fun singleState(entityId: EntityId, from: String? = "off", to: String = "on") =
        TriggerContext.StateContext.State(transition(entityId, from, to))

    @Test
    fun `state with a built-in domain returns the state of the entity that fired`() = runTestScope {
        val triggered = TriggeredScope(singleState("light.kitchen"), newRegistry())

        val result = triggered.state(Domain.Light)

        assertNotNull(result)
        assertEquals("light.kitchen", result.entityId)
        assertEquals("on", result.state.stateRaw)
        assertEquals("off", result.previousState?.stateRaw)
    }

    @Test
    fun `state with a domain returns the registered type`() = runTestScope {
        DomainParserRegistry.register(typedTestDomain)
        val triggered = TriggeredScope(singleState("${TestState.TEST_DOMAIN}.x"), newRegistry())

        val result = triggered.state(Domain(TestState.TEST_DOMAIN, GenericState.parser))

        assertNotNull(result)
        assertIs<TestState>(result.state)
        assertIs<TestState>(result.previousState)
    }

    @Test
    fun `state with a domain returns null when the entity that fired is in another domain`() = runTestScope {
        val triggered = TriggeredScope(singleState("switch.fan"), newRegistry())

        assertNull(triggered.state(Domain.Light))
    }

    @Test
    fun `state without arguments returns the registered type`() = runTestScope {
        DomainParserRegistry.register(typedTestDomain)
        val triggered = TriggeredScope(singleState("${TestState.TEST_DOMAIN}.x"), newRegistry())

        val result = triggered.state()

        assertNotNull(result)
        assertIs<TestState>(result.state)
        assertIs<TestState>(result.previousState)
    }

    @Test
    fun `all state lookups return the same class for the same transition`() = runTestScope {
        DomainParserRegistry.register(typedTestDomain)
        val registry = newRegistry()
        val entity = registry.get("${TestState.TEST_DOMAIN}.x", typedTestDomain)
        val triggered = TriggeredScope(singleState(entity.entityId), registry)

        assertIs<TestState>(triggered.state()?.state)
        assertIs<TestState>(triggered.state(testDomain)?.state)
        assertIs<TestState>(triggered.state(entity)?.state)
        assertIs<TestState>(triggered.state(listOf(entity))?.state)
    }

    @Test
    fun `state with a list returns the typed state of the entity that fired`() = runTestScope {
        val registry = newRegistry()
        val first = registry.get("${TestState.TEST_DOMAIN}.a", typedTestDomain)
        val second = registry.get("${TestState.TEST_DOMAIN}.b", typedTestDomain)
        val triggered = TriggeredScope(singleState(second.entityId), registry)

        val result = triggered.state(listOf(first, second))

        assertNotNull(result)
        assertEquals(second.entityId, result.entityId)
        val state: TestState = result.state
        assertEquals("on", state.stateRaw)
        assertEquals("off", result.previousState?.stateRaw)
    }

    @Test
    fun `state with a list returns null when the entity that fired is not in the list`() = runTestScope {
        val registry = newRegistry()
        val listed = registry.get("${TestState.TEST_DOMAIN}.a", typedTestDomain)
        val triggered = TriggeredScope(singleState("${TestState.TEST_DOMAIN}.other"), registry)

        assertNull(triggered.state(listOf(listed)))
    }

    @Test
    fun `state with a list returns null for a combined trigger`() = runTestScope {
        val registry = newRegistry()
        val first = registry.get("${TestState.TEST_DOMAIN}.a", typedTestDomain)
        val second = registry.get("${TestState.TEST_DOMAIN}.b", typedTestDomain)
        val combined = TriggerContext.StateContext.States(
            listOf(
                transition(first.entityId, from = "off", to = "on"),
                transition(second.entityId, from = "off", to = "on"),
            )
        )
        val triggered = TriggeredScope(combined, registry)

        assertNull(triggered.state(listOf(first, second)))
    }

    @Test
    fun `state with a domain returns the per-entity type`() = runTestScope {
        registerZoneParsers()
        val triggered = TriggeredScope(singleState(TestState.ZONE_ENTITY_ID), newRegistry())

        val result = triggered.state(typedTestDomain)

        assertNotNull(result)
        assertIs<TestState.Zone>(result.state)
        assertIs<TestState.Zone>(result.previousState)
    }

    @Test
    fun `state with a list returns the per-entity type`() = runTestScope {
        registerZoneParsers()
        val registry = newRegistry()
        val zone: Entity<TestState.Zone> = registry.get(TestState.ZONE_ENTITY_ID, ZONE_DOMAIN)
        val other = registry.get(OTHER_ENTITY_ID, typedTestDomain)
        val triggered = TriggeredScope(singleState(zone.entityId), registry)

        val result = triggered.state(listOf(other, zone))

        assertNotNull(result)
        assertIs<TestState.Zone>(result.state)
        assertIs<TestState.Zone>(result.previousState)
    }

    private fun registerZoneParsers() {
        DomainParserRegistry.register(typedTestDomain)
        DomainParserRegistry.registerEntityParser(TestState.ZONE_ENTITY_ID, TestState.Zone.parser)
    }

    private companion object {
        val ZONE_DOMAIN = Domain<TestState.Zone>(TestState.TEST_DOMAIN)
        const val OTHER_ENTITY_ID = "${TestState.TEST_DOMAIN}.other"
    }
}
