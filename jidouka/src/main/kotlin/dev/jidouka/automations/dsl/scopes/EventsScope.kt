package dev.jidouka.automations.dsl.scopes

import dev.jidouka.aliases.EventTypeId
import dev.jidouka.automations.dsl.triggers.TriggerContext
import dev.jidouka.components.event.BaseEvent
import dev.jidouka.components.event.EventType
import dev.jidouka.components.event.GenericEvent
import dev.jidouka.registry.EventRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

public class EventsScope internal constructor(
    private val eventRegistry: EventRegistry,
    private val registerTrigger: (Flow<TriggerContext>, EventTypeId) -> Unit
) {
    public fun <E : BaseEvent> on(
        eventType: EventType<E>,
        predicate: suspend (E) -> Boolean = { true }
    ) {
        val rawFlow = eventRegistry.getOrCreateFlow(eventType.id)

        val triggerFlow: Flow<TriggerContext> = rawFlow.mapNotNull { eventObject ->

            @Suppress("UNCHECKED_CAST")
            val parser = eventType.parser
                ?: GenericEvent.parser as? BaseEvent.Parser<E>
                ?: return@mapNotNull null
            val parsedEvent = parser.parse(eventObject) ?: return@mapNotNull null

            if (predicate(parsedEvent)) {
                TriggerContext.Event(
                    eventType = eventType.id,
                    data = parsedEvent
                )
            } else {
                null
            }
        }

        registerTrigger(triggerFlow, eventType.id)
    }

    public fun on(
        eventTypeId: EventTypeId,
        predicate: suspend (GenericEvent) -> Boolean = { true }
    ) {
        on(EventType.asGeneric(eventTypeId), predicate)
    }
}