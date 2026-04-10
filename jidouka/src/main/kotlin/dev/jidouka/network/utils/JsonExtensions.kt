package dev.jidouka.network.utils

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Extension functions for converting JsonObject and JsonElement to native Kotlin types.
 */

/**
 * Converts a JsonObject to a Map with native Kotlin types.
 *
 * @return Map<String, Any?> where values are native Kotlin types (String, Number, Boolean, List, Map, null)
 */
internal fun JsonObject.toNativeMap(): Map<String, Any?> {
    return this.mapValues { (_, value) ->
        value.toNativeValue()
    }
}

/**
 * Converts a JsonElement to its native Kotlin type equivalent.
 *
 * - JsonPrimitive -> String, Boolean, Int, Long, Double (based on content)
 * - JsonObject -> Map<String, Any?>
 * - JsonArray -> List<Any?>
 * - JsonNull -> null
 *
 * @return The native Kotlin representation of the JsonElement
 */
internal fun JsonElement.toNativeValue(): Any? {
    return when (this) {
        is JsonArray -> this.map { it.toNativeValue() }
        is JsonObject -> this.toNativeMap()
        is JsonNull -> null
        is JsonPrimitive -> {
            when {
                isString -> content
                else -> {
                    // Try parsing in order of specificity
                    booleanOrNull ?: intOrNull ?: longOrNull ?: doubleOrNull ?: content
                }
            }
        }
    }
}