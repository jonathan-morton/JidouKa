package dev.jidouka.client

import dev.jidouka.actions.ActionResponse
import dev.jidouka.aliases.SubscriptionId
import dev.jidouka.network.models.hass.websocket.ActionTarget
import dev.jidouka.network.models.hass.websocket.StateData
import kotlinx.serialization.json.JsonObject

internal interface HomeAssistantWebSocket {
    /**
     * Subscribe to state changes for an entity.
     * @param entityId The entity ID to subscribe to
     * @return The subscription ID from Home Assistant
     */
    suspend fun subscribeToEntity(entityId: String): SubscriptionId

    /**
     * Subscribe to events of a specific type.
     * @param eventType The event type to subscribe to
     * @return The subscription ID from Home Assistant
     */
    suspend fun subscribeToEvent(eventType: String): SubscriptionId

    /**
     * Unsubscribe from state changes using the subscription ID.
     * @param subscriptionId The subscription ID from Home Assistant
     */
    suspend fun unsubscribe(subscriptionId: SubscriptionId)

    suspend fun callServiceAction(
        domain: String,
        service: String,
        target: ActionTarget?,
        serviceData: JsonObject?,
        returnResponse: Boolean
    ): Result<ActionResponse?>

    suspend fun getStates(): Result<List<StateData>>
}
