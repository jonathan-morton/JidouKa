package dev.jidouka.network.serializers

import dev.jidouka.network.models.hass.websocket.EventResponse
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

internal object EventResponseSerializer : JsonContentPolymorphicSerializer<EventResponse>(EventResponse::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<EventResponse> {
        val jsonObject = element.jsonObject
        val event = jsonObject["event"]?.jsonObject
            ?: throw SerializationException("Missing 'event' field")

        return when {
            "variables" in event -> EventResponse.TriggerEventResponse.serializer()
            else -> {
                throw SerializationException("Unknown event structure: ${event.keys}")
            }
        }
    }
}