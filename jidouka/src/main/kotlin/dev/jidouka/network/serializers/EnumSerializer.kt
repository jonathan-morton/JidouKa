package dev.jidouka.network.serializers

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer

internal class EnumSerializer<T : Enum<T>>(
    private val defaultValue: T,
    private val enumSerializer: KSerializer<T>
) : KSerializer<T> {
    private val logger = KotlinLogging.logger {}

    override val descriptor: SerialDescriptor = enumSerializer.descriptor

    override fun serialize(encoder: Encoder, value: T) {
        enumSerializer.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): T {
        return try {
            enumSerializer.deserialize(decoder)
        } catch (exception: SerializationException) {
            logger.warn(exception) { "Failed to deserialize enum, using default value: $defaultValue" }
            defaultValue
        }
    }
}

internal inline fun <reified T : Enum<T>> enumSerializer(
    defaultValue: T
): KSerializer<T> = EnumSerializer(
    defaultValue = defaultValue,
    enumSerializer = serializer()
)