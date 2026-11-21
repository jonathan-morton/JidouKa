package dev.jidouka.automations.registry

import dev.jidouka.BaseUnitTest
import dev.jidouka.automations.AutomationMode
import dev.jidouka.automations.dsl.triggers.TriggerContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AutomationExecutorTests : BaseUnitTest() {

    private lateinit var executionScope: CoroutineScope
    private lateinit var executor: AutomationExecutor
    private lateinit var triggers: Channel<TriggerContext>

    @Before
    override fun setUp() {
        super.setUp()
        // Unconfined finishes a cancelled job inside job.cancel(), so its completion handler
        // runs while cancel() is still looping. That reproduces the race every time.
        executionScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        executor = AutomationExecutor(executionScope)
        triggers = Channel(Channel.UNLIMITED)
    }

    @After
    override fun tearDown() {
        executionScope.cancel()
        super.tearDown()
    }

    @Test
    fun `cancel stops a running action without throwing`() = runTestScope {
        var started = 0
        executor.execute(automation(AutomationMode.Single) { started++; awaitCancellation() })

        triggers.send(TriggerContext.Flow())
        assertEquals(1, started)
        assertTrue(executor.isRunning(AUTOMATION_ID))

        assertTrue(executor.cancel(AUTOMATION_ID))
        assertFalse(executor.isRunning(AUTOMATION_ID))
    }

    @Test
    fun `an automation runs again after it was cancelled`() = runTestScope {
        var started = 0
        executor.execute(automation(AutomationMode.Single) { started++; awaitCancellation() })

        triggers.send(TriggerContext.Flow())
        executor.cancel(AUTOMATION_ID)
        triggers.send(TriggerContext.Flow())

        assertEquals(2, started)
        assertTrue(executor.isRunning(AUTOMATION_ID))
    }

    @Test
    fun `restart cancels the running action and starts a new one`() = runTestScope {
        var started = 0
        var cancelled = 0
        executor.execute(automation(AutomationMode.Restart) {
            started++
            try {
                awaitCancellation()
            } finally {
                cancelled++
            }
        })

        triggers.send(TriggerContext.Flow())
        triggers.send(TriggerContext.Flow())

        assertEquals(2, started)
        assertEquals(1, cancelled)
        assertTrue(executor.isRunning(AUTOMATION_ID))
    }

    @Test
    fun `single ignores a trigger while an action is running`() = runTestScope {
        var started = 0
        executor.execute(automation(AutomationMode.Single) { started++; awaitCancellation() })

        triggers.send(TriggerContext.Flow())
        triggers.send(TriggerContext.Flow())

        assertEquals(1, started)
    }

    @Test
    fun `parallel stops starting actions at its maximum`() = runTestScope {
        var started = 0
        executor.execute(automation(AutomationMode.Parallel(2)) { started++; awaitCancellation() })

        repeat(3) { triggers.send(TriggerContext.Flow()) }

        assertEquals(2, started)
    }

    @Test
    fun `queued automation runs again after it was cancelled`() = runTestScope {
        var started = 0
        executor.execute(automation(AutomationMode.Queued(5)) { started++; awaitCancellation() })

        triggers.send(TriggerContext.Flow())
        assertTrue(executor.cancel(AUTOMATION_ID))
        triggers.send(TriggerContext.Flow())

        assertEquals(2, started)
    }

    @Test
    fun `await returns when the running action finishes`() = runTestScope {
        val release = CompletableDeferred<Unit>()
        executor.execute(automation(AutomationMode.Single) { release.await() })
        triggers.send(TriggerContext.Flow())

        val waiter = async { executor.await(AUTOMATION_ID) }
        runCurrent()
        assertFalse(waiter.isCompleted)

        release.complete(Unit)
        waiter.await()
        assertFalse(executor.isRunning(AUTOMATION_ID))
    }

    private fun automation(
        mode: AutomationMode,
        actions: suspend (TriggerContext) -> Unit
    ) = RegisteredAutomation(
        id = AUTOMATION_ID,
        mode = mode,
        name = AUTOMATION_ID,
        description = null,
        runOnStartup = false,
        triggerFlow = triggers.receiveAsFlow(),
        triggerMetadata = emptyList(),
        evaluateStartup = { null },
        evaluateConditions = null,
        executeActions = actions,
    )

    private companion object {
        const val AUTOMATION_ID = "test_automation"
    }
}
