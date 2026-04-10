package dev.jidouka

import dev.jidouka.api.Jidouka
import dev.jidouka.automations.AutomationMode
import dev.jidouka.test.AutomationTestEnvironment
import dev.jidouka.test.RecordedEvent
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TimeTests : BaseUnitTest() {

    private val bedroomLightId = "light.bedroom"
    private val porchLightId = "light.porch"
    private val notifyId = "notify.mobile_app"
    private val motionId = "binary_sensor.hallway_motion"
    private val sunEntityId = "sensor.sun_next_setting"
    private val backupScriptId = "script.nightly_backup"
    private val hallwayLightId = "light.hallway"
    private val holidayLightId = "light.holiday_string"
    private val alarmId = "script.alarm"
    private val heatingId = "climate.heating"

    //region TimeTriggers.at(LocalTime)

    @Test
    fun `at local time fires when clock reaches target`() = runTest {
        val `morning lights turn on at 7am` = Jidouka.automation(
            id = "morning_lights",
            mode = AutomationMode.Single
        ) {
            triggers {
                time.at(LocalTime(hour = 7, minute = 0))
            }

            actions {
                actions.call("light", "turn_on") {
                    entity(bedroomLightId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setClockTime(LocalDateTime(year = 2025, month = 3, day = 8, hour = 6, minute = 55))
            env.register(`morning lights turn on at 7am`)

            assertTrue(env.events<RecordedEvent.Action>().isEmpty())

            env.advanceClock(5.minutes)

            val actions = env.events<RecordedEvent.Action>()
            assertEquals(1, actions.size)
            assertEquals("light", actions[0].domainId)
            assertEquals("turn_on", actions[0].action)
            assertTrue(actions[0].target.entityIds?.contains(bedroomLightId) == true)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `at local time does not fire before target`() = runTest {
        val `morning lights wait until 7am` = Jidouka.automation(
            id = "morning_lights",
            mode = AutomationMode.Single
        ) {
            triggers {
                time.at(LocalTime(hour = 7, minute = 0))
            }
            actions {
                actions.call("light", "turn_on") {
                    entity(bedroomLightId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setClockTime(LocalDateTime(year = 2025, month = 3, day = 8, hour = 6, minute = 30))
            env.register(`morning lights wait until 7am`)

            // Advance to 6:50
            env.advanceClock(20.minutes)

            assertTrue(env.events<RecordedEvent.Action>().isEmpty())
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `at local time with fireImmediately triggers when already past`() = runTest {
        val `evening mode activates on startup` = Jidouka.automation(
            id = "evening_mode",
            mode = AutomationMode.Single
        ) {
            triggers {
                time.at(LocalTime(hour = 18, minute = 0), fireImmediately = true)
            }
            actions {
                actions.call("light", "turn_on") {
                    entity(bedroomLightId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setClockTime(LocalDateTime(year = 2025, month = 3, day = 8, hour = 20, minute = 0))
            env.register(`evening mode activates on startup`)

            // Should fire immediately without advancing the clock
            env.advanceClock(1.minutes)

            val actions = env.events<RecordedEvent.Action>()
            assertEquals(1, actions.size)
            assertEquals("light", actions[0].domainId)
            assertEquals("turn_on", actions[0].action)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `at local time without fireImmediately skips current period`() = runTest {
        val `evening mode waits until tomorrow` = Jidouka.automation(
            id = "evening_mode",
            mode = AutomationMode.Single
        ) {
            triggers {
                time.at(LocalTime(hour = 18, minute = 0))
            }
            actions {
                actions.call("light", "turn_on") {
                    entity(bedroomLightId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setClockTime(LocalDateTime(year = 2025, month = 3, day = 8, hour = 20, minute = 0))
            env.register(`evening mode waits until tomorrow`)

            // should NOT fire because we're in the suppressed first period
            env.advanceClock(30.minutes)

            assertTrue(env.events<RecordedEvent.Action>().isEmpty())
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    //endregion

    //region TimeTriggers.at(LocalDateTime)

    @Test
    fun `at local datetime fires at exact target`() = runTest {
        val `new years countdown lights` = Jidouka.automation(
            id = "new_years_lights",
            mode = AutomationMode.Single
        ) {
            triggers {
                time.at(LocalDateTime(year = 2025, month = 12, day = 31, hour = 23, minute = 59))
            }
            actions {
                actions.call("light", "turn_on") {
                    entity(holidayLightId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setClockTime(LocalDateTime(year = 2025, month = 12, day = 31, hour = 23, minute = 50))
            env.register(`new years countdown lights`)

            assertTrue(env.events<RecordedEvent.Action>().isEmpty())

            env.advanceClock(9.minutes)

            val actions = env.events<RecordedEvent.Action>()
            assertEquals(1, actions.size)
            assertEquals("light", actions[0].domainId)
            assertEquals("turn_on", actions[0].action)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `at local datetime fires only once`() = runTest {
        var triggerCount = 0

        val `one time scheduled maintenance` = Jidouka.automation(
            id = "scheduled_maintenance",
            mode = AutomationMode.Single
        ) {
            triggers {
                time.at(LocalDateTime(year = 2025, month = 6, day = 15, hour = 2, minute = 0))
            }
            actions {
                triggerCount++
                actions.call("script", "turn_on") {
                    entity(backupScriptId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setClockTime(LocalDateTime(year = 2025, month = 6, day = 15, hour = 1, minute = 55))
            env.register(`one time scheduled maintenance`)

            env.advanceClock(5.minutes)
            assertEquals(1, triggerCount)

            env.advanceClock(1.hours)
            assertEquals(1, triggerCount)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    //endregion

    //region TimeTriggers.at(Entity, extractInstant)

    @Test
    fun `at entity instant fires when clock reaches extracted time`() = runTest {
        val `turn on porch light at sunset` = Jidouka.automation(
            id = "porch_light_sunset",
            mode = AutomationMode.Single
        ) {
            triggers {
                time.at(entity = entity(sunEntityId), fireImmediately = true) { state ->
                    state?.state?.let { Instant.parse(it) }
                }
            }
            actions {
                actions.call("light", "turn_on") {
                    entity(porchLightId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setEntityState(sunEntityId, state = "2025-03-08T18:30:00Z")
            env.setClockTime(LocalDateTime(2025, 3, 8, 18, 0))
            env.register(`turn on porch light at sunset`)

            assertTrue(env.events<RecordedEvent.Action>().isEmpty())

            env.advanceClock(30.minutes)

            val actions = env.events<RecordedEvent.Action>()
            assertEquals(1, actions.size)
            assertEquals("light", actions[0].domainId)
            assertEquals("turn_on", actions[0].action)
            assertTrue(actions[0].target.entityIds?.contains(porchLightId) == true)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `at entity instant updates when entity state changes`() = runTest {
        var triggerCount = 0

        val `porch light follows shifting sunset` = Jidouka.automation(
            id = "porch_light_sunset",
            mode = AutomationMode.Parallel(5)
        ) {
            triggers {
                time.at(entity = entity(sunEntityId), fireImmediately = true) { state ->
                    state?.state?.let { Instant.parse(it) }
                }
            }
            actions {
                triggerCount++
                actions.call("light", "turn_on") {
                    entity(porchLightId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setEntityState(sunEntityId, state = "2025-03-08T18:30:00Z")
            env.setClockTime(LocalDateTime(2025, 3, 8, 18, 0))
            env.register(`porch light follows shifting sunset`)

            env.advanceClock(30.minutes)
            assertEquals(1, triggerCount)

            env.emitState(sunEntityId, state = "2025-03-09T18:35:00Z")

            env.advanceClock(24.hours)
            env.advanceClock(5.minutes)

            assertTrue(triggerCount >= 2)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `at entity instant skips when extract returns null`() = runTest {
        val `porch light waits for valid sunset data` = Jidouka.automation(
            id = "porch_light_sunset",
            mode = AutomationMode.Single
        ) {
            triggers {
                time.at(entity = entity(sunEntityId), fireImmediately = true) { state ->
                    state?.state?.let { Instant.parse(it) }
                }
            }
            actions {
                actions.call("light", "turn_on") {
                    entity(porchLightId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setEntityState(sunEntityId, state = "unavailable")
            env.setClockTime(LocalDateTime(2025, 3, 8, 18, 0))
            env.register(`porch light waits for valid sunset data`)

            env.advanceClock(2.hours)

            assertTrue(env.events<RecordedEvent.Action>().isEmpty())
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    //endregion

    //region TimeTriggers.every(Duration)

    @Test
    fun `every duration fires at regular intervals`() = runTest {
        var triggerCount = 0

        val `periodic sensor health check` = Jidouka.automation(
            id = "sensor_health",
            mode = AutomationMode.Parallel(10)
        ) {
            triggers {
                time.every(duration = 30.minutes, fireImmediately = true)
            }
            actions {
                triggerCount++
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setClockTime(LocalDateTime(2025, 3, 8, 12, 0))
            env.register(`periodic sensor health check`)

            env.advanceClock(1.minutes)
            assertEquals(1, triggerCount)

            env.advanceClock(30.minutes)
            assertEquals(2, triggerCount)

            env.advanceClock(30.minutes)
            env.awaitIdle()
            assertEquals(3, triggerCount)

            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `every duration with fireImmediately triggers on first tick`() = runTest {
        var triggerCount = 0

        val `immediate sensor poll on startup` = Jidouka.automation(
            id = "sensor_poll",
            mode = AutomationMode.Parallel(10)
        ) {
            triggers {
                time.every(duration = 1.hours, fireImmediately = true)
            }
            actions {
                triggerCount++
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setClockTime(LocalDateTime(2025, 3, 8, 12, 0))
            env.register(`immediate sensor poll on startup`)

            env.advanceClock(1.minutes)
            assertEquals(1, triggerCount)

            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    //endregion

    //region TimeTriggers.every(DateTimeUnit)

    @Test
    fun `every day fires daily at specified time`() = runTest {
        var triggerCount = 0

        val `daily backup at midnight` = Jidouka.automation(
            id = "daily_backup",
            mode = AutomationMode.Parallel(10)
        ) {
            triggers {
                time.every(
                    unit = DateTimeUnit.DAY,
                    at = LocalTime(0, 0),
                    fireImmediately = false
                )
            }
            actions {
                triggerCount++
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setClockTime(LocalDateTime(2025, 3, 8, 23, 55))
            env.register(`daily backup at midnight`)

            // Cross midnight into March 9
            env.advanceClock(5.minutes)
            assertEquals(1, triggerCount)

            // Advance to March 10 midnight
            env.advanceClock(24.hours)
            assertEquals(2, triggerCount)

            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `every week fires on matching day`() = runTest {
        var triggerCount = 0

        val `weekly report every monday` = Jidouka.automation(
            id = "weekly_report",
            mode = AutomationMode.Parallel(10)
        ) {
            triggers {
                time.every(
                    unit = DateTimeUnit.WEEK,
                    at = LocalTime(9, 0),
                    on = DayOfWeek.MONDAY,
                    fireImmediately = true
                )
            }
            actions {
                triggerCount++
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            // March 10, 2025 is a Monday
            env.setClockTime(LocalDateTime(2025, 3, 10, 8, 55))
            env.register(`weekly report every monday`)

            // Advance past 9:00 AM Monday
            env.advanceClock(5.minutes)
            assertEquals(1, triggerCount)

            // Advance to Tuesday — should not fire
            env.advanceClock(24.hours)
            assertEquals(1, triggerCount)

            // Advance to next Monday 9:00 AM (6 more days)
            env.advanceClock(144.hours)
            assertEquals(2, triggerCount)

            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    //endregion

    //region TimeAccess.isBetween(LocalTime)

    @Test
    fun `isBetween local time matches within range`() = runTest {
        val `dim lights during evening hours` = Jidouka.automation(
            id = "evening_dim",
            mode = AutomationMode.Single
        ) {
            triggers {
                state(entity = entity(motionId)) { it?.state == "on" }
            }
            conditions {
                condition {
                    time.isBetween(LocalTime(18, 0), LocalTime(23, 0))
                }
            }
            actions {
                actions.call("light", "turn_on", data = mapOf("brightness" to 80)) {
                    entity(hallwayLightId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setClockTime(LocalDateTime(2025, 3, 8, 20, 0))
            env.register(`dim lights during evening hours`)

            env.emitState(motionId, "on")

            val actions = env.events<RecordedEvent.Action>()
            assertEquals(1, actions.size)
            assertEquals("light", actions[0].domainId)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `isBetween local time handles midnight wrap`() = runTest {
        val `quiet hours overnight` = Jidouka.automation(
            id = "quiet_hours",
            mode = AutomationMode.Single
        ) {
            triggers {
                state(entity = entity(motionId)) { it?.state == "on" }
            }
            conditions {
                condition {
                    time.isBetween(LocalTime(hour = 22, minute = 0), LocalTime(hour = 6, minute = 0))
                }
            }
            actions {
                actions.call("notify", "mobile_app", data = mapOf("message" to "Motion during quiet hours")) {
                    entity(notifyId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setClockTime(LocalDateTime(year = 2025, month = 3, day = 9, hour = 2, minute = 0))
            env.register(`quiet hours overnight`)

            env.emitState(motionId, "on")

            val actions = env.events<RecordedEvent.Action>()
            assertEquals(1, actions.size)
            assertEquals("notify", actions[0].domainId)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `isBetween local time rejects outside range`() = runTest {
        val `daytime motion ignored at night` = Jidouka.automation(
            id = "daytime_motion",
            mode = AutomationMode.Single
        ) {
            triggers {
                state(entity = entity(motionId)) { it?.state == "on" }
            }
            conditions {
                condition {
                    time.isBetween(LocalTime(hour = 8, minute = 0), LocalTime(hour = 17, minute = 0))
                }
            }
            actions {
                actions.call("light", "turn_on") {
                    entity(hallwayLightId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setClockTime(LocalDateTime(year = 2025, month = 3, day = 8, hour = 21, minute = 0))
            env.register(`daytime motion ignored at night`)

            env.emitState(motionId, "on")

            assertTrue(env.events<RecordedEvent.Action>().isEmpty())
            assertTrue(env.events<RecordedEvent.ConditionFailed>().any { it.automationId == "daytime_motion" })
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    //endregion

    //region TimeAccess.isBetween(DayOfWeek)

    @Test
    fun `isBetween day of week matches weekdays`() = runTest {
        val `workday morning alarm` = Jidouka.automation(
            id = "workday_alarm",
            mode = AutomationMode.Single
        ) {
            triggers {
                state(entity = entity(motionId)) { it?.state == "on" }
            }
            conditions {
                condition {
                    time.isBetween(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
                }
            }
            actions {
                actions.call("script", "turn_on") {
                    entity(alarmId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            // March 12, 2025 is a Wednesday
            env.setClockTime(LocalDateTime(2025, 3, 12, 7, 0))
            env.register(`workday morning alarm`)

            env.emitState(motionId, "on")

            val actions = env.events<RecordedEvent.Action>()
            assertEquals(1, actions.size)
            assertEquals("script", actions[0].domainId)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `isBetween day of week handles week wrap`() = runTest {
        val `weekend motion lighting` = Jidouka.automation(
            id = "weekend_lighting",
            mode = AutomationMode.Single
        ) {
            triggers {
                state(entity = entity(motionId)) { it?.state == "on" }
            }
            conditions {
                condition {
                    time.isBetween(DayOfWeek.SATURDAY, DayOfWeek.MONDAY)
                }
            }
            actions {
                actions.call("light", "turn_on") {
                    entity(hallwayLightId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            // March 9, 2025 is a Sunday
            env.setClockTime(LocalDateTime(year = 2025, month = 3, day = 9, hour = 10, minute = 0))
            env.register(`weekend motion lighting`)

            env.emitState(motionId, "on")

            val actions = env.events<RecordedEvent.Action>()
            assertEquals(1, actions.size)
            assertEquals("light", actions[0].domainId)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())

            env.clearEvents()

            // March 12, 2025 is a Wednesday
            env.setClockTime(LocalDateTime(year = 2025, month = 3, day = 12, hour = 10, minute = 0))
            env.emitState(motionId, "off")
            env.emitState(motionId, "on")

            assertTrue(env.events<RecordedEvent.Action>().isEmpty())
            assertTrue(env.events<RecordedEvent.ConditionFailed>().any { it.automationId == "weekend_lighting" })
        }
    }

    //endregion

    //region TimeAccess.isBetween(Pair<Month, Int>)

    @Test
    fun `isBetween month day matches within range`() = runTest {
        val `holiday lights in december` = Jidouka.automation(
            id = "holiday_lights",
            mode = AutomationMode.Single
        ) {
            triggers {
                state(entity = entity(motionId)) { it?.state == "on" }
            }
            conditions {
                condition {
                    time.isBetween(Month.DECEMBER to 1, Month.DECEMBER to 31)
                }
            }
            actions {
                actions.call("light", "turn_on") {
                    entity(holidayLightId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setClockTime(LocalDateTime(year = 2025, month = 12, day = 15, hour = 18, minute = 0))
            env.register(`holiday lights in december`)

            env.emitState(motionId, "on")

            val actions = env.events<RecordedEvent.Action>()
            assertEquals(1, actions.size)
            assertEquals("light", actions[0].domainId)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `isBetween month day handles year wrap`() = runTest {
        val `winter schedule december through february` = Jidouka.automation(
            id = "winter_schedule",
            mode = AutomationMode.Single
        ) {
            triggers {
                state(entity = entity(motionId)) { it?.state == "on" }
            }
            conditions {
                condition {
                    // December 1 through February 28 wraps across the year boundary
                    time.isBetween(Month.DECEMBER to 1, Month.FEBRUARY to 28)
                }
            }
            actions {
                actions.call("climate", "set_temperature", data = mapOf("temperature" to 72)) {
                    entity(heatingId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setClockTime(LocalDateTime(year = 2025, month = 1, day = 15, hour = 8, minute = 0))
            env.register(`winter schedule december through february`)

            env.emitState(motionId, "on")

            val actions = env.events<RecordedEvent.Action>()
            assertEquals(1, actions.size)
            assertEquals("climate", actions[0].domainId)
            assertEquals("set_temperature", actions[0].action)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())

            env.clearEvents()

            env.setClockTime(LocalDateTime(year = 2025, month = 6, day = 15, hour = 8, minute = 0))
            env.emitState(motionId, "off")
            env.emitState(motionId, "on")

            assertTrue(env.events<RecordedEvent.Action>().isEmpty())
            assertTrue(env.events<RecordedEvent.ConditionFailed>().any { it.automationId == "winter_schedule" })
        }
    }

    //endregion
}