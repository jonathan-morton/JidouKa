package dev.jidouka.extensions


import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

//region Instant Extension Functions
/**
 * Checks if this instant is before the other instant.
 * @param other The instant to compare against
 * @return true if this instant is before the other instant
 */
internal fun Instant.isBefore(other: Instant): Boolean {
    return this < other
}

/**
 * Checks if this instant is after the other instant.
 * @param other The instant to compare against
 * @return true if this instant is after the other instant
 */
internal fun Instant.isAfter(other: Instant): Boolean {
    return this > other
}

/**
 * Checks if this instant is before or equal to the other instant.
 * @param other The instant to compare against
 * @return true if this instant is before or equal to the other instant
 */
internal fun Instant.isBeforeOrEqual(other: Instant): Boolean {
    return this <= other
}

/**
 * Checks if this instant is after or equal to the other instant.
 * @param other The instant to compare against
 * @return true if this instant is after or equal to the other instant
 */
internal fun Instant.isAfterOrEqual(other: Instant): Boolean {
    return this >= other
}

/**
 * Checks if this instant is between the start and end instants (inclusive).
 * @param start The start instant
 * @param end The end instant
 * @return true if this instant is between start and end (inclusive)
 */
internal fun Instant.isBetween(start: Instant, end: Instant): Boolean {
    return this in start..end
}

/**
 * Checks if this instant is between the start and end instants (exclusive).
 * @param start The start instant
 * @param end The end instant
 * @return true if this instant is strictly between start and end
 */
internal fun Instant.isBetweenExclusive(start: Instant, end: Instant): Boolean {
    return this in start..<end
}

/**
 * Returns the earlier of two instants
 */
internal fun Instant.min(other: Instant): Instant = if (this < other) this else other

/**
 * Returns the latter of two instants
 */
internal fun Instant.max(other: Instant): Instant = if (this > other) this else other
//endregion

//region LocalDateTime Extension Functions

/**
 * Checks if this LocalDateTime is before the other LocalDateTime.
 * @param other The LocalDateTime to compare against
 * @return true if this LocalDateTime is before the other
 */
internal fun LocalDateTime.isBefore(other: LocalDateTime): Boolean {
    return this < other
}

/**
 * Checks if this LocalDateTime is after the other LocalDateTime.
 * @param other The LocalDateTime to compare against
 * @return true if this LocalDateTime is after the other
 */
internal fun LocalDateTime.isAfter(other: LocalDateTime): Boolean {
    return this > other
}

/**
 * Checks if this LocalDateTime is before or equal to the other LocalDateTime.
 * @param other The LocalDateTime to compare against
 * @return true if this LocalDateTime is before or equal to the other
 */
internal fun LocalDateTime.isBeforeOrEqual(other: LocalDateTime): Boolean {
    return this <= other
}

/**
 * Checks if this LocalDateTime is after or equal to the other LocalDateTime.
 * @param other The LocalDateTime to compare against
 * @return true if this LocalDateTime is after or equal to the other
 */
internal fun LocalDateTime.isAfterOrEqual(other: LocalDateTime): Boolean {
    return this >= other
}

/**
 * Checks if this LocalDateTime is between the start and end (inclusive).
 * @param start The start LocalDateTime
 * @param end The end LocalDateTime
 * @return true if this LocalDateTime is between start and end (inclusive)
 */
internal fun LocalDateTime.isBetween(start: LocalDateTime, end: LocalDateTime): Boolean {
    return this in start..end
}

/**
 * Checks if this LocalDateTime is between the start and end (exclusive).
 * @param start The start LocalDateTime
 * @param end The end LocalDateTime
 * @return true if this LocalDateTime is strictly between start and end
 */
internal fun LocalDateTime.isBetweenExclusive(start: LocalDateTime, end: LocalDateTime): Boolean {
    return this in start..<end
}

/**
 * Returns the earlier of two LocalDateTime instances
 */
internal fun LocalDateTime.min(other: LocalDateTime): LocalDateTime = if (this < other) this else other

/**
 * Returns the latter of two LocalDateTime instances
 */
