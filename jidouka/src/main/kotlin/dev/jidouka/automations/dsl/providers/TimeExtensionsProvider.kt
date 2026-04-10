package dev.jidouka.automations.dsl.providers

import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

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
}