package dev.jidouka.test.monitors

import dev.jidouka.monitors.TimeMonitor
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.Duration
import kotlin.time.Instant

internal class TestTimeMonitor : TimeMonitor {
    private val logger = KotlinLogging.logger {}

    private val _clock = MutableSharedFlow<Instant>(
        replay = 1,
        extraBufferCapacity = 16
    )
    override val clock: SharedFlow<Instant> = _clock

    private var _currentTime: Instant = Instant.DISTANT_PAST
    val currentTime: Instant
        get() = _currentTime

    /**
     * Sets the clock to a specific time
     * Will emit to the clock flow, triggering time based automations
     */
    suspend fun setTime(time: Instant) {
        _currentTime = time
        _clock.emit(time)
        logger.debug { "Clock set to $currentTime" }
    }

    /**
     * Advance the clock by a duration.
     * Emits new time to clock flow.
     */
    suspend fun advance(duration: Duration) {
        _currentTime += duration
        _clock.emit(_currentTime)
        logger.debug { "Clock advanced by $duration to $_currentTime" }
    }

    fun syncClockToScheduler(duration: Duration) {
        require((duration.isNegative().not())) {
            "Can not sync time backwards: $duration"
        }

        if (duration == Duration.ZERO) {
            logger.info { "Duration was zero" }
            return
        }

        _currentTime += duration
        logger.debug { "Syncing clock to scheduler by $duration to $_currentTime" }
    }
}