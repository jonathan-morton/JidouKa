package dev.jidouka.network.models.hass.websocket

import dev.jidouka.network.serializers.EventResponseSerializer
import dev.jidouka.network.serializers.MessageBaseSerializer
import dev.jidouka.network.serializers.ResultResponseSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

//region BASE MESSAGE

@Serializable(with = MessageBaseSerializer::class)
internal sealed class MessageBase {
    abstract val id: Int

    @SerialName("type")
    abstract val messageType: MessageType
}

//endregion

//region REQUESTS (Outgoing)

internal sealed class HaRequest : MessageBase() {

    @Serializable
    data class SubscribeTriggerRequest(
        override val id: Int,
        @SerialName("trigger")
        val trigger: TriggerConfiguration
    ) : HaRequest() {
        @SerialName("type")
        override val messageType: MessageType = MessageType.SubscribeTrigger
    }

    @Serializable
    data class SubscribeEventsRequest(
        override val id: Int,
        @SerialName("event_type")
        val eventType: String //TODO create enum
    ) : HaRequest() {
        @SerialName("type")
        override val messageType: MessageType = MessageType.SubscribeEvents
    }

    @Serializable
    data class UnsubscribeEventsRequest(
        override val id: Int,
        @SerialName("subscription")
        val subscriptionId: Int
    ) : HaRequest() {
        @SerialName("type")
        override val messageType: MessageType = MessageType.UnsubscribeEvents
    }

    @Serializable
    data class CallServiceActionRequest(
        override val id: Int,
        @SerialName("domain")
        val domain: String,
        @SerialName("service")
        val action: String,
        @SerialName("target")
        val target: ActionTarget? = null,
        @SerialName("service_data")
        val actionData: JsonObject? = null,
        @SerialName("return_response")
        val returnResponse: Boolean = false
    ) : HaRequest() {
        @SerialName("type")
        override val messageType: MessageType = MessageType.CallServiceAction
    }

    @Serializable
    data class GetStatesRequest(
        override val id: Int
    ) : HaRequest() {
        @SerialName("type")
        override val messageType: MessageType = MessageType.GetStates
    }

    @Serializable
    data class PingRequest(
        override val id: Int
    ) : HaRequest() {
        @SerialName("type")
        override val messageType: MessageType = MessageType.Ping
    }
}

//endregion

//region RESPONSES (Incoming)

@Serializable
internal sealed class HaResponse : MessageBase() {
    @Serializable
    data class Pong(
        override val id: Int
    ) : HaResponse() {
        @SerialName("type")
        override val messageType: MessageType = MessageType.Pong
    }
}

//region RESULT RESPONSE

@Serializable(with = ResultResponseSerializer::class)
internal sealed class ResultResponse : HaResponse() {
    abstract val success: Boolean

    @SerialName("type")
    override val messageType: MessageType = MessageType.Result

    @Serializable
    data class Success(
        override val id: Int,
        override val success: Boolean = true,
        @SerialName("result")
        val result: JsonElement?
    ) : ResultResponse() {

        @Serializable
        data class Data(
            @SerialName("context")
            val context: Context? = null,
            @SerialName("response")
            val response: JsonObject? = null
        )
    }

    @Serializable
    data class Error(
        override val id: Int,
        override val success: Boolean = false,
        @SerialName("error")
        val error: JsonObject
    ) : ResultResponse() {

        val code: String
            get() = error["code"]?.toString()?.removeSurrounding("\"") ?: "unknown_error"

        val message: String
            get() = error["message"]?.toString()?.removeSurrounding("\"") ?: "Unknown error occurred"
    }
}

//endregion

//region EVENT RESPONSE

@Serializable(with = EventResponseSerializer::class)
internal sealed class EventResponse : HaResponse() {
    @SerialName("type")
    override val messageType: MessageType = MessageType.Event

    @Serializable
    data class TriggerEventResponse(
        override val id: Int,
        @SerialName("event")
        val event: TriggerEvent
    ) : EventResponse()
}


//endregion

//endregion

//region CONTEXT

@Serializable
public data class Context(
    @SerialName("id")
    public val id: String,
    @SerialName("parent_id")
    public val parentId: String?,
    @SerialName("user_id")
    public val userId: String?
)

//endregion

