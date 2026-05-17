package dev.jidouka.network.models.hass.websocket

import dev.jidouka.network.models.hass.websocket.trigger.WebhookHttpMethod
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant


@Serializable
internal data class TriggerConfiguration(
    @SerialName("platform")
    val platform: Platform,
    @SerialName("entity_id")
    val entityId: String? = null,
    @SerialName("event_type")
    val eventType: String? = null,
    @SerialName("from")
    val from: String? = null,
    @SerialName("to")
    val to: String? = null,
    @SerialName("for")
    val forDuration: String? = null,
    @SerialName("attribute")
    val attribute: String? = null,
    //region webhooks
    @SerialName("webhook_id")
    val webhookId: String? = null,
    @SerialName("allowed_methods")
    val allowedMethods: List<WebhookHttpMethod>? = null,
    @SerialName("local_only")
    val isLocalOnly: Boolean? = null

    //endregion
) {
    @Serializable
    enum class Platform {
        @SerialName("unknown")
        Unknown,

        @SerialName("state")
        State,

        @SerialName("event")
        Event,

        @SerialName("webhook")
        Webhook,
    }
}

//region Trigger event response
@Serializable
internal data class EventData(
    @SerialName("event_type")
    val eventType: String,
    @SerialName("data")
    val data: JsonObject,
    @SerialName("origin")
    val origin: String,
    @SerialName("time_fired")
    @Contextual
    val timeFired: Instant,
    @SerialName("context")
    val context: Context
)

@Serializable
internal data class TriggerEvent(
    @SerialName("variables")
    val variables: TriggerVariables,
    @SerialName("context")
    val context: Context? = null
)

@Serializable
internal data class TriggerVariables(
    @SerialName("trigger")
    val trigger: TriggerData
)

@Serializable
internal data class TriggerData(
    @SerialName("id")
    val id: String,
    @SerialName("idx")
    val idx: String,
    @SerialName("alias")
    val alias: String?,
    @SerialName("platform")
    val platform: TriggerConfiguration.Platform,
    @SerialName("entity_id")
    val entityId: String? = null,
    @SerialName("from_state")
    val fromState: StateData? = null,
    @SerialName("to_state")
    val toState: StateData? = null,
    @SerialName("for")
    val forDuration: String? = null,
    @SerialName("attribute")
    val attribute: String? = null,
    @SerialName("event")
    val event: EventData? = null,
    @SerialName("description")
    val description: String,
    //region webhook
    @SerialName("webhook_id")
    val webhookId: String? = null,
    @SerialName("json")
    val json: JsonObject? = null,
    @SerialName("data")
    val data: MultiDictRepresentation? = null,
    @SerialName("query")
    val query: MultiDictRepresentation? = null
    //endregion
)

//endregion

@Serializable
internal data class StateData(
    @SerialName("entity_id")
    val entityId: String,
    @SerialName("state")
    val state: String,
    @SerialName("attributes")
    val attributes: JsonObject,
    @SerialName("last_changed")
    @Contextual
    val lastChanged: Instant,
    @SerialName("last_reported")
    @Contextual
    val lastReported: Instant,
    @SerialName("last_updated")
    @Contextual
    val lastUpdated: Instant,
    @SerialName("context")
    val context: Context
)

//region Webhook
/**
 * Home Assistant's representation of Python MultiDictProxy objects.
 * Used for form data and query parameters in webhook payloads.
 */
@Serializable
internal data class MultiDictRepresentation(
    @SerialName("__type")
    val type: String,
    @SerialName("repr")
    val representation: String
)
//endregion
