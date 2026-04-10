package dev.jidouka.network.models.hass.websocket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.VisibleForTesting

@Serializable
@VisibleForTesting
public data class ActionTarget(
    @SerialName("entity_id")
    val entityIds: List<String>? = null,
    @SerialName("area_id")
    val areaIds: List<String>? = null,
    @SerialName("device_id")
    val deviceIds: List<String>? = null,
    @SerialName("floor_id")
    val floorIds: List<String>? = null,
    @SerialName("label_id")
    val labelIds: List<String>? = null
)