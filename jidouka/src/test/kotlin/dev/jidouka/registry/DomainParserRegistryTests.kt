@file:Suppress("KotlinMisorderedAssertEqualsArguments")

package dev.jidouka.registry

import dev.jidouka.BaseUnitTest
import dev.jidouka.components.BaseState
import dev.jidouka.components.Domain
import dev.jidouka.components.TestState
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DomainParserRegistryTests : BaseUnitTest() {
    @Before
    override fun setUp() {
        super.setUp()
        DomainParserRegistry.reset()
    }

    @After
    override fun tearDown() {
        super.tearDown()
        DomainParserRegistry.reset()
    }

    @Test
    fun `installFromClasspath discovers the test state provider`() {
        DomainParserRegistry.installFromClasspath()

        val domain = DomainParserRegistry.getDomain(TestState.TEST_DOMAIN)

        assertSame<BaseState.Parser<*>?>(TestState.parser, domain?.entityParser)
    }

    @Test
    fun `installFromClasspath is idempotent`() {
        DomainParserRegistry.installFromClasspath()
        DomainParserRegistry.installFromClasspath()

        val domain = DomainParserRegistry.getDomain(TestState.TEST_DOMAIN)

        assertSame<BaseState.Parser<*>?>(TestState.parser, domain?.entityParser)
    }

    @Test
    fun `unknown domain lookup returns null`() {
        assertNull(DomainParserRegistry.getDomain("nonexistent"))
    }

    @Test
    fun `register returns true for a clean registration`() {
        val result = DomainParserRegistry.register(Domain(TestState.TEST_DOMAIN, TestState.parser))

        assertTrue(result)
    }

    @Test
    fun `register returns false when re-registering an already registered domain id`() {
        DomainParserRegistry.register(Domain(TestState.TEST_DOMAIN, TestState.parser))

        val result = DomainParserRegistry.register(Domain<TestState>(TestState.TEST_DOMAIN))

        assertFalse(result)
    }

    @Test
    fun `re-registering a domain id overwrites the previous registration`() {
        val first = Domain(TestState.TEST_DOMAIN, TestState.parser)
        val second = Domain<TestState>(TestState.TEST_DOMAIN)

        DomainParserRegistry.register(first)
        assertSame(DomainParserRegistry.getDomain(TestState.TEST_DOMAIN), first)

        DomainParserRegistry.register(second)
        assertSame(DomainParserRegistry.getDomain(TestState.TEST_DOMAIN), second)
    }

    @Test
    fun `registering distinct domain ids each return true`() {
        val firstResult = DomainParserRegistry.register(Domain(TestState.TEST_DOMAIN, TestState.parser))
        val secondResult = DomainParserRegistry.register(Domain<TestState>("other_domain"))

        assertTrue(firstResult)
        assertTrue(secondResult)
    }

    @Test
    fun `installFromClasspath discovers the test provider's entity parsers`() {
        DomainParserRegistry.installFromClasspath()

        assertSame(TestState.Zone.parser, DomainParserRegistry.getEntityParser(TestState.ZONE_ENTITY_ID))
    }

    @Test
    fun `unknown entity parser lookup returns null`() {
        assertNull(DomainParserRegistry.getEntityParser(UNKNOWN_ENTITY_ID))
    }

    @Test
    fun `registerEntityParser returns true for a clean registration`() {
        val result = DomainParserRegistry.registerEntityParser(TestState.ZONE_ENTITY_ID, TestState.Zone.parser)

        assertTrue(result)
    }

    @Test
    fun `registerEntityParser returns false when re-registering an entity id`() {
        DomainParserRegistry.registerEntityParser(TestState.ZONE_ENTITY_ID, TestState.Zone.parser)

        val result = DomainParserRegistry.registerEntityParser(TestState.ZONE_ENTITY_ID, TestState.parser)

        assertFalse(result)
    }

    @Test
    fun `reset clears entity parsers`() {
        DomainParserRegistry.registerEntityParser(TestState.ZONE_ENTITY_ID, TestState.Zone.parser)

        DomainParserRegistry.reset()

        assertNull(DomainParserRegistry.getEntityParser(TestState.ZONE_ENTITY_ID))
    }

    private companion object {
        const val UNKNOWN_ENTITY_ID = "${TestState.TEST_DOMAIN}.nonexistent"
    }
}