package dev.jidouka.monitors

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal interface TimeMonitor {
    val clock: SharedFlow<Instant>
}

@OptIn(ExperimentalTime::class)
@Single
internal class SystemTimeMonitor(
    private val scope: CoroutineScope
) : TimeMonitor {

    override val clock: SharedFlow<Instant> = flow {
        while (true) {
            emit(Clock.System.now())
            delay(500.milliseconds)
        }
    }.shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(replayExpirationMillis = 0),
    )
}