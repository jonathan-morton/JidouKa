package dev.jidouka.automations.dsl.providers

import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Provides LocalDateTime extension functions for use throughout the automation DSL.
 */
public interface TimeExtensionsProvider {
    /**
     * The time zone used for LocalDateTime conversions
     */
    public val timeZone: TimeZone

    /**
     * Creates a new LocalDateTime offset by the specified amounts.
     *
     * @param years Number of years to add (can be negative)
     * @param months Number of months to add (can be negative)
     * @param days Number of days to add (can be negative)
     * @param hours Number of hours to add (can be negative)
     * @param minutes Number of minutes to add (can be negative)
     * @param seconds Number of seconds to add (can be negative)
     * @return A new LocalDateTime with the offset applied
     */
    public fun LocalDateTime.offset(
        years: Int = 0,
        months: Int = 0,
        days: Int = 0,
        hours: Int = 0,
        minutes: Int = 0,
        seconds: Int = 0
    ): LocalDateTime

    /**
     * Adds a DateTimePeriod to this LocalDateTime.
     *
     * @param period The period to add
     * @return A new LocalDateTime with the period added
     */
    public operator fun LocalDateTime.plus(period: DateTimePeriod): LocalDateTime

    /**
     * Subtracts a DateTimePeriod from this LocalDateTime.
     *
     * @param period The period to subtract
     * @return A new LocalDateTime with the period subtracted
     */
    public operator fun LocalDateTime.minus(period: DateTimePeriod): LocalDateTime

    /**
     * Parses an ISO 8601 date and time with an offset or `Z`, the format Home Assistant's `isoformat()` sends.
     *
     * A time without an offset doesn't parse. Convert it with `toLocalDateTimeOrNull()?.toInstant(timeZone)`,
     * so the zone is written out.
     *
     * @receiver The text to parse, for example `2026-05-04T10:15:30.123456+00:00`
     * @return The instant, or null if the text doesn't parse or has no offset
     */
    public fun String.toInstantOrNull(): Instant?

    /**
     * Parses a date and time with no offset, separated by `T` or a space.
     *
     * @receiver The text to parse, for example `2026-05-04T00:00:00` or `2026-05-04 00:00:00`
     * @return The date and time, or null if the text doesn't parse or has an offset
     */
    public fun String.toLocalDateTimeOrNull(): LocalDateTime?

    /**
     * Parses an ISO 8601 date.
     *
     * @receiver The text to parse, for example `2026-05-04`
     * @return The date, or null if the text doesn't parse
     */
    public fun String.toLocalDateOrNull(): LocalDate?

    /**
     * Parses an ISO 8601 time of day, with or without seconds.
     *
     * @receiver The text to parse, for example `01:23:45` or `01:23`
     * @return The time, or null if the text doesn't parse
     */
    public fun String.toLocalTimeOrNull(): LocalTime?
}

internal class DefaultTimeExtensionsProvider(
    override val timeZone: TimeZone
) : TimeExtensionsProvider {

    override fun LocalDateTime.offset(
        years: Int,
        months: Int,
        days: Int,
        hours: Int,
        minutes: Int,
        seconds: Int
    ): LocalDateTime {
        val instant = this.toInstant(timeZone)
        val period = DateTimePeriod(
            years = years,
            months = months,
            days = days,
            hours = hours,
            minutes = minutes,
            seconds = seconds
        )
        val newInstant = instant.plus(period, timeZone)
        return newInstant.toLocalDateTime(timeZone)
    }

    override operator fun LocalDateTime.plus(period: DateTimePeriod): LocalDateTime {
        val instant = this.toInstant(timeZone)
        val newInstant = instant.plus(period, timeZone)
        return newInstant.toLocalDateTime(timeZone)
    }

    override operator fun LocalDateTime.minus(period: DateTimePeriod): LocalDateTime {
        val instant = this.toInstant(timeZone)
        val newInstant = instant.minus(period, timeZone)
        return newInstant.toLocalDateTime(timeZone)
    }

    override fun String.toInstantOrNull(): Instant? {
        return Instant.parseOrNull(this)
    }

    override fun String.toLocalDateTimeOrNull(): LocalDateTime? {
        val isoText = replaceFirst(
            oldChar = ' ',
            newChar = 'T',
        )
        return LocalDateTime.Formats.ISO.parseOrNull(isoText)
    }

    override fun String.toLocalDateOrNull(): LocalDate? {
        return LocalDate.Formats.ISO.parseOrNull(this)
    }

    override fun String.toLocalTimeOrNull(): LocalTime? {
        return LocalTime.Formats.ISO.parseOrNull(this)
    }
}