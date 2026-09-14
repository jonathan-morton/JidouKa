package dev.jidouka.network.utils

import dev.jidouka.common.network.utils.JsonPrimitiveKind
import dev.jidouka.common.network.utils.classifyKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long

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
        is JsonPrimitive -> when (classifyKind()) {
            JsonPrimitiveKind.STRING -> content
            JsonPrimitiveKind.BOOLEAN -> boolean
            JsonPrimitiveKind.INT -> int
            JsonPrimitiveKind.LONG -> long
            JsonPrimitiveKind.DOUBLE -> double
        }
    }
}