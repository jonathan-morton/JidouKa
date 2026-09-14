package dev.jidouka.common.network.utils

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonPrimitiveClassificationTest {
    @Test
    fun `classifies boolean`() = assertEquals(JsonPrimitiveKind.BOOLEAN, JsonPrimitive(true).classifyKind())

    @Test
    fun `classifies int`() = assertEquals(JsonPrimitiveKind.INT, JsonPrimitive(42).classifyKind())

    @Test
    fun `classifies long above Int MAX_VALUE`() =
        assertEquals(JsonPrimitiveKind.LONG, JsonPrimitive(2147483648L).classifyKind())

    @Test
    fun `classifies double`() = assertEquals(JsonPrimitiveKind.DOUBLE, JsonPrimitive(1.5).classifyKind())

    @Test
    fun `classifies string`() = assertEquals(JsonPrimitiveKind.STRING, JsonPrimitive("hello").classifyKind())

    @Test
    fun `classifies numeric string as string`() =
        assertEquals(JsonPrimitiveKind.STRING, JsonPrimitive("42").classifyKind())
}