package dev.jidouka.automations.dsl.triggers

import dev.jidouka.aliases.WebhookId
import dev.jidouka.network.models.hass.websocket.trigger.WebhookHttpMethod

public data class WebhookTriggerConfiguration(
    val id: WebhookId,
    val allowedMethods: Set<WebhookHttpMethod> = setOf(WebhookHttpMethod.PUT),
    val isLocalOnly: Boolean = true
) {
    internal companion object {
        fun requireWebhookConfigurationMatches(
            webhookId: WebhookId,
            existingAllowedMethods: Set<WebhookHttpMethod>,
            incomingAllowedMethods: Set<WebhookHttpMethod>,
            existingIsLocalOnly: Boolean,
            incomingIsLocalOnly: Boolean
        ) {
            if (existingAllowedMethods != incomingAllowedMethods || existingIsLocalOnly != incomingIsLocalOnly) {
                error(
                    "Webhook '${webhookId}' is already registered with " +
                            "allowedMethods=${existingAllowedMethods}, isLocalOnly=${existingIsLocalOnly}. " +
                            "Cannot register with allowedMethods=${incomingAllowedMethods}, isLocalOnly=${incomingIsLocalOnly}."
                )
            }
        }
    }
}
