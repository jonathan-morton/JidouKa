package dev.jidouka.test.registry

import dev.jidouka.components.event.EventObject
import dev.jidouka.registry.AbstractEventRegistry

internal class InMemoryEventRegistry : AbstractEventRegistry() {

    override suspend fun emitEvent(event: EventObject) {
        val flow = eventFlows[event.eventType]
        if (flow != null) {
            flow.emit(event)
            logger.debug { "Emitted event type '${event.eventType}'" }
        } else {
            error(
                "Test emitted event type '${event.eventType}' but no automation is listening. " +
                        "Did you forget to register the automation first?"
            )
        }
    }
}