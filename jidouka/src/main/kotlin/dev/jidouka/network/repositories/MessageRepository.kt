package dev.jidouka.network.repositories

import dev.jidouka.extensions.traceF
import dev.jidouka.network.JsonManager
import dev.jidouka.network.models.hass.websocket.AuthorizationMessage
import dev.jidouka.network.models.hass.websocket.AuthorizationMessageSerializer
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.annotation.Single

internal interface MessageRepository {
    suspend fun parseAuthorizationMessage(rawJson: String): AuthorizationMessage
    suspend fun serializeAuthorizationMessage(message: AuthorizationMessage): String
}

@Single(binds = [MessageRepository::class])
internal class MessageRepositoryDefault(
    private val jsonManager: JsonManager
) : MessageRepository {
    private val authorizationSerializer = AuthorizationMessageSerializer(jsonManager.json)
    val logger = KotlinLogging.logger {}

    override suspend fun parseAuthorizationMessage(rawJson: String): AuthorizationMessage {
        val json = jsonManager.json
        logger.traceF { rawJson }
        return json.decodeFromString(authorizationSerializer, rawJson)
    }

    override suspend fun serializeAuthorizationMessage(message: AuthorizationMessage): String {
        val json = jsonManager.json
        return json.encodeToString(authorizationSerializer, message)
    }
}