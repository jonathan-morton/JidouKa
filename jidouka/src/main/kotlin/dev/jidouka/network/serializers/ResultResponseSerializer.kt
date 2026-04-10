package dev.jidouka.network.serializers

import dev.jidouka.network.models.hass.websocket.ResultResponse
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object ResultResponseSerializer : JsonContentPolymorphicSerializer<ResultResponse>(ResultResponse::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ResultResponse> {
        val jsonObject = element.jsonObject
        val success = jsonObject["success"]?.jsonPrimitive?.boolean ?: true

        return if (success) {
            ResultResponse.Success.serializer()
        } else {
            ResultResponse.Error.serializer()
        }
    }
}
