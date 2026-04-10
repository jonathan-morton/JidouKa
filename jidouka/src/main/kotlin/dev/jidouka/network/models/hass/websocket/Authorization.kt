package dev.jidouka.network.models.hass.websocket

import dev.jidouka.extensions.traceF
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal sealed class AuthorizationMessage {
    @Contextual
    abstract val type: MessageType

    @Serializable
    data class Required(
        @SerialName("ha_version")
        val haVersion: String
    ) : AuthorizationMessage() {
        @Contextual
        override val type: MessageType = MessageType.AuthorizationRequired
    }

    @Serializable
    data class Authorize(
        @SerialName("access_token")
        val accessToken: String
    ) : AuthorizationMessage() {
        @Contextual
        override val type: MessageType = MessageType.Authorize
    }

    @Serializable
    data class Ok(
        @SerialName("ha_version")
        val haVersion: String
    ) : AuthorizationMessage() {
        @Contextual
        override val type: MessageType = MessageType.AuthorizationOk
    }

    @Serializable
    data class Invalid(
        val message: String
    ) : AuthorizationMessage() {
        @Contextual
        override val type: MessageType = MessageType.AuthorizationInvalid
    }
}

internal class AuthorizationMessageSerializer(
    private val json: Json
) : KSerializer<AuthorizationMessage> {
    private val logger = KotlinLogging.logger {}

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AuthorizationMessage")

    override fun serialize(
        encoder: Encoder,
        value: AuthorizationMessage
    ) {
        val jsonEncoder = encoder as? JsonEncoder ?: return
        logger.traceF { value.toString() }
        val element = when (value) {
            is AuthorizationMessage.Authorize -> json.encodeToJsonElement(value)
            is AuthorizationMessage.Invalid -> json.encodeToJsonElement(value)
            is AuthorizationMessage.Ok -> json.encodeToJsonElement(value)
            is AuthorizationMessage.Required -> json.encodeToJsonElement(value)
        }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): AuthorizationMessage {
        val jsonDecoder = decoder as? JsonDecoder ?: throw Exception("Decoder is not a JsonDecoder")
        val element = jsonDecoder.decodeJsonElement()

        val typeElement = element.jsonObject["type"]
            ?: throw SerializationException("Missing 'type' field in auth message")

        @Suppress("MoveVariableDeclarationIntoWhen")
        val messageType = json.decodeFromJsonElement<MessageType>(typeElement)

        return when (messageType) {
            MessageType.AuthorizationRequired -> json.decodeFromJsonElement<AuthorizationMessage.Required>(element)
            MessageType.Authorize -> json.decodeFromJsonElement<AuthorizationMessage.Authorize>(element)
            MessageType.AuthorizationOk -> json.decodeFromJsonElement<AuthorizationMessage.Ok>(element)
            MessageType.AuthorizationInvalid -> json.decodeFromJsonElement<AuthorizationMessage.Invalid>(element)
            MessageType.Unknown,
            MessageType.Result,
            MessageType.Event,
            MessageType.SubscribeEvents,
            MessageType.SubscribeTrigger,
            MessageType.UnsubscribeEvents,
            MessageType.CallServiceAction,
            MessageType.GetStates,
            MessageType.Ping,
            MessageType.Pong -> {
                throw SerializationException("Invalid auth message type: $messageType")
            }
        }
    }
}