package dev.jidouka

import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

/**
 * Base class for unit tests providing common setup and utilities.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class BaseUnitTest {

    /**
     * Test dispatcher for coroutines.
     */
    protected val testDispatcher: TestDispatcher = StandardTestDispatcher()

    /**
     * Test scope for launching coroutines in tests.
     */
    protected val testScope: TestScope = TestScope(testDispatcher)

    @Before
    open fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    open fun tearDown() {
        clearAllMocks()
        unmockkAll()

        Dispatchers.resetMain()
    }

    /**
     * Helper function to run a test with the test scope.
     */
    protected fun runTestScope(block: suspend TestScope.() -> Unit) = testScope.runTest { block() }
}
