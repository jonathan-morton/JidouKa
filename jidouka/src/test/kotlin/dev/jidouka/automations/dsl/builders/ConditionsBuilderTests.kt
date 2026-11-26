package dev.jidouka.automations.dsl.builders

import dev.jidouka.BaseUnitTest
import dev.jidouka.automations.dsl.triggers.TriggerContext
import dev.jidouka.automations.registry.AutomationRegistry
import dev.jidouka.registry.EntityRegistry
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ConditionsBuilderTests : BaseUnitTest() {

    @MockK
    private lateinit var automationRegistry: AutomationRegistry

    @MockK
    private lateinit var entityRegistry: EntityRegistry

    private val evaluationTimeout = 30.seconds
    private val pastEvaluationTimeout = evaluationTimeout * 2
    private val otherConditionDelay = 5.seconds

    private fun conditions(vararg predicates: suspend () -> Boolean): ConditionsBuilder {
        return ConditionsBuilder(
            triggerContext = TriggerContext.Time(triggeredAt = Instant.fromEpochMilliseconds(0)),
            automationRegistry = automationRegistry,
            entityRegistry = entityRegistry,
            automationId = "test",
            clock = Clock.System,
            timeZone = TimeZone.UTC,
        ).apply {
            predicates.forEach { condition(it) }
        }
    }

    @Test
    fun `no conditions evaluate to true`() = runTestScope {
        assertTrue(conditions().evaluate())
    }

    @Test
    fun `a single condition evaluates to its own result`() = runTestScope {
        assertTrue(conditions({ true }).evaluate())
        assertFalse(conditions({ false }).evaluate())
    }

    @Test
    fun `a single condition that throws evaluates to false`() = runTestScope {
        assertFalse(conditions({ error("condition failed") }).evaluate())
    }

    @Test
    fun `a single condition that times out on its own evaluates to false`() = runTestScope {
        val builder = conditions({ withTimeout(1.seconds) { delay(10.seconds); true } })

        assertFalse(builder.evaluate())
    }

    @Test
    fun `a single condition running past the timeout evaluates to false after 30 seconds`() = runTestScope {
        val builder = conditions({ delay(pastEvaluationTimeout); true })

        assertFalse(builder.evaluate())
        assertEquals(evaluationTimeout.inWholeMilliseconds, currentTime)
    }

    @Test
    fun `all false conditions evaluate to false`() = runTestScope {
        assertFalse(conditions({ false }, { false }).evaluate())
    }

    @Test
    fun `one true condition among false ones evaluates to true`() = runTestScope {
        assertTrue(conditions({ false }, { true }, { false }).evaluate())
    }

    @Test
    fun `a true condition does not wait for a slower one`() = runTestScope {
        val builder = conditions({ delay(pastEvaluationTimeout); false }, { true })

        assertTrue(builder.evaluate())
        assertEquals(0, currentTime)
    }

    @Test
    fun `a condition that times out on its own does not stop the others`() = runTestScope {
        val builder = conditions(
            { withTimeout(1.seconds) { delay(10.seconds); true } },
            { delay(otherConditionDelay); true },
        )

        assertTrue(builder.evaluate())
        assertEquals(otherConditionDelay.inWholeMilliseconds, currentTime)
    }

    @Test
    fun `conditions running past the timeout evaluate to false after 30 seconds`() = runTestScope {
        val builder = conditions({ delay(pastEvaluationTimeout); true }, { delay(pastEvaluationTimeout); true })

        assertFalse(builder.evaluate())
        assertEquals(evaluationTimeout.inWholeMilliseconds, currentTime)
    }

    @Test
    fun `cancelling the evaluation cancels instead of evaluating to false`() = runTestScope {
        val builder = conditions({ delay(pastEvaluationTimeout); true }, { delay(pastEvaluationTimeout); true })
        var result: Boolean? = null

        val job = launch { result = builder.evaluate() }
        runCurrent()
        job.cancelAndJoin()

        assertNull(result)
    }


}
