package dev.jidouka.automations.dsl.triggers

import dev.jidouka.aliases.EntityId
import dev.jidouka.aliases.EventTypeId

internal sealed class TriggerMetadata {
    internal data class StateTrigger(
        val entityIds: Set<EntityId>
    ) : TriggerMetadata()

    internal data class EventTrigger(
        val eventTypes: Set<EventTypeId>
    ) : TriggerMetadata()
}