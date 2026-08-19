package dev.jidouka.registry

import dev.jidouka.BaseUnitTest
import dev.jidouka.components.Domain
import dev.jidouka.state.TestState
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

        assertSame(TestState.parser, domain?.entityParser)
    }

    @Test
    fun `installFromClasspath is idempotent`() {
        DomainParserRegistry.installFromClasspath()
        DomainParserRegistry.installFromClasspath()

        val domain = DomainParserRegistry.getDomain(TestState.TEST_DOMAIN)

        assertSame(TestState.parser, domain?.entityParser)
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
}