package dev.jidouka.network.utils

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Utility for converting Map<String, Any?> to JsonObject.
 */
internal object JsonObjectBuilder {

    /**
     * Builds a JsonObject from a Map<String, Any?>.
     *
     * @param data The map to convert
     * @return JsonObject representation of the map
     * @throws IllegalArgumentException if an unsupported type is encountered
     */
    fun buildJsonObject(data: Map<String, Any?>): JsonObject {
        return JsonObject(
            data.mapValues { (key, value) ->
                convertToJsonElement(value, path = key)
            }
        )
    }

    /**
     * Converts any value to a JsonElement.
     *
     * @param value The value to convert
     * @param path The current path (for error messages)
     * @return JsonElement representation of the value
     * @throws IllegalArgumentException if an unsupported type is encountered
     */
    private fun convertToJsonElement(value: Any?, path: String = ""): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Array<*> -> convertArrayToJsonArray(value, path)
            is List<*> -> convertListToJsonArray(value, path)
            is Map<*, *> -> convertMapToJsonObject(value, path)
            is JsonElement -> value
            else -> throw IllegalArgumentException(
                "Unsupported type '${value::class.simpleName}' at path '$path'. " +
                        "Supported types: String, Number, Boolean, Array, List, Map, null, JsonElement"
            )
        }
    }

    /**
     * Converts an Array to a JsonArray.
     *
     * @param array The array to convert
     * @param path The current path (for error messages)
     * @return JsonArray representation of the array
     */
    private fun convertArrayToJsonArray(array: Array<*>, path: String): JsonArray {
        return JsonArray(
            array.mapIndexed { index, item ->
                convertToJsonElement(item, "$path[$index]")
            }
        )
    }

    /**
     * Converts a List to a JsonArray.
     *
     * @param list The list to convert
     * @param path The current path (for error messages)
     * @return JsonArray representation of the list
     */
    private fun convertListToJsonArray(list: List<*>, path: String): JsonArray {
        return JsonArray(
            list.mapIndexed { index, item ->
                convertToJsonElement(item, "$path[$index]")
            }
        )
    }

    /**
     * Converts a Map to a JsonObject.
     *
     * @param map The map to convert
     * @param path The current path (for error messages)
     * @return JsonObject representation of the map
     * @throws IllegalArgumentException if keys are not strings
     */
    private fun convertMapToJsonObject(map: Map<*, *>, path: String): JsonObject {
        val jsonMap = mutableMapOf<String, JsonElement>()

        map.forEach { (key, value) ->
            if (key !is String) {
                throw IllegalArgumentException(
                    "Map keys must be strings. Found '${key?.let { it::class.simpleName } ?: "null"}' at path '$path'"
                )
            }
            val elementPath = if (path.isNotEmpty()) "$path.$key" else key
            jsonMap[key] = convertToJsonElement(value, elementPath)
        }

        return JsonObject(jsonMap)
    }
}