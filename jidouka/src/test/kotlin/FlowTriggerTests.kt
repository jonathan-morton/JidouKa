@file:Suppress("LocalVariableName")

package dev.jidouka

import dev.jidouka.api.Jidouka
import dev.jidouka.automations.AutomationMode
import dev.jidouka.test.AutomationTestEnvironment
import dev.jidouka.test.RecordedEvent
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FlowTriggerTests : BaseUnitTest() {

    // region flow() trigger tests

    @Test
    fun `flow trigger fires action when predicate matches`() = runTest {
        val externalFlow = MutableSharedFlow<Int>(replay = 1)

        val `act on high value from external flow` = Jidouka.automation(
            id = "flow_high_value",
            mode = AutomationMode.Single
        ) {
            triggers {
                flow(source = externalFlow) { it > 100 }
            }
            actions {
                actions.call("notify", "mobile_app", data = mapOf("message" to "High value")) {
                    entity("notify.mobile_app")
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`act on high value from external flow`)

            externalFlow.emit(150)
            testScheduler.advanceUntilIdle()

            assertEquals(1, env.events<RecordedEvent.Action>().size)
            assertEquals("notify", env.events<RecordedEvent.Action>()[0].domainId)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `flow trigger does not fire when predicate is false`() = runTest {
        val externalFlow = MutableSharedFlow<Int>(replay = 1)

        val `act on high value from external flow` = Jidouka.automation(
            id = "flow_high_value",
            mode = AutomationMode.Single
        ) {
            triggers {
                flow(source = externalFlow) { it > 100 }
            }
            actions {
                actions.call("notify", "mobile_app") {
                    entity("notify.mobile_app")
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`act on high value from external flow`)

            externalFlow.emit(50)
            testScheduler.advanceUntilIdle()

            assertTrue(env.events<RecordedEvent.Action>().isEmpty())
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `flow trigger with label sets label in trigger context`() = runTest {
        val externalFlow = MutableSharedFlow<String>(replay = 1)
        var capturedByFlow = false
        var capturedByLabelledFlow = false
        var capturedByWrongLabel = false

        val `react to labelled api webhook` = Jidouka.automation(
            id = "labelled_flow",
            mode = AutomationMode.Single
        ) {
            triggers {
                flow(source = externalFlow, label = "api-webhook") { it.isNotEmpty() }
            }
            actions {
                capturedByFlow = triggered.byFlow()
                capturedByLabelledFlow = triggered.byFlow("api-webhook")
                capturedByWrongLabel = triggered.byFlow("wrong-label")
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`react to labelled api webhook`)

            externalFlow.emit("payload")
            testScheduler.advanceUntilIdle()

            assertTrue(capturedByFlow)
            assertTrue(capturedByLabelledFlow)
            assertFalse(capturedByWrongLabel)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `flow trigger with data transformation passes transformed data`() = runTest {
        data class SensorReading(val temperature: Double, val humidity: Double)

        val sensorFlow = MutableSharedFlow<SensorReading>(replay = 1)
        var capturedData: Any? = null

        val `extract temperature from sensor reading` = Jidouka.automation(
            id = "sensor_transform",
            mode = AutomationMode.Single
        ) {
            triggers {
                flow(
                    source = sensorFlow,
                    data = { reading -> reading.temperature }
                ) { it.temperature > 30.0 }
            }
            actions {
                capturedData = triggered.flow()?.data
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`extract temperature from sensor reading`)

            sensorFlow.emit(SensorReading(temperature = 35.0, humidity = 60.0))
            testScheduler.advanceUntilIdle()

            assertEquals(35.0, capturedData)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `flow trigger with distinctUntilChanged suppresses duplicate emissions`() = runTest {
        val externalFlow = MutableSharedFlow<Int>()
        var triggerCount = 0

        val `count distinct threshold crossings` = Jidouka.automation(
            id = "distinct_flow",
            mode = AutomationMode.Parallel(10)
        ) {
            triggers {
                flow(source = externalFlow, distinctUntilChanged = true) { it > 100 }
            }
            actions {
                triggerCount++
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`count distinct threshold crossings`)

            externalFlow.emit(150)
            testScheduler.advanceUntilIdle()
            assertEquals(1, triggerCount)

            externalFlow.emit(150)
            testScheduler.advanceUntilIdle()
            assertEquals(1, triggerCount)
        }
    }

    @Test
    fun `flow trigger with distinctUntilChanged false fires on every emission`() = runTest {
        val externalFlow = MutableSharedFlow<Int>()
        var triggerCount = 0

        val `count every threshold crossing` = Jidouka.automation(
            id = "repeating_flow",
            mode = AutomationMode.Parallel(10)
        ) {
            triggers {
                flow(source = externalFlow, distinctUntilChanged = false) { it > 100 }
            }
            actions {
                triggerCount++
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`count every threshold crossing`)

            externalFlow.emit(150)
            testScheduler.advanceUntilIdle()
            assertEquals(1, triggerCount)

            externalFlow.emit(150)
            testScheduler.advanceUntilIdle()
            assertEquals(2, triggerCount)
        }
    }

    @Test
    fun `multiple flow triggers use OR logic`() = runTest {
        val temperatureFlow = MutableSharedFlow<Double>(replay = 1)
        val humidityFlow = MutableSharedFlow<Double>(replay = 1)

        val `alert on any extreme reading` = Jidouka.automation(
            id = "multi_flow_alert",
            mode = AutomationMode.Parallel(10)
        ) {
            triggers {
                flow(source = temperatureFlow, label = "temperature") { it > 40.0 }
                flow(source = humidityFlow, label = "humidity") { it > 90.0 }
            }
            actions {
                actions.call("notify", "mobile_app") {
                    entity("notify.mobile_app")
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`alert on any extreme reading`)

            temperatureFlow.emit(45.0)
            testScheduler.advanceUntilIdle()

            assertEquals(1, env.events<RecordedEvent.Action>().size)

            env.clearEvents()

            humidityFlow.emit(95.0)
            testScheduler.advanceUntilIdle()

            assertEquals(1, env.events<RecordedEvent.Action>().size)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `flow trigger blocked by failing condition`() = runTest {
        val externalFlow = MutableSharedFlow<Int>(replay = 1)
        val personEntityId = "person.owner"

        val `alert when home on high value` = Jidouka.automation(
            id = "conditional_flow",
            mode = AutomationMode.Single
        ) {
            triggers {
                flow(source = externalFlow) { it > 100 }
            }
            conditions {
                condition {
                    entity(personEntityId).state()?.state == "home"
                }
            }
            actions {
                actions.call("notify", "mobile_app") {
                    entity("notify.mobile_app")
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setEntityState(personEntityId, "not_home")
            env.register(`alert when home on high value`)

            externalFlow.emit(150)
            testScheduler.advanceUntilIdle()

            assertTrue(env.events<RecordedEvent.Action>().isEmpty())
            assertTrue(env.events<RecordedEvent.TriggerFired>().any { it.automationId == "conditional_flow" })
            assertTrue(env.events<RecordedEvent.ConditionFailed>().any { it.automationId == "conditional_flow" })
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    // endregion

    // region sharedFlow() trigger tests

    @Test
    fun `sharedFlow trigger evaluates replay cache on startup when runOnStartup is true`() = runTest {
        val externalFlow = MutableSharedFlow<Int>(replay = 1)

        val `act immediately on cached high value` = Jidouka.automation(
            id = "startup_flow",
            mode = AutomationMode.Single
        ) {
            runOnStartup = true
            triggers {
                sharedFlow(source = externalFlow) { it > 100 }
            }
            actions {
                actions.call("notify", "mobile_app", data = mapOf("message" to "Startup trigger")) {
                    entity("notify.mobile_app")
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            externalFlow.emit(200)

            env.register(`act immediately on cached high value`)

            assertEquals(1, env.events<RecordedEvent.Action>().size)
            assertEquals("notify", env.events<RecordedEvent.Action>()[0].domainId)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `sharedFlow trigger does not fire on startup when predicate is false`() = runTest {
        val externalFlow = MutableSharedFlow<Int>(replay = 1)

        val `act immediately on cached high value` = Jidouka.automation(
            id = "startup_flow_no_match",
            mode = AutomationMode.Single
        ) {
            runOnStartup = true
            triggers {
                sharedFlow(source = externalFlow) { it > 100 }
            }
            actions {
                actions.call("notify", "mobile_app") {
                    entity("notify.mobile_app")
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            externalFlow.emit(50)

            env.register(`act immediately on cached high value`)

            assertTrue(env.events<RecordedEvent.Action>().isEmpty())
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    // endregion

    // region TriggerContext.Flow accessor tests

    @Test
    fun `byFlow returns true when automation triggered by flow`() = runTest {
        val externalFlow = MutableSharedFlow<String>(replay = 1)
        var wasByFlow = false
        var wasByTime = false

        val `detect flow trigger type` = Jidouka.automation(
            id = "flow_type_check",
            mode = AutomationMode.Single
        ) {
            triggers {
                flow(source = externalFlow) { it.isNotEmpty() }
            }
            actions {
                wasByFlow = triggered.byFlow()
                wasByTime = triggered.byTime()
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`detect flow trigger type`)

            externalFlow.emit("data")
            testScheduler.advanceUntilIdle()

            assertTrue(wasByFlow)
            assertFalse(wasByTime)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `byFlow with label returns true for matching label`() = runTest {
        val externalFlow = MutableSharedFlow<String>(replay = 1)
        var matchedLabel = false
        var matchedWrongLabel = false

        val `check flow label match` = Jidouka.automation(
            id = "flow_label_match",
            mode = AutomationMode.Single
        ) {
            triggers {
                flow(source = externalFlow, label = "webhook") { it.isNotEmpty() }
            }
            actions {
                matchedLabel = triggered.byFlow("webhook")
                matchedWrongLabel = triggered.byFlow("other")
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`check flow label match`)

            externalFlow.emit("payload")
            testScheduler.advanceUntilIdle()

            assertTrue(matchedLabel)
            assertFalse(matchedWrongLabel)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `flow context returns data when triggered by flow`() = runTest {
        val externalFlow = MutableSharedFlow<Int>(replay = 1)
        var capturedFlowData: Any? = null

        val `capture flow emission data` = Jidouka.automation(
            id = "flow_data_access",
            mode = AutomationMode.Single
        ) {
            triggers {
                flow(source = externalFlow) { it > 0 }
            }
            actions {
                capturedFlowData = triggered.flow()?.data
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`capture flow emission data`)

            externalFlow.emit(42)
            testScheduler.advanceUntilIdle()

            assertEquals(42, capturedFlowData)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `flow context with label returns context for matching label`() = runTest {
        val externalFlow = MutableSharedFlow<String>(replay = 1)
        var matchedContext: Any? = "not_set"
        var unmatchedContext: Any? = "not_set"

        val `access labelled flow context` = Jidouka.automation(
            id = "flow_label_context",
            mode = AutomationMode.Single
        ) {
            triggers {
                flow(source = externalFlow, label = "sensor") { it.isNotEmpty() }
            }
            actions {
                matchedContext = triggered.flow("sensor")
                unmatchedContext = triggered.flow("wrong")
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`access labelled flow context`)

            externalFlow.emit("reading")
            testScheduler.advanceUntilIdle()

            assertNotNull(matchedContext)
            assertNull(unmatchedContext)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `flow context returns null when triggered by state`() = runTest {
        val motionId = "binary_sensor.motion"
        var flowContext: Any? = "not_set"
        var wasByFlow = false

        val `state trigger does not produce flow context` = Jidouka.automation(
            id = "state_not_flow",
            mode = AutomationMode.Single
        ) {
            triggers {
                state(entity = entity(motionId)) { it?.state == "on" }
            }
            actions {
                flowContext = triggered.flow()
                wasByFlow = triggered.byFlow()
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`state trigger does not produce flow context`)

            env.emitState(motionId, "on")
            testScheduler.advanceUntilIdle()

            assertNull(flowContext)
            assertFalse(wasByFlow)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }
    // endregion

    // region observe() trigger tests

    @Test
    fun `observe registers entity for subscription`() = runTest {
        val temperatureId = "sensor.temperature"
        var triggerCount = 0

        val `react to observed temperature changes` = Jidouka.automation(
            id = "observe_subscription",
            mode = AutomationMode.Parallel(10)
        ) {
            val temperatureEntity = entity(temperatureId)
            triggers {
                val temperature = observe(temperatureEntity)
                flow(temperature) { it != null }
            }
            actions {
                triggerCount++
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`react to observed temperature changes`)

            env.emitState(temperatureId, "22.0")
            testScheduler.advanceUntilIdle()

            assertEquals(1, triggerCount)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `observe returns parsed state changes`() = runTest {
        val temperatureId = "sensor.temperature"
        var capturedData: Any? = null

        val `capture parsed state from observed entity` = Jidouka.automation(
            id = "observe_parsed_state",
            mode = AutomationMode.Single
        ) {
            val temperatureEntity = entity(temperatureId)
            triggers {
                val temperature = observe(temperatureEntity).mapNotNull { it?.state }
                flow(temperature) { it.isNotEmpty() }
            }
            actions {
                capturedData = triggered.flow()?.data
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`capture parsed state from observed entity`)

            val temperatureState = "25.5"
            env.emitState(temperatureId, temperatureState)
            testScheduler.advanceUntilIdle()

            assertEquals(temperatureState, capturedData)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `observe with rolling average triggers on threshold`() = runTest {
        val temperatureId = "sensor.outside_temp"
        var capturedAverage: Any? = null

        val `alert when rolling average exceeds threshold` = Jidouka.automation(
            id = "rolling_avg_temp",
            mode = AutomationMode.Single
        ) {
            val temperatureEntity = entity(temperatureId)
            triggers {
                val rollingAvg = observe(temperatureEntity)
                    .mapNotNull { it?.state?.toDoubleOrNull() }
                    .runningFold(emptyList<Double>()) { window, v -> (window + v).takeLast(3) }
                    .map {
                        if (it.isEmpty()) {
                            0.0
                        } else {
                            it.average()
                        }
                    }

                flow(rollingAvg) { avg -> avg > 30.0 }
            }
            actions {
                capturedAverage = triggered.flow()?.data
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`alert when rolling average exceeds threshold`)

            env.emitState(temperatureId, "25.0")
            testScheduler.advanceUntilIdle()
            assertNull(capturedAverage)

            env.emitState(temperatureId, "28.0")
            testScheduler.advanceUntilIdle()
            assertNull(capturedAverage)

            env.emitState(temperatureId, "40.0")
            testScheduler.advanceUntilIdle()

            assertNotNull(capturedAverage)
            assertEquals(31.0, capturedAverage as Double, 0.01)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `observe multiple entities combined with stdlib combine`() = runTest {
        val temperatureId = "sensor.temperature"
        val humidityId = "sensor.humidity"

        val `alert on high heat index from combined observations` = Jidouka.automation(
            id = "heat_index_alert",
            mode = AutomationMode.Single
        ) {
            val temperatureEntity = entity(temperatureId)
            val humidityEntity = entity(humidityId)
            triggers {
                val temperature = observe(temperatureEntity).mapNotNull { it?.state?.toDoubleOrNull() }
                val humidity = observe(humidityEntity).mapNotNull { it?.state?.toDoubleOrNull() }

                val heatIndex = combine(temperature, humidity) { t, h ->
                    t + (0.5 * h)
                }

                flow(heatIndex) { it > 100.0 }
            }
            actions {
                actions.call("notify", "mobile_app", data = mapOf("message" to "Heat index high")) {
                    entity("notify.mobile_app")
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`alert on high heat index from combined observations`)

            env.emitState(temperatureId, "80.0")
            testScheduler.advanceUntilIdle()
            assertTrue(env.events<RecordedEvent.Action>().isEmpty())

            env.emitState(humidityId, "30.0")
            testScheduler.advanceUntilIdle()
            assertTrue(env.events<RecordedEvent.Action>().isEmpty())

            env.emitState(humidityId, "50.0")
            testScheduler.advanceUntilIdle()

            assertEquals(1, env.events<RecordedEvent.Action>().size)
            assertEquals("notify", env.events<RecordedEvent.Action>()[0].domainId)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    // endregion
}