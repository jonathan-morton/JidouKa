package dev.jidouka.components.event

import dev.jidouka.aliases.EventTypeId

public class EventType<E : BaseEvent>(
    public val id: String,
    public val parser: BaseEvent.Parser<E>?
) {
    public companion object {
        public fun asGeneric(eventTypeId: EventTypeId): EventType<GenericEvent> {
            return EventType(eventTypeId, GenericEvent.parser)
        }
    }
}