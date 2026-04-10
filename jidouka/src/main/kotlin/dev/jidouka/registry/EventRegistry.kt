package dev.jidouka.registry

import dev.jidouka.components.event.EventObject
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

internal interface EventRegistry {
    fun getOrCreateFlow(eventType: String): SharedFlow<EventObject>
    suspend fun emitEvent(event: EventObject)
}

internal abstract class AbstractEventRegistry : EventRegistry {
    protected val eventFlows = ConcurrentHashMap<String, MutableSharedFlow<EventObject>>()
    protected val logger = KotlinLogging.logger {}

    override fun getOrCreateFlow(eventType: String): SharedFlow<EventObject> {
        var wasCreated = false
        val flow = eventFlows.computeIfAbsent(eventType) {
            wasCreated = true
            MutableSharedFlow(
                replay = 0,
                extraBufferCapacity = EVENTS_FLOW_EXTRA_BUFFER_CAPACITY
            )
        }
        if (wasCreated) {
            logger.debug { "Created event flow for event type '$eventType'" }
        }
        return flow
    }

    companion object {
        private const val EVENTS_FLOW_EXTRA_BUFFER_CAPACITY = 64
    }
}

@Single(binds = [EventRegistry::class])
internal class HomeAssistantEventRegistry : AbstractEventRegistry() {
    private val mutex = Mutex()

    override suspend fun emitEvent(
        event: EventObject
    ) {
        mutex.withLock {
            val eventType = event.eventType
            val flow = eventFlows[eventType]
            if (flow != null) {
                flow.emit(event)
                logger.debug { "Emitted event type '$eventType'" }
            } else {
                logger.warn { "No subscribers for event type '$eventType', event dropped" }
            }
        }
    }
}