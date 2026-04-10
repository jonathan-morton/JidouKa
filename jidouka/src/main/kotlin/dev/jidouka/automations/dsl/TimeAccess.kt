package dev.jidouka.automations.dsl

import dev.jidouka.automations.dsl.providers.DefaultTimeExtensionsProvider
import dev.jidouka.automations.dsl.providers.TimeExtensionsProvider
import dev.jidouka.extensions.isAfterOrEqual
import dev.jidouka.extensions.isBeforeOrEqual
import dev.jidouka.extensions.isBetween
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Providers for access to time parameters and date information
 * @property localNow Current date and time in the configured [timeZone]
 * @property localTime Current time in the configured [timeZone]
 * @property localDate Current date in the configured [timeZone]
 */
public class TimeAccess internal constructor(
    private val clock: Clock,
    override val timeZone: TimeZone
) : TimeExtensionsProvider by DefaultTimeExtensionsProvider(timeZone) {
    private val logger = KotlinLogging.logger {}

    public val now: Instant
        get() = clock.now()

    public val localNow: LocalDateTime
        get() = now.toLocalDateTime(timeZone)

    public val localTime: LocalTime
        get() = localNow.time

    public val localDate: LocalDate
        get() = localNow.date

    /**
     * Returns true if the current time is between [start] and [end] (inclusive).
     *
     * Handles wrap-around: if `start > end`, the range wraps across midnight.
     * For example, `isBetween(22:00, 06:00)` matches times from 10 PM to 6 AM.
     *
     * @param start The start time (inclusive)
     * @param end The end time (inclusive)
     */
    public fun isBetween(start: LocalTime, end: LocalTime): Boolean {
        val currentTime = localTime
        return if (start.isBeforeOrEqual(end)) {
            currentTime.isBetween(start, end)
        } else {
            currentTime.isAfterOrEqual(start) || currentTime.isBeforeOrEqual(end)
        }
    }

    /**
     * Returns true if the current day of week is between [start] and [end] (inclusive).
     *
     * Handles wrap-around: if `start > end`, the range wraps across the week.
     * For example, `isBetween(SATURDAY, MONDAY)` matches Saturday, Sunday, and Monday.
     *
     * @param start The start day (inclusive)
     * @param end The end day (inclusive)
     */
    public fun isBetween(start: DayOfWeek, end: DayOfWeek): Boolean {
        val dayOfWeek = localNow.dayOfWeek
        return if (start.ordinal <= end.ordinal) {
            dayOfWeek in start..end
        } else {
            dayOfWeek >= start || dayOfWeek <= end
        }
    }

    /**
     * Returns true if the current date is between [start] and [end] (inclusive).
     *
     * Handles wrap-around: if the start month/day is later in the year than the end,
     * the range wraps across the year boundary. For example,
     * `isBetween(DECEMBER to 15, FEBRUARY to 28)` matches mid-December through late February.
     *
     * Date pairs are represented as `Month to dayOfMonth` (e.g., `MARCH to 1`).
     *
     * @param start The start date as a (Month, dayOfMonth) pair (inclusive)
     * @param end The end date as a (Month, dayOfMonth) pair (inclusive)
     */
    public fun isBetween(start: Pair<Month, Int>, end: Pair<Month, Int>): Boolean {
        val currentMonth = localDate.month.number
        val currentDay = localDate.day
        val startMonth = start.first.number
        val startDay = start.second
        val endMonth = end.first.number
        val endDay = end.second

        if (currentMonth == startMonth && currentMonth == endMonth) {
            return currentDay in startDay..endDay
        }

        val sameMonthWraps = (startMonth == endMonth && startDay > endDay)
        if (sameMonthWraps) {
            logger.warn { "This isBetween is a near full year comparison, is this comparison correct?\nstart: $start, end: $end" }
        }
        val wrapsToNextYear = startMonth > endMonth || sameMonthWraps

        if (wrapsToNextYear.not()) {
            if (currentMonth > startMonth && currentMonth < endMonth) return true
            if (currentMonth !in startMonth..endMonth) return false
        } else {
            if (currentMonth !in endMonth..startMonth) return true
            if (currentMonth < startMonth && currentMonth > endMonth) return false
        }

        return if (currentMonth == startMonth) {
            currentDay >= startDay
        } else {
            currentDay <= endDay
        }
    }
}