package dev.jidouka.automations.registry.subscription

import dev.jidouka.common.network.models.hass.websocket.trigger.WebhookHttpMethod

internal data class WebhookSubscriptionConfiguration(
    val allowedMethods: Set<WebhookHttpMethod>,
    val isLocalOnly: Boolean
)
