package dev.jidouka.network.serializers

import dev.jidouka.network.models.hass.websocket.MessageType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

internal object MessageTypeSerializer : KSerializer<MessageType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("MessageType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: MessageType) {
        encoder.encodeString(value.serialName)
    }

    override fun deserialize(decoder: Decoder): MessageType {
        val stringValue = decoder.decodeString()

        return MessageType.entries.firstOrNull { entry ->
            entry.serialName == stringValue
        } ?: MessageType.Unknown
    }
}
