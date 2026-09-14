package dev.jidouka.common.network.utils

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

public enum class JsonPrimitiveKind { BOOLEAN, INT, LONG, DOUBLE, STRING }

public fun JsonPrimitive.classifyKind(): JsonPrimitiveKind {
    return when {
        isString -> JsonPrimitiveKind.STRING
        booleanOrNull != null -> JsonPrimitiveKind.BOOLEAN
        intOrNull != null -> JsonPrimitiveKind.INT
        longOrNull != null -> JsonPrimitiveKind.LONG
        doubleOrNull != null -> JsonPrimitiveKind.DOUBLE
        else -> JsonPrimitiveKind.STRING
    }
}