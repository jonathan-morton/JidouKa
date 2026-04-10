package dev.jidouka.actions

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Response from a Home Assistant action call.
 *
 * @property data The response data
 */
public open class ActionResponse(
    public val data: Map<String, Any?>
) {
    /**
     * Convert this response to a typed response.
     *
     * @param factory Constructor that takes (Map<String, Any?>, Json) and returns T
     * @param json Json instance to use for deserialization
     * @return Typed response instance
     */
    public fun <T : ActionResponse> typed(
        factory: (Map<String, Any?>, Json) -> T,
        json: Json
    ): T {
        return factory(this.data, json)
    }

    public inline fun <reified T> decode(json: Json): T {
        val jsonObject = json.encodeToJsonElement(data) as JsonObject
        return json.decodeFromJsonElement(jsonObject)
    }
}