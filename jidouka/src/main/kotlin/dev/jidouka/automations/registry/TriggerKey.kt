package dev.jidouka.automations.registry

import dev.jidouka.automations.dsl.triggers.TriggerMetadata

internal sealed class TriggerKey {
    internal data class Entity(val entityId: String) : TriggerKey()
    internal data class Event(val eventType: String) : TriggerKey()
    internal data class Webhook(val webhookId: String) : TriggerKey()

    internal companion object {
        internal fun getKeys(metadata: TriggerMetadata): List<TriggerKey> {
            return when (metadata) {
                is TriggerMetadata.StateTrigger -> {
                    metadata.entityIds.map { id ->
                        Entity(id)
                    }
                }

                is TriggerMetadata.EventTrigger -> {
                    metadata.eventTypes.map { eventType ->
                        Event(eventType)
                    }
                }

                is TriggerMetadata.WebhookTrigger -> {
                    metadata.webhookIds.map { id ->
                        Webhook(id)
                    }
                }
            }
        }
    }
}