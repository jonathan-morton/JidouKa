package dev.jidouka.network

import dev.jidouka.network.models.hass.websocket.MessageType
import dev.jidouka.network.serializers.InstantSerializer
import dev.jidouka.network.serializers.enumSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.koin.core.annotation.Single
import kotlin.time.Instant

@Single
internal class JsonManager {
    private object MessageTypeSerializer : KSerializer<MessageType> by enumSerializer(MessageType.Unknown)

    private val haSerializersModule = SerializersModule {
        contextual(MessageType::class, MessageTypeSerializer)
        contextual(Instant::class, InstantSerializer)
    }

    val json = Json {
        ignoreUnknownKeys = true
        serializersModule = haSerializersModule
        encodeDefaults = true
        explicitNulls = false
    }
}