internal fun LocalDateTime.max(other: LocalDateTime): LocalDateTime = if (this > other) this else other

//endregion

//region LocalTime Extension Functions

/**
 * Checks if this LocalTime is before the other LocalTime.
 * @param other The LocalTime to compare against
 * @return true if this LocalTime is before the other
 */
internal fun LocalTime.isBefore(other: LocalTime): Boolean = this < other

/**
 * Checks if this LocalTime is after the other LocalTime.
 * @param other The LocalTime to compare against
 * @return true if this LocalTime is after the other
 */
internal fun LocalTime.isAfter(other: LocalTime): Boolean = this > other

/**
 * Checks if this LocalTime is before or equal to the other LocalTime.
 * @param other The LocalTime to compare against
 * @return true if this LocalTime is before or equal to the other
 */
internal fun LocalTime.isBeforeOrEqual(other: LocalTime): Boolean = this <= other

/**
 * Checks if this LocalTime is after or equal to the other LocalTime.
 * @param other The LocalTime to compare against
 * @return true if this LocalTime is after or equal to the other
 */
internal fun LocalTime.isAfterOrEqual(other: LocalTime): Boolean = this >= other

/**
 * Checks if this LocalTime is between the start and end times (inclusive).
 * @param start The start LocalTime
 * @param end The end LocalTime
 * @return true if this LocalTime is between start and end (inclusive)
 */
internal fun LocalTime.isBetween(start: LocalTime, end: LocalTime): Boolean = this in start..end

/**
 * Checks if this LocalTime is between the start and end times (exclusive).
 * @param start The start LocalTime
 * @param end The end LocalTime
 * @return true if this LocalTime is strictly between start and end
 */
internal fun LocalTime.isBetweenExclusive(start: LocalTime, end: LocalTime): Boolean = this in start..<end

/**
 * Returns the earlier of two LocalTime instances
 */
internal fun LocalTime.min(other: LocalTime): LocalTime = if (this < other) this else other

/**
 * Returns the later of two LocalTime instances
 */
internal fun LocalTime.max(other: LocalTime): LocalTime = if (this > other) this else other

//endregion

//region LocalDate Extension Functions

/**
 * Checks if this LocalDate is before the other LocalDate.
 * @param other The LocalDate to compare against
 * @return true if this LocalDate is before the other
 */
internal fun LocalDate.isBefore(other: LocalDate): Boolean = this < other

/**
 * Checks if this LocalDate is after the other LocalDate.
 * @param other The LocalDate to compare against
 * @return true if this LocalDate is after the other
 */
internal fun LocalDate.isAfter(other: LocalDate): Boolean = this > other

/**
 * Checks if this LocalDate is before or equal to the other LocalDate.
 * @param other The LocalDate to compare against
 * @return true if this LocalDate is before or equal to the other
 */
internal fun LocalDate.isBeforeOrEqual(other: LocalDate): Boolean = this <= other

/**
 * Checks if this LocalDate is after or equal to the other LocalDate.
 * @param other The LocalDate to compare against
 * @return true if this LocalDate is after or equal to the other
 */
internal fun LocalDate.isAfterOrEqual(other: LocalDate): Boolean = this >= other

/**
 * Checks if this LocalDate is between the start and end dates (inclusive).
 * @param start The start LocalDate
 * @param end The end LocalDate
 * @return true if this LocalDate is between start and end (inclusive)
 */
internal fun LocalDate.isBetween(start: LocalDate, end: LocalDate): Boolean = this in start..end

/**
 * Checks if this LocalDate is between the start and end dates (exclusive).
 * @param start The start LocalDate
 * @param end The end LocalDate
 * @return true if this LocalDate is strictly between start and end
 */
internal fun LocalDate.isBetweenExclusive(start: LocalDate, end: LocalDate): Boolean = this in start..<end

/**
 * Returns the earlier of two LocalDate instances
 */
internal fun LocalDate.min(other: LocalDate): LocalDate = if (this < other) this else other

/**
 * Returns the later of two LocalDate instances
 */
internal fun LocalDate.max(other: LocalDate): LocalDate = if (this > other) this else other

//endregion
