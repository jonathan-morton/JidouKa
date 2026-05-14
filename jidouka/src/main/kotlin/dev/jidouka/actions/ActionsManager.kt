package dev.jidouka.actions

import dev.jidouka.client.HomeAssistantWebSocket
import dev.jidouka.network.JsonManager
import dev.jidouka.network.models.hass.websocket.ActionTarget
import dev.jidouka.network.utils.JsonObjectBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

internal interface ActionsManager {
    val json: Json

    suspend fun callAction(
        domain: String,
        action: String,
        target: ActionTarget?,
        data: Map<String, Any?>
    ): Result<Unit>

    suspend fun callActionWithResponse(
        domain: String,
        action: String,
        target: ActionTarget?,
        data: Map<String, Any?>
    ): Result<ActionResponse?>
}

@Single
internal class HomeAssistantActionsManager(
    private val client: HomeAssistantWebSocket,
    private val jsonManager: JsonManager,
) : ActionsManager {
    private val logger = KotlinLogging.logger {}

    override val json: Json
        get() = jsonManager.json

    override suspend fun callAction(
        domain: String,
        action: String,
        target: ActionTarget?,
        data: Map<String, Any?>
    ): Result<Unit> {
        logger.info { "Calling action '$domain.$action'" }
        target?.let {
            val targetCount = (target.entityIds?.size ?: 0) + (target.areaIds?.size ?: 0) +
                    (target.deviceIds?.size ?: 0) + (target.floorIds?.size ?: 0) +
                    (target.labelIds?.size ?: 0)
            logger.debug { "Calling action '$domain.$action' on $targetCount target(s)${if (data.isNotEmpty()) " with $data" else ""}" }
        }

        val serviceData = if (data.isEmpty()) null else {
            JsonObjectBuilder.buildJsonObject(data)
        }

        val result = client.callServiceAction(
            domain = domain,
            service = action,
            target = target,
            serviceData = serviceData,
            returnResponse = false
        ).map { }

        result.onSuccess {
            logger.debug { "Action '$domain.$action' completed successfully" }
        }.onFailure { error ->
            logger.error(error) { "Action '$domain.$action' failed" }
        }

        return result
    }

    override suspend fun callActionWithResponse(
        domain: String,
        action: String,
        target: ActionTarget?,
        data: Map<String, Any?>
    ): Result<ActionResponse?> {
        logger.info { "Calling action '$domain.$action' with response" }
        target?.let {
            val targetCount = (target.entityIds?.size ?: 0) + (target.areaIds?.size ?: 0) +
                    (target.deviceIds?.size ?: 0) + (target.floorIds?.size ?: 0) +
                    (target.labelIds?.size ?: 0)
            logger.debug { "Calling action '$domain.$action' on $targetCount target(s) with response${if (data.isNotEmpty()) " and $data" else ""}" }
        }

        val serviceData = if (data.isEmpty()) null else {
            JsonObjectBuilder.buildJsonObject(data)
        }

        val result = client.callServiceAction(
            domain = domain,
            service = action,
            target = target,
            serviceData = serviceData,
            returnResponse = true
        )

        result.onSuccess { response ->
            logger.debug { "Action '$domain.$action' completed successfully${if (response != null) " with response" else " (no response)"}" }
        }.onFailure { error ->
            logger.error(error) { "Action '$domain.$action' with response failed" }
        }

        return result
    }
}