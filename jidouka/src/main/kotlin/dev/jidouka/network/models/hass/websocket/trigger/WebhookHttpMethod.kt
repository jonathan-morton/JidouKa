package dev.jidouka.network.models.hass.websocket.trigger

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class WebhookHttpMethod {
    @SerialName("GET")
    GET,

    @SerialName("HEAD")
    HEAD,

    @SerialName("POST")
    POST,

    @SerialName("PUT")
    PUT
}