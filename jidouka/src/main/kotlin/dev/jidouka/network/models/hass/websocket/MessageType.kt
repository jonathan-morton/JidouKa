package dev.jidouka.network.models.hass.websocket

import dev.jidouka.network.serializers.MessageTypeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable(with = MessageTypeSerializer::class)
internal enum class MessageType {
    @SerialName("unknown")
    Unknown,

    @SerialName("auth")
    Authorize,

    @SerialName("auth_required")
    AuthorizationRequired,

    @SerialName("auth_ok")
    AuthorizationOk,

    @SerialName("auth_invalid")
    AuthorizationInvalid,

    @SerialName("result")
    Result,

    @SerialName("event")
    Event,

    @SerialName("subscribe_events")
    SubscribeEvents,

    @SerialName("unsubscribe_events")
    UnsubscribeEvents,

    @SerialName("subscribe_trigger")
    SubscribeTrigger,

    @SerialName("call_service")
    CallServiceAction,

    @SerialName("get_states")
    GetStates,

    @SerialName("ping")
    Ping,

    @SerialName("pong")
    Pong,
    ;

    val serialName: String
        get() {
            val field = MessageType::class.java.getField(this.name)
            val annotation = field.getAnnotation(SerialName::class.java)
            return annotation?.value ?: this.name.lowercase()
        }

}