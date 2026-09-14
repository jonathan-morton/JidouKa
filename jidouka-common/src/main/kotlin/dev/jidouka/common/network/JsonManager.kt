package dev.jidouka.common.network


import dev.jidouka.common.network.models.hass.websocket.MessageType
import dev.jidouka.common.network.serializers.InstantSerializer
import dev.jidouka.common.network.serializers.enumSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.koin.core.annotation.Single
import kotlin.time.Instant

@Single
public class JsonManager {
    private object MessageTypeSerializer : KSerializer<MessageType> by enumSerializer(MessageType.Unknown)

    private val haSerializersModule = SerializersModule {
        contextual(MessageType::class, MessageTypeSerializer)
        contextual(Instant::class, InstantSerializer)
    }

    public val json: Json = Json {
        ignoreUnknownKeys = true
        serializersModule = haSerializersModule
        encodeDefaults = true
        explicitNulls = false
    }
}