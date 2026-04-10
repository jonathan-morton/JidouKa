package dev.jidouka.automations.dsl.triggers

import dev.jidouka.automations.dsl.builders.TriggersBuilder
import dev.jidouka.automations.dsl.providers.DefaultTimeExtensionsProvider
import dev.jidouka.automations.dsl.providers.TimeExtensionsProvider
import dev.jidouka.components.BaseState
import dev.jidouka.components.Entity
import dev.jidouka.extensions.isAfterOrEqual
import dev.jidouka.extensions.isBefore
import kotlinx.coroutines.flow.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Triggers that are based on time
 */
@OptIn(ExperimentalTime::class)
public class TimeTriggers internal constructor(
    private val triggersBuilder: TriggersBuilder,
    private val clockFlow: SharedFlow<Instant>,
    private val clock: Clock,
    override val timeZone: TimeZone = TimeZone.currentSystemDefault()
) : TimeExtensionsProvider by DefaultTimeExtensionsProvider(timeZone) {
    /**
     * Triggers daily at a specific local time.
     *
     * @param localTime The time of day to trigger at
     * @param fireImmediately If true and the current time is already past the target time today,
     *                        triggers immediately. Otherwise, waits for the next occurrence.
     */
    public fun at(
        localTime: LocalTime,
        fireImmediately: Boolean = false
    ) {
        triggersBuilder.timeTriggeredFlow(
            clockFlow
                .map { nowInstant ->
                    val localDateTime = nowInstant.toLocalDateTime(timeZone)
                    if (localDateTime.time >= localTime) {
                        localDateTime.date
                    } else {
                        null
                    }
                }
                .distinctUntilChanged()
                .let { flow ->
                    if (fireImmediately) {
                        flow
                    } else {
                        flow.drop(1)
                    }
                }.mapNotNull { date ->
                    if (date != null) true else null
                }
        )
    }

    /**
     * Triggers once at a specific date and time.
     *
     * @param localDateTime The target date and time to trigger at
     * @throws IllegalArgumentException if the target localDateTime is in the past
     */
    public fun at(
        localDateTime: LocalDateTime
    ) {
        val nowInstant = clock.now()
        val targetInstant = localDateTime.toInstant(timeZone)

        require(nowInstant.isBefore(targetInstant)) {
            "Target time $localDateTime is in the past (current time: ${nowInstant.toLocalDateTime(timeZone)})"
        }

        triggersBuilder.timeTriggeredFlow(
            clockFlow
                .filter { it.isAfterOrEqual(targetInstant) }
                .take(1)
                .map { true }
        )
    }

    /**
     * Triggers when the current time matches the instant extracted from the entity's state.
     * The lambda receives the current entity state and should return the target Instant,
     * or null if no trigger should occur.
     *
     * @param entity The entity whose state contains timing information
     * @param extractInstant Lambda that extracts the target Instant from the entity state (or null to disable)
     */
    public fun <S : BaseState<S>> at(
        entity: Entity<S>,
        fireImmediately: Boolean = false,
        extractInstant: (S?) -> Instant?
    ) {
        triggersBuilder.timeTriggeredFlow(
            combine(entity.stateFlow, clockFlow) { state, nowInstant ->
                val targetInstant = extractInstant(state)

                if (targetInstant == null) {
                    false
                } else {
                    nowInstant.isAfterOrEqual(targetInstant)
                }
            }
                .withFireImmediately(fireImmediately)
                .distinctUntilChanged()
                .filter { it }
        )
        triggersBuilder.addEntityId(entity)
    }

    /**
     * Triggers at fixed durations.
     *
     * Warning: Not Daylight Savings time aware. Durations should be thought of as an amount of seconds.
     * If you need calendar based triggers, like every day or month, use DateTimeUnits
     *
     * @param duration The interval between triggers
     * @param startTime The time to start the interval from
     * @param fireImmediately If true and current time is already at/past startTime, triggers immediately
     * @throws IllegalArgumentException if duration is not positive
     */
    public fun every(
        duration: Duration,
        startTime: Instant = clock.now(),
        fireImmediately: Boolean = false
    ) {
        require(duration.isPositive()) {
            "Duration must be positive, got: $duration"
        }

        triggersBuilder.timeTriggeredFlow(
            flow {
                var nextTriggerTime = startTime + duration
                var isFirstEmission = true

                clockFlow.collect { currentTime ->
                    val shouldTrigger = when {
                        isFirstEmission && fireImmediately && currentTime.isAfterOrEqual(startTime) -> {
                            isFirstEmission = false
                            true
                        }

                        isFirstEmission -> {
                            isFirstEmission = false
                            false
                        }

                        currentTime.isAfterOrEqual(nextTriggerTime) -> {
                            nextTriggerTime = advanceTriggerTimeAfter(nextTriggerTime, currentTime, duration)
                            true
                        }

                        else -> false
                    }
                    emit(shouldTrigger)
                }
            }.ensureDistinctTriggers()
        )
    }

    /**
     * Triggers at calendar-based intervals (days, weeks, months, years).
     *
     * @param unit The date-based unit
     * @param at The time of day to trigger at
     * @param on The day of the week (only relevant for WEEK-based units)
     * @param onDayOfMonth The day of the month to trigger on
     * @param startDate The reference date to calculate intervals from
     * @param fireImmediately If true and currently in a valid trigger period, triggers immediately
     */
    public fun every(
        unit: DateTimeUnit.DateBased,
        at: LocalTime = LocalTime(0, 0, 0),
        on: DayOfWeek? = null,
        onDayOfMonth: Int? = null,
        startDate: LocalDate? = null,
        fireImmediately: Boolean = false
    ) {
        val referenceDate = startDate ?: clock.now().toLocalDateTime(timeZone).date

        triggersBuilder.timeTriggeredFlow(
            clockFlow
                .map { nowInstant ->
                    val nowLocalDateTime = nowInstant.toLocalDateTime(timeZone)
                    val nowDate = nowLocalDateTime.date
                    val nowTime = nowLocalDateTime.time

                    val isAtOrAfterTriggerTime = nowTime >= at
                    val matchesDayOfWeek = on?.let { nowDate.dayOfWeek == it } ?: true
                    val matchesDayOfMonth = onDayOfMonth?.let { nowDate.day == it } ?: true
                    val matchesInterval = matchesDateInterval(nowDate, referenceDate, unit)

                    val shouldTrigger = isAtOrAfterTriggerTime &&
                            matchesDayOfWeek &&
                            matchesDayOfMonth &&
                            matchesInterval

                    if (shouldTrigger) {
                        nowDate
                    } else {
                        null
                    }
                }
                .distinctUntilChanged()
                .let { flow ->
                    if (fireImmediately) flow else flow.drop(1)
                }
                .mapNotNull { date ->
                    if (date != null) {
                        true
                    } else {
                        null
                    }
                }
        )
    }

    private fun matchesDateInterval(
        currentDate: LocalDate,
        referenceDate: LocalDate,
        unit: DateTimeUnit.DateBased
    ): Boolean = when (unit) {
        is DateTimeUnit.DayBased -> {
            val daysBetween = currentDate.toEpochDays() - referenceDate.toEpochDays()
            daysBetween >= 0 && daysBetween % unit.days.toLong() == 0L
        }

        is DateTimeUnit.MonthBased -> {
            val monthsBetween = (currentDate.year - referenceDate.year) * 12 +
                    (currentDate.month.number - referenceDate.month.number)
            monthsBetween >= 0 && monthsBetween % unit.months == 0
        }
    }

    /**
     * Ensures consecutive true values in a boolean flow can pass through distinctUntilChanged
     * by forcing a false-true sequence when needed.
     *
     * This is necessary because distinctUntilChanged filters out consecutive identical values,
     * which would prevent back-to-back triggers from firing.
     */
    private fun Flow<Boolean>.ensureDistinctTriggers(): Flow<Boolean> = flow {
        var lastEmittedValue = false
        collect { shouldTrigger ->
            when {
                shouldTrigger != lastEmittedValue -> {
                    lastEmittedValue = shouldTrigger
                    emit(shouldTrigger)
                }

                shouldTrigger -> {
                    // Force false-true sequence for consecutive triggers
                    emit(false)
                    emit(true)
                }

                else -> emit(false)
            }
        }
    }.distinctUntilChanged()

    /**
     * Advances the trigger time past the current time by the specified duration intervals.
     * This ensures we don't miss triggers if time has jumped forward significantly.
     */
    private fun advanceTriggerTimeAfter(
        currentTriggerTime: Instant,
        currentTime: Instant,
        duration: Duration
    ): Instant {
        var nextTime = currentTriggerTime + duration

        while (nextTime <= currentTime) {
            nextTime += duration
        }
        return nextTime
    }

    /**
     * Handles fire-immediately logic.
     *
     * When fireImmediately=false: Suppresses the entire first trigger period, only triggering
     * on subsequent periods
     *
     * @param fireImmediately Whether to emit true during the first trigger period
     */
    private fun Flow<Boolean>.withFireImmediately(fireImmediately: Boolean): Flow<Boolean> = flow {
        if (fireImmediately) {
            collect { emit(it) }
        } else {
            var isInFirstPeriod = true

            collect { condition ->
                when {
                    condition && isInFirstPeriod -> {
                        emit(false)
                    }

                    condition.not() && isInFirstPeriod -> {
                        isInFirstPeriod = false
                        emit(false)
                    }

                    else -> {
                        emit(condition)
                    }
                }
            }
        }
    }
}



