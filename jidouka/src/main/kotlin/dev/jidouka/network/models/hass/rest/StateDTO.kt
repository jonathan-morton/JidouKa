package dev.jidouka.network.models.hass.rest

import dev.jidouka.aliases.EntityId
import dev.jidouka.components.StateObject
import dev.jidouka.network.utils.toNativeMap
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

@Serializable
internal data class StateDTO(
    @SerialName("entity_id")
    val entityId: EntityId,
    @SerialName("state")
    val state: String,
    @SerialName("attributes")
    val attributes: JsonObject,
    @Contextual
    @SerialName("last_changed")
    val lastChanged: Instant,
    @Contextual
    @SerialName("last_updated")
    val lastUpdated: Instant,

    ) {
    fun toStateObject() = StateObject(
        entityId = entityId,
        state = state,
        attributesRaw = attributes.toNativeMap(),
        lastChanged = lastChanged,
        lastUpdated = lastUpdated,
        lastReported = null,
        context = null
    )
}