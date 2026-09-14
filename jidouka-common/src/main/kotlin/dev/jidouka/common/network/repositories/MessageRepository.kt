package dev.jidouka.common.network.repositories

import dev.jidouka.common.extensions.traceF
import dev.jidouka.common.network.JsonManager
import dev.jidouka.common.network.models.hass.websocket.AuthorizationMessage
import dev.jidouka.common.network.models.hass.websocket.AuthorizationMessageSerializer
import dev.jidouka.common.network.models.hass.websocket.HaRequest
import dev.jidouka.common.network.models.hass.websocket.MessageBase
import dev.jidouka.common.network.models.hass.websocket.ResultResponse
import dev.jidouka.common.network.models.hass.websocket.StateData
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.koin.core.annotation.Single

public interface MessageRepository {
    public suspend fun parseAuthorizationMessage(rawJson: String): AuthorizationMessage
    public suspend fun serializeAuthorizationMessage(message: AuthorizationMessage): String

    public suspend fun parseMessage(rawJson: String): MessageBase
    public suspend fun serializeRequest(request: HaRequest): String

    public suspend fun parseStates(result: JsonElement): List<StateData>
    public suspend fun parseServiceActionData(result: JsonElement): ResultResponse.Success.Data

}

@Single(binds = [MessageRepository::class])
public class MessageRepositoryDefault(
    private val jsonManager: JsonManager
) : MessageRepository {
    private val authorizationSerializer = AuthorizationMessageSerializer(jsonManager.json)
    private val logger = KotlinLogging.logger {}

    override suspend fun parseAuthorizationMessage(rawJson: String): AuthorizationMessage {
        val json = jsonManager.json
        logger.traceF { rawJson }
        return json.decodeFromString(authorizationSerializer, rawJson)
    }

    override suspend fun serializeAuthorizationMessage(message: AuthorizationMessage): String {
        val json = jsonManager.json
        return json.encodeToString(authorizationSerializer, message)
    }

    override suspend fun parseMessage(rawJson: String): MessageBase {
        val json = jsonManager.json
        return json.decodeFromString(rawJson)
    }

    override suspend fun serializeRequest(request: HaRequest): String {
        val json = jsonManager.json
        return json.encodeToString<MessageBase>(request)
    }

    override suspend fun parseStates(result: JsonElement): List<StateData> {
        val json = jsonManager.json
        return json.decodeFromJsonElement(result)
    }

    override suspend fun parseServiceActionData(result: JsonElement): ResultResponse.Success.Data {
        val json = jsonManager.json
        return json.decodeFromJsonElement(result)
    }
}