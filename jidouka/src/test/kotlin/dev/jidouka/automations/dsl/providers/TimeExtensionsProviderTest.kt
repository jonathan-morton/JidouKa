@file:Suppress("KotlinMisorderedAssertEqualsArguments")

package dev.jidouka.automations.dsl.providers

import dev.jidouka.automations.dsl.providers.TimeExtensionsProviderTest.Companion.DATE
import dev.jidouka.automations.dsl.providers.TimeExtensionsProviderTest.Companion.TIME
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimeExtensionsProviderTest {
    private val provider = DefaultTimeExtensionsProvider(TimeZone.UTC)

    @Test
    fun `toInstantOrNull reads ISO 8601 with an offset`() = with(provider) {
        assertEquals(INSTANT, INSTANT_UTC_TEXT.toInstantOrNull())
        assertEquals(INSTANT, INSTANT_PLUS_TWO_HOURS_TEXT.toInstantOrNull())
    }

    @Test
    fun `toInstantOrNull never reads a time without an offset`() = with(provider) {
        assertNull(DATE_TIME_T_TEXT.toInstantOrNull())
        assertNull(NOT_A_TIME.toInstantOrNull())
    }

    @Test
    fun `toLocalDateTimeOrNull reads T and space separators`() = with(provider) {
        assertEquals(DATE_TIME, DATE_TIME_T_TEXT.toLocalDateTimeOrNull())
        assertEquals(DATE_TIME, DATE_TIME_SPACE_TEXT.toLocalDateTimeOrNull())
        assertNull(INSTANT_UTC_TEXT.toLocalDateTimeOrNull())
    }

    @Test
    fun `toLocalDateOrNull and toLocalTimeOrNull read dates and times`() = with(provider) {
        assertEquals(DATE, DATE_TEXT.toLocalDateOrNull())
        assertEquals(TIME, TIME_TEXT.toLocalTimeOrNull())
        assertNull(NOT_A_DATE.toLocalDateOrNull())
        assertNull(NOT_A_TIME.toLocalTimeOrNull())
    }

    private companion object {
        private val DATE = LocalDate(2026, Month.MAY, 4)
        private val TIME = LocalTime(10, 15, 30, 123_456_000)
        private val DATE_TIME = LocalDateTime(DATE, TIME)
        private val INSTANT = DATE_TIME.toInstant(TimeZone.UTC)

        /** Home Assistant's `isoformat()` text for [DATE], [TIME] and the date-times built from them. */
        private const val DATE_TEXT = "2026-05-04"
        private const val TIME_TEXT = "10:15:30.123456"
        private const val DATE_TIME_T_TEXT = "${DATE_TEXT}T$TIME_TEXT"
        private const val DATE_TIME_SPACE_TEXT = "$DATE_TEXT $TIME_TEXT"
        private const val INSTANT_UTC_TEXT = "$DATE_TIME_T_TEXT+00:00"
        private const val INSTANT_PLUS_TWO_HOURS_TEXT = "2026-05-04T12:15:30.123456+02:00"

        private const val NOT_A_DATE = "yesterday"
        private const val NOT_A_TIME = "noon"
    }
}
