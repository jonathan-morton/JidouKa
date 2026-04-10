package dev.jidouka.components.event

import dev.jidouka.network.models.hass.websocket.Context
import kotlin.time.Instant

/**
 * Abstract base class for creating a Home Assistant event
 * @property eventTypeId The Home Assistant event type (e.g., "state_changed", "call_service")
 * @property rawData The event's data payload
 * @property timeFired When the event occurred
 * @property origin The origin of the event
 */
public abstract class BaseEvent {
    public abstract val eventTypeId: String
    public abstract val rawData: Map<String, Any?>
    public abstract val timeFired: Instant
    public abstract val origin: String


    /**
     * Parser for deserializing [EventObject] into a specific event type.
     *
     * @param E The specific event type this parser produces
     */
    public interface Parser<E : BaseEvent> {
        public fun parse(eventObject: EventObject): E?
    }
}

/**
 * Event data as received from Home Assistant's WebSocket API.
 *
 * This is the deserialized form of the event before it's parsed into
 * a specific [BaseEvent] subclass. Event parsers transform this into
 * typed event instances.
 *
 * @property eventType The Home Assistant event type
 * @property data The event's data payload
 * @property timeFired When the event occurred
 * @property origin The origin of the event
 * @property context Home Assistant's context for this event
 */
public data class EventObject(
    val eventType: String,
    val data: Map<String, Any?>,
    val timeFired: Instant,
    val origin: String,
    val context: Context
)

/**
 * Generic event implementation that can represent any Home Assistant event.
 */
public class GenericEvent(
    override val eventTypeId: String,
    override val rawData: Map<String, Any?>,
    override val origin: String,
    override val timeFired: Instant
) : BaseEvent() {

    public companion object {
        public val parser: Parser<GenericEvent> = object : Parser<GenericEvent> {
            override fun parse(eventObject: EventObject): GenericEvent {
                return GenericEvent(
                    eventTypeId = eventObject.eventType,
                    rawData = eventObject.data,
                    timeFired = eventObject.timeFired,
                    origin = eventObject.origin
                )
            }
        }

        public fun asGenericEvent(event: BaseEvent): GenericEvent {
            return event as? GenericEvent
                ?: GenericEvent(
                    eventTypeId = event.eventTypeId,
                    rawData = event.rawData,
                    origin = event.origin,
                    timeFired = event.timeFired
                )
        }
    }
}
