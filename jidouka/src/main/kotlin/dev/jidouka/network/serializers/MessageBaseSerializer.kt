package dev.jidouka.network.serializers

import dev.jidouka.network.models.hass.websocket.EventResponse
import dev.jidouka.network.models.hass.websocket.HaRequest
import dev.jidouka.network.models.hass.websocket.HaResponse
import dev.jidouka.network.models.hass.websocket.MessageBase
import dev.jidouka.network.models.hass.websocket.MessageType
import dev.jidouka.network.models.hass.websocket.ResultResponse
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object MessageBaseSerializer : JsonContentPolymorphicSerializer<MessageBase>(baseClass = MessageBase::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<MessageBase> {
        val typeString = element.jsonObject["type"]?.jsonPrimitive?.content
            ?: throw SerializationException("Missing 'type' field")

        val messageType = MessageType.entries.firstOrNull { entry ->
            entry.serialName == typeString
        } ?: MessageType.Unknown

        return when (messageType) {
            MessageType.SubscribeTrigger -> HaRequest.SubscribeTriggerRequest.serializer()
            MessageType.SubscribeEvents -> HaRequest.SubscribeEventsRequest.serializer()
            MessageType.UnsubscribeEvents -> HaRequest.UnsubscribeEventsRequest.serializer()
            MessageType.Result -> ResultResponse.serializer()
            MessageType.Event -> EventResponse.serializer()
            MessageType.Pong -> HaResponse.Pong.serializer()

            // These shouldn't be received (only sent)
            MessageType.Authorize,
            MessageType.AuthorizationRequired,
            MessageType.AuthorizationOk,
            MessageType.AuthorizationInvalid,
            MessageType.CallServiceAction,
            MessageType.GetStates,
            MessageType.Ping,
            MessageType.Unknown -> throw SerializationException("Unexpected message type: $messageType")
        }
    }
}