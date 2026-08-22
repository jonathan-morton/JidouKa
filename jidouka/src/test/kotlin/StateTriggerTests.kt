@file:Suppress("LocalVariableName")

package dev.jidouka

import dev.jidouka.api.Jidouka
import dev.jidouka.automations.AutomationMode
import dev.jidouka.test.AutomationTestEnvironment
import dev.jidouka.test.RecordedEvent
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class StateTriggerTests : BaseUnitTest() {

    val hallwayLightId = "light.hallway"
    val hallwayMotionId = "binary_sensor.hallway_motion"

    private val sunRisingId = "sensor.home_sun_rising"
    private val sunSettingId = "sensor.home_sun_setting"
    private val outletHeaterId = "switch.main_bathroom_outlet"

    @Test
    fun `state trigger fires action when predicate matches`() = runTest {
        val `turn on hallway light on motion` = Jidouka.automation(
            id = "motion_light",
            mode = AutomationMode.Single
        ) {
            triggers {
                state(entity = entity(hallwayMotionId)) { it?.stateRaw == "on" }
            }
            actions {
                val hallwayLight = entity(hallwayLightId)
                actions.call("light", "turn_on") {
                    entity(hallwayLight)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`turn on hallway light on motion`)

            env.emitState("binary_sensor.hallway_motion", "on")

            assertEquals(1, env.events<RecordedEvent.Action>().size)
            assertEquals("light", env.events<RecordedEvent.Action>()[0].domainId)
            assertEquals("turn_on", env.events<RecordedEvent.Action>()[0].action)
            assertTrue(env.events<RecordedEvent.Action>()[0].target?.entityIds?.contains("light.hallway") == true)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `state trigger fires action when predicate matches with serializable data`() = runTest {
        val id = "test_with_medicine_box"
        val medicineContactId = "binary_sensor.medicine_container_contact"
        val message = "test"

        @Serializable
        @SerialName("data")
        data class NotificationData(
            @SerialName("message")
            val message: String,
            @SerialName("title")
            val title: String? = null,
        )

        val `send notification medicine has been taken` = Jidouka.automation(id) {
            triggers {
                val medicineContactSensor = entity(medicineContactId)
                state(medicineContactSensor) { contact ->
                    contact ?: return@state false
                    contact.stateRaw == "off"
                }
            }

            actions {
                actions.call(
                    domain = "notify",
                    action = "mobile_app_mac_studio",
                    data = NotificationData(message = message),
                )
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`send notification medicine has been taken`)

            env.emitState(medicineContactId, "off")

            assertEquals(1, env.events<RecordedEvent.Action>().size)
            assertEquals("notify", env.events<RecordedEvent.Action>()[0].domainId)
            assertEquals("mobile_app_mac_studio", env.events<RecordedEvent.Action>()[0].action)

            val notificationData = env.events<RecordedEvent.Action>()[0].data
            assertEquals(message, notificationData["message"])

            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `state trigger does not fire when predicate is false`() = runTest {
        val `turn on hallway light on motion` = Jidouka.automation(
            id = "motion_light",
            mode = AutomationMode.Single
        ) {
            triggers {
                state(entity = entity(hallwayMotionId)) { it?.stateRaw == "on" }
            }
            actions {
                val hallwayLight = entity(hallwayLightId)
                actions.call("light", "turn_on") {
                    entity(hallwayLight)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`turn on hallway light on motion`)

            env.emitState("binary_sensor.hallway_motion", "off")

            assertTrue(env.events<RecordedEvent.Action>().isEmpty())
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `conditions block gates action execution`() = runTest {
        val motionEntityId = "binary_sensor.motion"
        val hallwayLightId = "light.hallway"

        val `turn on light at night on motion` = Jidouka.automation(
            id = "night_light",
            mode = AutomationMode.Single
        ) {
            triggers {
                state(entity = entity(motionEntityId)) { it?.stateRaw == "on" }
            }
            conditions {
                condition {
                    val sunRiseState = entity(sunRisingId).state()
                    val sunSetState = entity(sunSettingId).state()

                    val sunRiseTimeString = sunRiseState?.attributesRaw?.get("today") as? String
                    val sunSetTimeString = sunSetState?.attributesRaw?.get("today") as? String

                    if (sunRiseTimeString != null && sunSetTimeString != null) {
                        val sunRiseTime = Instant.parse(sunRiseTimeString).toLocalDateTime(time.timeZone).time
                        val sunSetTime = Instant.parse(sunSetTimeString).toLocalDateTime(time.timeZone).time

                        time.isBetween(sunSetTime, sunRiseTime)
                    } else {
                        false
                    }
                }
            }
            actions {
                actions.call("light", "turn_on") {
                    entity(hallwayLightId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setEntityState(sunRisingId, "sensor", attributes = mapOf("today" to "2025-03-08T06:30:00Z"))
            env.setEntityState(sunSettingId, "sensor", attributes = mapOf("today" to "2025-03-08T18:30:00Z"))

            env.setClockTime(LocalDateTime(2025, 3, 8, 15, 0))
            env.register(`turn on light at night on motion`)

            env.emitState(motionEntityId, "on")

            assertTrue(env.events<RecordedEvent.Action>().isEmpty())
            assertTrue(env.events<RecordedEvent.TriggerFired>().any { it.automationId == "night_light" })
            assertTrue(env.events<RecordedEvent.ConditionFailed>().any { it.automationId == "night_light" })
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())

            env.clearEvents()
            // Set time to after sunset
            env.setClockTime(LocalDateTime(year = 2025, month = 3, day = 8, hour = 21, minute = 0))

            env.emitState(motionEntityId, "off") // Reset
            env.emitState(motionEntityId, "on")

            assertEquals(1, env.events<RecordedEvent.Action>().size)
            assertEquals("light", env.events<RecordedEvent.Action>()[0].domainId)
            assertEquals("turn_on", env.events<RecordedEvent.Action>()[0].action)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `delayed action executes after clock advance`() = runTest {
        val garageDoorId = "cover.garage_door"
        val garageLightId = "light.garage"

        val `turn off garage light after door closes` = Jidouka.automation(
            id = "garage_light",
            mode = AutomationMode.Single
        ) {
            triggers {
                state(entity = entity(garageDoorId)) { it?.stateRaw == "closed" }
            }
            actions {
                delay(5.minutes)
                actions.call("light", "turn_off") {
                    entity(garageLightId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`turn off garage light after door closes`)

            env.emitState(garageDoorId, "closed")

            assertTrue(env.events<RecordedEvent.Action>().isEmpty())

            env.advanceClock(5.minutes)
            env.awaitCompletion("garage_light")

            assertEquals(1, env.events<RecordedEvent.Action>().size)
            assertEquals("light", env.events<RecordedEvent.Action>()[0].domainId)
            assertEquals("turn_off", env.events<RecordedEvent.Action>()[0].action)
            assertTrue(env.events<RecordedEvent.Action>()[0].target?.entityIds?.contains(garageLightId) == true)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `automation can cancel another automation`() = runTest {
        val bathroomMotionId = "binary_sensor.bathroom_motion"
        val bathroomDoorId = "binary_sensor.bathroom_door"

        val `turn on heater after motion delay` = Jidouka.automation(
            id = "heater_on",
            mode = AutomationMode.Single
        ) {
            triggers {
                state(entity = entity(bathroomMotionId)) { it?.stateRaw == "on" }
            }
            actions {
                delay(10.minutes)
                actions.call("switch", "turn_on") {
                    entity(outletHeaterId)
                }
            }
        }

        val `cancel heater when door opens` = Jidouka.automation(
            id = "heater_off",
            mode = AutomationMode.Single
        ) {
            triggers {
                state(entity = entity(bathroomDoorId)) { it?.stateRaw == "on" }
            }
            actions {
                automations.cancel("heater_on")
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`turn on heater after motion delay`)
            env.register(`cancel heater when door opens`)

            env.emitState(bathroomMotionId, "on")
            assertTrue(env.isRunning("heater_on"))

            assertTrue(env.events<RecordedEvent.Action>().isEmpty())

            env.emitState(bathroomDoorId, "on")

            assertFalse(env.isRunning("heater_on"))

            env.advanceClock(10.minutes)

            assertTrue(env.events<RecordedEvent.Action>().isEmpty())
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `combineState with 2 entities triggers when both conditions met`() = runTest {
        val temperatureId = "sensor.bathroom_temperature_temperature"
        val humidityId = "sensor.bathroom_temperature_humidity"
        val fanId = "switch.bathroom_fan"

        val `turn on fan when hot and humid` = Jidouka.automation(
            id = "humidity_control",
            mode = AutomationMode.Single
        ) {
            triggers {
                combineState(
                    entity1 = entity(temperatureId),
                    entity2 = entity(humidityId)
                ) { temp, humidity ->
                    val tempValue = temp?.stateRaw?.toDoubleOrNull() ?: return@combineState false
                    val humidityValue = humidity?.stateRaw?.toDoubleOrNull() ?: return@combineState false
                    tempValue > 75.0 && humidityValue > 65.0
                }
            }
            actions {
                actions.call("switch", "turn_on") {
                    entity(fanId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`turn on fan when hot and humid`)

            // Set only temperature high - should not trigger
            env.emitState(temperatureId, "78.0")
            assertTrue(env.events<RecordedEvent.Action>().isEmpty())

            // Set humidity high as well - should trigger
            env.emitState(humidityId, "70.0")
            assertEquals(1, env.events<RecordedEvent.Action>().size)
            assertEquals("switch", env.events<RecordedEvent.Action>()[0].domainId)
            assertEquals("turn_on", env.events<RecordedEvent.Action>()[0].action)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `combineState 3 entities re-evaluates on each entity change`() = runTest {
        val motionId = "binary_sensor.hallway_motion"
        val doorId = "binary_sensor.front_door"
        val lightId = "light.hallway"
        val alarmId = "alarm_control_panel.home"

        val `trigger alarm on motion with door closed lights off` = Jidouka.automation(
            id = "security_check",
            mode = AutomationMode.Single
        ) {
            triggers {
                combineState(
                    entity1 = entity(motionId),
                    entity2 = entity(doorId),
                    entity3 = entity(lightId)
                ) { motion, door, light ->
                    motion?.stateRaw == "on" && door?.stateRaw == "off" && light?.stateRaw == "off"
                }
            }
            actions {
                actions.call("alarm_control_panel", "alarm_trigger") {
                    entity(alarmId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`trigger alarm on motion with door closed lights off`)

            env.emitState(doorId, "off")
            env.emitState(lightId, "off")
            assertTrue(env.events<RecordedEvent.Action>().isEmpty())

            // Motion detected - should trigger alarm
            env.emitState(motionId, "on")
            assertEquals(1, env.events<RecordedEvent.Action>().size)
            assertEquals("alarm_control_panel", env.events<RecordedEvent.Action>()[0].domainId)
            assertEquals("alarm_trigger", env.events<RecordedEvent.Action>()[0].action)

            env.clearEvents()

            // Turn on lights - motion still on but lights on, should not trigger
            env.emitState(lightId, "on")
            env.emitState(motionId, "off")
            env.emitState(motionId, "on")
            assertTrue(env.events<RecordedEvent.Action>().isEmpty())
        }
    }

    @Test
    fun `combineState with distinctUntilChanged false triggers on every update`() = runTest {
        val sensor1Id = "sensor.temperature"
        val sensor2Id = "sensor.humidity"
        var triggerCount = 0

        val `log every sensor reading` = Jidouka.automation(
            id = "repeating_check",
            mode = AutomationMode.Parallel(10)
        ) {
            triggers {
                combineState(
                    entity1 = entity(sensor1Id),
                    entity2 = entity(sensor2Id),
                    distinctUntilChanged = false
                ) { temp, humidity ->
                    temp?.stateRaw != null && humidity?.stateRaw != null
                }
            }
            actions {
                triggerCount++
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`log every sensor reading`)

            env.emitState(sensor1Id, "72.0")
            env.emitState(sensor2Id, "50.0")
            testScheduler.advanceUntilIdle()
            assertEquals(1, triggerCount)

            env.emitState(sensor1Id, "72.5")
            testScheduler.advanceUntilIdle()
            assertEquals(2, triggerCount)
        }
    }

    @Test
    fun `state trigger predicate receives previous state via transition`() = runTest {
        val thermostatId = "climate.living_room"
        val notifyId = "notify.mobile_app"

        val `alert on large temperature swing` = Jidouka.automation(
            id = "temp_change_alert",
            mode = AutomationMode.Single
        ) {
            triggers {
                state(entity = entity(thermostatId)) { currentState ->
                    val current = currentState?.attributesRaw?.get("current_temperature") as? String
                    val previous = currentState?.previous?.attributesRaw?.get("current_temperature") as? String

                    if (current != null && previous != null) {
                        val currentTemp = current.toDoubleOrNull() ?: 0.0
                        val previousTemp = previous.toDoubleOrNull() ?: 0.0
                        abs(currentTemp - previousTemp) > 5.0
                    } else {
                        false
                    }
                }
            }

            actions {
                actions.call(
                    "notify", "mobile_app",
                    data = mapOf("message" to "Large temperature change detected!")
                ) {
                    entity(notifyId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setEntityState(
                thermostatId, "heat",
                attributes = mapOf("current_temperature" to "70.0")
            )

            env.register(`alert on large temperature swing`)

            // Small change - should not trigger (70 -> 72 = 2 degree change)
            env.emitState(
                thermostatId, "heat",
                attributes = mapOf("current_temperature" to "72.0")
            )
            testScheduler.advanceUntilIdle()
            assertTrue(env.events<RecordedEvent.Action>().isEmpty())

            // Large change - should trigger (72 -> 78 = 6 degree change)
            env.emitState(
                thermostatId, "heat",
                attributes = mapOf("current_temperature" to "78.0")
            )
            testScheduler.advanceUntilIdle()
            assertEquals(1, env.events<RecordedEvent.Action>().size)
            assertEquals("notify", env.events<RecordedEvent.Action>()[0].domainId)
        }
    }

    @Test
    fun `multiple state triggers act as OR condition`() = runTest {
        val motion1Id = "binary_sensor.kitchen_motion"
        val motion2Id = "binary_sensor.dining_motion"
        val doorId = "binary_sensor.patio_door"
        val lightId = "light.main_area"

        val `turn on light from any activity sensor` = Jidouka.automation(
            id = "multi_trigger_light",
            mode = AutomationMode.Single
        ) {
            triggers {
                state(entity = entity(motion1Id)) { it?.stateRaw == "on" }
                state(entity = entity(motion2Id)) { it?.stateRaw == "on" }
                state(entity = entity(doorId)) { it?.stateRaw == "on" }
            }
            actions {
                actions.call("light", "turn_on") {
                    entity(lightId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`turn on light from any activity sensor`)

            env.emitState(motion1Id, "on")
            assertEquals(1, env.events<RecordedEvent.Action>().size)

            env.clearEvents()

            env.emitState(motion2Id, "on")
            assertEquals(1, env.events<RecordedEvent.Action>().size)

            env.clearEvents()

            env.emitState(doorId, "on")
            assertEquals(1, env.events<RecordedEvent.Action>().size)

            env.events<RecordedEvent.Action>().forEach { action ->
                assertEquals("light", action.domainId)
                assertEquals("turn_on", action.action)
                assertTrue(action.target?.entityIds?.contains(lightId) == true)
            }
        }
    }

    @Test
    fun `state transition from specific value triggers action`() = runTest {
        val alarmId = "alarm_control_panel.home"
        val notifyId = "notify.mobile_app"

        val `notify when alarm disarmed from armed away` = Jidouka.automation(
            id = "alarm_disarmed",
            mode = AutomationMode.Single
        ) {
            triggers {
                state(entity = entity(alarmId)) { currentState ->
                    // Trigger only when transitioning from armed to disarmed
                    currentState?.previous?.stateRaw == "armed_away" && currentState.stateRaw == "disarmed"
                }
            }
            actions {
                actions.call(
                    "notify", "mobile_app",
                    data = mapOf("message" to "Alarm has been disarmed")
                ) {
                    entity(notifyId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setEntityState(alarmId, "disarmed")

            env.register(`notify when alarm disarmed from armed away`)

            env.emitState(alarmId, "armed_away")
            testScheduler.advanceUntilIdle()
            assertTrue(env.events<RecordedEvent.Action>().isEmpty())

            env.emitState(alarmId, "armed_home")
            testScheduler.advanceUntilIdle()
            assertTrue(env.events<RecordedEvent.Action>().isEmpty())

            env.emitState(alarmId, "armed_away")
            testScheduler.advanceUntilIdle()
            assertTrue(env.events<RecordedEvent.Action>().isEmpty())

            env.emitState(alarmId, "disarmed")
            testScheduler.advanceUntilIdle()
            assertEquals(1, env.events<RecordedEvent.Action>().size)
            assertEquals("notify", env.events<RecordedEvent.Action>()[0].domainId)
        }
    }

    @Test
    fun `sequential actions with delay both execute to completion`() = runTest {
        val windowId = "binary_sensor.window"

        val `turn on heater then off after delay when window closes` = Jidouka.automation(
            id = "heater",
            mode = AutomationMode.Single
        ) {
            triggers { state(entity(windowId)) { it?.stateRaw == "closed" } }
            actions {
                actions.call("switch", "turn_on") {
                    entity(outletHeaterId)
                }
                delay(30.minutes)
                actions.call("switch", "turn_off") {
                    entity(outletHeaterId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`turn on heater then off after delay when window closes`)

            env.emitState(windowId, "closed")
            env.advanceClock(30.minutes)
            env.awaitCompletion("heater")

            val actions = env.events<RecordedEvent.Action>()
            assertEquals(2, actions.size)
            assertEquals("turn_on", actions[0].action)
            assertEquals("turn_off", actions[1].action)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `restart cancellation recorded as cancelled not failed`() = runTest {
        val hallwayId = "binary_sensor.hallway"
        val hallwayLightId = "light.hallway"

        val `turn on then off hallway light on motion` = Jidouka.automation(
            id = "motion_light",
            mode = AutomationMode.Restart
        ) {
            triggers {
                state(entity(hallwayId)) {
                    it?.stateRaw == "on"
                }
            }
            actions {
                actions.call("light", "turn_on") {
                    entity(hallwayLightId)
                }
                delay(5.minutes)
                actions.call("light", "turn_off") {
                    entity(hallwayLightId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`turn on then off hallway light on motion`)

            env.emitState(hallwayId, "on")

            env.advanceClock(2.minutes)

            env.emitState(hallwayId, "off")
            env.emitState(hallwayId, "on")

            env.advanceClock(5.minutes)

            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
            val completions = env.events<RecordedEvent.AutomationCompleted>()
            assertTrue("Expected at least one cancelled completion", completions.any { it.cancelled })
            assertTrue("Expected at least one non-cancelled completion", completions.any { it.cancelled.not() })
        }
    }

    @Test
    fun `state trigger with distinctUntilChanged suppresses re-fire when predicate result unchanged`() = runTest {
        val sensorId = "sensor.temperature"
        val fanId = "switch.fan"

        val `turn on fan when temperature state is hot` = Jidouka.automation(
            id = "temp_fan",
            mode = AutomationMode.Parallel(5)
        ) {
            triggers {
                state(entity = entity(sensorId)) { it?.stateRaw == "hot" }
            }
            actions {
                actions.call("switch", "turn_on") { entity(fanId) }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`turn on fan when temperature state is hot`)

            env.emitState(sensorId, "hot")
            assertEquals(1, env.events<RecordedEvent.Action>().size)

            env.advanceClock(1.seconds)

            env.emitState(sensorId, "hot")

            assertEquals(1, env.events<RecordedEvent.Action>().size)
        }
    }

    @Test
    fun `combineState trigger with distinctUntilChanged suppresses re-fire when predicate result unchanged`() =
        runTest {
            val tempId = "sensor.temperature"
            val humidityId = "sensor.humidity"
            val fanId = "switch.fan"

            val `turn on fan when temperature is hot` = Jidouka.automation(
                id = "combined_fan",
                mode = AutomationMode.Parallel(5)
            ) {
                triggers {
                    combineState(
                        entity1 = entity(tempId),
                        entity2 = entity(humidityId)
                    ) { temp, _ ->
                        (temp?.stateRaw?.toDoubleOrNull() ?: 0.0) > 70.0
                    }
                }
                actions {
                    actions.call("switch", "turn_on") { entity(fanId) }
                }
            }

            AutomationTestEnvironment.test(this) { env ->
                env.register(`turn on fan when temperature is hot`)

                env.emitState(humidityId, "50.0")
                assertTrue(env.events<RecordedEvent.Action>().isEmpty())
                env.emitState(tempId, "75.0")
                assertEquals(1, env.events<RecordedEvent.Action>().size)

                env.advanceClock(1.seconds)

                env.emitState(humidityId, "80.0")

                assertEquals(1, env.events<RecordedEvent.Action>().size)
            }
        }

    @Test
    fun `unhandled action exception records AutomationFailed event`() = runTest {
        val motionId = "binary_sensor.motion"

        val `faulty automation that throws` = Jidouka.automation(
            id = "faulty",
            mode = AutomationMode.Single
        ) {
            triggers { state(entity(motionId)) { it?.stateRaw == "on" } }
            actions { error("service unavailable") }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`faulty automation that throws`)

            env.emitState(motionId, "on")

            val failures = env.events<RecordedEvent.AutomationFailed>()
            assertEquals(1, failures.size)
            assertEquals("faulty", failures[0].automationId)
            assertIs<IllegalStateException>(failures[0].exception)
            assertTrue(env.events<RecordedEvent.Action>().isEmpty())
        }
    }

    @Test
    fun `time isBetween LocalTime in trigger predicate gates trigger on time of day`() = runTest {
        val motionId = "binary_sensor.porch_motion"
        val lightId = "light.porch"

        val `turn on porch light on motion at night` = Jidouka.automation(
            id = "porch_night_light",
            mode = AutomationMode.Single
        ) {
            triggers {
                state(entity = entity(motionId)) {
                    it?.stateRaw == "on" && time.isBetween(LocalTime(22, 0), LocalTime(6, 0))
                }
            }
            actions {
                actions.call("light", "turn_on") {
                    entity(lightId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            val dayTime = LocalDateTime(2025, 3, 8, 14, 0)
            env.setClockTime(dayTime)
            env.register(`turn on porch light on motion at night`)

            env.emitState(motionId, "on")
            assertTrue(env.events<RecordedEvent.Action>().isEmpty())

            env.clearEvents()

            // Nighttime - should trigger
            val nightTime = LocalDateTime(2025, 3, 8, 23, 0)
            env.setClockTime(nightTime)
            env.emitState(motionId, "off")
            env.emitState(motionId, "on")

            assertEquals(1, env.events<RecordedEvent.Action>().size)
            assertEquals("light", env.events<RecordedEvent.Action>()[0].domainId)
            assertEquals("turn_on", env.events<RecordedEvent.Action>()[0].action)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `time isBetween LocalTime with non-wrapping range in trigger predicate`() = runTest {
        val presenceId = "binary_sensor.office_presence"
        val sceneId = "scene.office_mode"

        val `activate office mode during work hours` = Jidouka.automation(
            id = "office_mode",
            mode = AutomationMode.Single
        ) {
            triggers {
                state(entity = entity(presenceId)) {
                    it?.stateRaw == "home" && time.isBetween(LocalTime(9, 0), LocalTime(17, 0))
                }
            }
            actions {
                actions.call("scene", "turn_on") {
                    entity(sceneId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            val outsideWorkHours = LocalDateTime(2025, 3, 8, 20, 0)
            env.setClockTime(outsideWorkHours)
            env.register(`activate office mode during work hours`)

            env.emitState(presenceId, "home")
            assertTrue(env.events<RecordedEvent.Action>().isEmpty())

            env.clearEvents()

            // Midday - within work hours
            val workHours = LocalDateTime(2025, 3, 8, 12, 0)
            env.setClockTime(workHours)
            env.emitState(presenceId, "away")
            env.emitState(presenceId, "home")

            assertEquals(1, env.events<RecordedEvent.Action>().size)
            assertEquals("scene", env.events<RecordedEvent.Action>()[0].domainId)
            assertEquals("turn_on", env.events<RecordedEvent.Action>()[0].action)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `time isBetween DayOfWeek in trigger predicate restricts trigger to weekdays`() = runTest {
        val alarmId = "binary_sensor.alarm_dismissed"
        val notifyId = "notify.mobile_app"

        val `send morning briefing on weekdays` = Jidouka.automation(
            id = "weekday_briefing",
            mode = AutomationMode.Single
        ) {
            triggers {
                state(entity = entity(alarmId)) {
                    it?.stateRaw == "dismissed" && time.isBetween(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
                }
            }
            actions {
                actions.call("notify", "mobile_app", data = mapOf("message" to "Good morning")) {
                    entity(notifyId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setClockTime(LocalDateTime(2025, 3, 8, 7, 0)) // March 8, 2025 is a Saturday
            env.register(`send morning briefing on weekdays`)

            env.emitState(alarmId, "dismissed")
            assertTrue(env.events<RecordedEvent.Action>().isEmpty())

            env.clearEvents()

            env.setClockTime(LocalDateTime(2025, 3, 5, 7, 0)) // March 5, 2025 is a Wednesday
            env.emitState(alarmId, "active")
            env.emitState(alarmId, "dismissed")

            assertEquals(1, env.events<RecordedEvent.Action>().size)
            assertEquals("notify", env.events<RecordedEvent.Action>()[0].domainId)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `time isBetween Month pair in trigger predicate restricts trigger to season`() = runTest {
        val thermostatId = "climate.living_room"
        val scheduleId = "switch.heating_schedule"

        val `enable heating schedule in winter` = Jidouka.automation(
            id = "winter_heating",
            mode = AutomationMode.Single
        ) {
            triggers {
                state(entity = entity(thermostatId)) {
                    it?.stateRaw == "heat" && time.isBetween(Month.NOVEMBER to 1, Month.FEBRUARY to 28)
                }
            }
            actions {
                actions.call("switch", "turn_on") {
                    entity(scheduleId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            val summerTime = LocalDateTime(2025, 6, 1, 8, 0)
            env.setClockTime(summerTime)
            env.register(`enable heating schedule in winter`)

            env.emitState(thermostatId, "heat")
            assertTrue(env.events<RecordedEvent.Action>().isEmpty())

            env.clearEvents()

            val winterTime = LocalDateTime(2025, 12, 15, 8, 0)
            env.setClockTime(winterTime)
            env.emitState(thermostatId, "off")
            env.emitState(thermostatId, "heat")

            assertEquals(1, env.events<RecordedEvent.Action>().size)
            assertEquals("switch", env.events<RecordedEvent.Action>()[0].domainId)
            assertEquals("turn_on", env.events<RecordedEvent.Action>()[0].action)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `time localTime accessible in trigger predicate for comparison`() = runTest {
        val motionId = "binary_sensor.hallway_motion_2"
        val lightId = "light.hallway_2"

        val `turn on hallway light after sunset time` = Jidouka.automation(
            id = "evening_hallway_light",
            mode = AutomationMode.Single
        ) {
            triggers {
                state(entity = entity(motionId)) {
                    it?.stateRaw == "on" && time.localTime >= LocalTime(18, 0)
                }
            }
            actions {
                actions.call("light", "turn_on") {
                    entity(lightId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            val morning = LocalDateTime(2025, 3, 8, 10, 0)
            env.setClockTime(morning)
            env.register(`turn on hallway light after sunset time`)

            env.emitState(motionId, "on")
            assertTrue(env.events<RecordedEvent.Action>().isEmpty())

            env.clearEvents()

            val evening = LocalDateTime(2025, 3, 8, 19, 0)
            env.setClockTime(evening)
            env.emitState(motionId, "off")
            env.emitState(motionId, "on")

            assertEquals(1, env.events<RecordedEvent.Action>().size)
            assertEquals("light", env.events<RecordedEvent.Action>()[0].domainId)
            assertEquals("turn_on", env.events<RecordedEvent.Action>()[0].action)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `time localDate accessible in trigger predicate`() = runTest {
        val presenceId = "binary_sensor.home_presence"
        val sceneId = "scene.holiday_mode"

        val `activate holiday mode on christmas` = Jidouka.automation(
            id = "holiday_mode",
            mode = AutomationMode.Single
        ) {
            triggers {
                state(entity = entity(presenceId)) {
                    it?.stateRaw == "home" &&
                            time.localDate.month == Month.DECEMBER &&
                            time.localDate.day == 25
                }
            }
            actions {
                actions.call("scene", "turn_on") {
                    entity(sceneId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setClockTime(LocalDateTime(2025, 12, 26, 10, 0))
            env.register(`activate holiday mode on christmas`)

            env.emitState(presenceId, "home")
            assertTrue(env.events<RecordedEvent.Action>().isEmpty())

            env.clearEvents()

            env.setClockTime(LocalDateTime(2025, 12, 25, 10, 0))
            env.emitState(presenceId, "away")
            env.emitState(presenceId, "home")

            assertEquals(1, env.events<RecordedEvent.Action>().size)
            assertEquals("scene", env.events<RecordedEvent.Action>()[0].domainId)
            assertEquals("turn_on", env.events<RecordedEvent.Action>()[0].action)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `states trigger fires when any member entity matches and identifies the firing entity`() = runTest {
        val battery1Id = "sensor.battery_1"
        val battery2Id = "sensor.battery_2"
        val battery3Id = "sensor.battery_3"
        val notifyId = "notify.mobile_app"

        val `alert on any low battery` = Jidouka.automation(
            id = "battery_alert",
            mode = AutomationMode.Parallel(5)
        ) {
            triggers {
                val batterySensors = listOf(
                    entity(battery1Id),
                    entity(battery2Id),
                    entity(battery3Id)
                )

                state(batterySensors) { (it?.stateRaw?.toDoubleOrNull() ?: 100.0) < 20.0 }
            }
            actions {
                val firedEntityId = triggered.state()?.entityId
                actions.call(
                    "notify", "mobile_app",
                    data = mapOf("message" to "Low battery: $firedEntityId")
                ) {
                    entity(notifyId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setEntityState(battery1Id, "80")
            env.setEntityState(battery2Id, "90")
            env.setEntityState(battery3Id, "85")
            env.register(`alert on any low battery`)

            env.emitState(battery2Id, "15")

            assertEquals(1, env.events<RecordedEvent.Action>().size)
            val message = env.events<RecordedEvent.Action>()[0].data["message"]
            assertEquals("Low battery: $battery2Id", message)

            env.clearEvents()

            env.emitState(battery1Id, "10")
            assertEquals(1, env.events<RecordedEvent.Action>().size)
            assertEquals("Low battery: $battery1Id", env.events<RecordedEvent.Action>()[0].data["message"])

            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }
}