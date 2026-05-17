package dev.jidouka.automations.registry.subscription

import dev.jidouka.network.models.hass.websocket.trigger.WebhookHttpMethod

internal data class WebhookSubscriptionConfiguration(
    val allowedMethods: Set<WebhookHttpMethod>,
    val isLocalOnly: Boolean
)
