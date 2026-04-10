package dev.jidouka.network.utils

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JsonObjectBuilderTest {

    @Test
    fun `buildJsonObject handles empty map`() {
        val result = JsonObjectBuilder.buildJsonObject(emptyMap())
        assertEquals(JsonObject(emptyMap()), result)
    }

    @Test
    fun `buildJsonObject handles null values`() {
        val data = mapOf("key" to null)
        val result = JsonObjectBuilder.buildJsonObject(data)

        assertEquals(JsonNull, result["key"])
    }

    @Test
    fun `buildJsonObject handles String values`() {
        val data = mapOf(
            "name" to "John Doe",
            "description" to "Test description"
        )
        val result = JsonObjectBuilder.buildJsonObject(data)

        assertEquals(JsonPrimitive("John Doe"), result["name"])
        assertEquals(JsonPrimitive("Test description"), result["description"])
    }

    @Test
    fun `buildJsonObject handles Number values`() {
        val data = mapOf(
            "intValue" to 42,
            "longValue" to 100L,
            "floatValue" to 3.14f,
            "doubleValue" to 2.71828
        )
        val result = JsonObjectBuilder.buildJsonObject(data)

        assertEquals(JsonPrimitive(42), result["intValue"])
        assertEquals(JsonPrimitive(100L), result["longValue"])
        assertEquals(JsonPrimitive(3.14f), result["floatValue"])
        assertEquals(JsonPrimitive(2.71828), result["doubleValue"])
    }

    @Test
    fun `buildJsonObject handles Boolean values`() {
        val data = mapOf(
            "enabled" to true,
            "disabled" to false
        )
        val result = JsonObjectBuilder.buildJsonObject(data)

        assertEquals(JsonPrimitive(true), result["enabled"])
        assertEquals(JsonPrimitive(false), result["disabled"])
    }

    @Test
    fun `buildJsonObject handles List values`() {
        val data = mapOf(
            "numbers" to listOf(1, 2, 3),
            "strings" to listOf("a", "b", "c"),
            "mixed" to listOf(1, "two", true, null)
        )
        val result = JsonObjectBuilder.buildJsonObject(data)

        val numbersArray = result["numbers"]
        assertIs<JsonArray>(numbersArray)
        assertEquals(3, numbersArray.size)
        assertEquals(JsonPrimitive(1), numbersArray[0])
        assertEquals(JsonPrimitive(2), numbersArray[1])
        assertEquals(JsonPrimitive(3), numbersArray[2])

        val stringsArray = result["strings"]
        assertIs<JsonArray>(stringsArray)
        assertEquals(3, stringsArray.size)
        assertEquals(JsonPrimitive("a"), stringsArray[0])

        val mixedArray = result["mixed"]
        assertIs<JsonArray>(mixedArray)
        assertEquals(4, mixedArray.size)
        assertEquals(JsonPrimitive(1), mixedArray[0])
        assertEquals(JsonPrimitive("two"), mixedArray[1])
        assertEquals(JsonPrimitive(true), mixedArray[2])
        assertEquals(JsonNull, mixedArray[3])
    }

    @Test
    fun `buildJsonObject handles nested Map values`() {
        val data = mapOf(
            "user" to mapOf(
                "name" to "John",
                "age" to 30,
                "settings" to mapOf(
                    "notifications" to true,
                    "theme" to "dark"
                )
            )
        )
        val result = JsonObjectBuilder.buildJsonObject(data)

        val userObject = result["user"]
        assertIs<JsonObject>(userObject)
        assertEquals(JsonPrimitive("John"), userObject["name"])
        assertEquals(JsonPrimitive(30), userObject["age"])

        val settingsObject = userObject["settings"]
        assertIs<JsonObject>(settingsObject)
        assertEquals(JsonPrimitive(true), settingsObject["notifications"])
        assertEquals(JsonPrimitive("dark"), settingsObject["theme"])
    }

    @Test
    fun `buildJsonObject handles complex nested structures`() {
        val data = mapOf(
            "brightness" to 255,
            "color" to mapOf(
                "r" to 255,
                "g" to 128,
                "b" to 0
            ),
            "effects" to listOf("rainbow", "pulse", "fade"),
            "schedule" to mapOf(
                "enabled" to true,
                "times" to listOf(
                    mapOf("hour" to 8, "minute" to 0),
                    mapOf("hour" to 20, "minute" to 30)
                )
            )
        )
        val result = JsonObjectBuilder.buildJsonObject(data)

        assertNotNull(result)
        assertEquals(JsonPrimitive(255), result["brightness"])

        val colorObject = result["color"]
        assertIs<JsonObject>(colorObject)
        assertEquals(JsonPrimitive(255), colorObject["r"])

        val effectsArray = result["effects"]
        assertIs<JsonArray>(effectsArray)
        assertEquals(3, effectsArray.size)

        val scheduleObject = result["schedule"]
        assertIs<JsonObject>(scheduleObject)
        val timesArray = scheduleObject["times"]
        assertIs<JsonArray>(timesArray)
        assertEquals(2, timesArray.size)
    }

    @Test
    fun `buildJsonObject passes through JsonElement values`() {
        val existingJsonObject = JsonObject(mapOf("foo" to JsonPrimitive("bar")))
        val existingJsonArray = JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2)))

        val data = mapOf(
            "object" to existingJsonObject,
            "array" to existingJsonArray,
            "primitive" to JsonPrimitive("test")
        )
        val result = JsonObjectBuilder.buildJsonObject(data)

        assertEquals(existingJsonObject, result["object"])
        assertEquals(existingJsonArray, result["array"])
        assertEquals(JsonPrimitive("test"), result["primitive"])
    }

    @Test
    fun `buildJsonObject throws for unsupported types`() {
        data class UnsupportedType(val value: String)

        val data = mapOf("key" to UnsupportedType("test"))

        val exception = assertFailsWith<IllegalArgumentException> {
            JsonObjectBuilder.buildJsonObject(data)
        }

        assertTrue(exception.message?.contains("Unsupported type") == true)
        assertTrue(exception.message?.contains("UnsupportedType") == true)
        assertTrue(exception.message?.contains("key") == true)
    }

    @Test
    fun `buildJsonObject throws for non-string map keys`() {
        val data = mapOf(
            "nested" to mapOf(
                123 to "value" // Non-string key
            )
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            JsonObjectBuilder.buildJsonObject(data)
        }

        assertEquals(exception.message?.contains("Map keys must be strings"), true)
    }

    @Test
    fun `buildJsonObject handles Array values`() {
        val data = mapOf(
            "intArray" to arrayOf(1, 2, 3),
            "stringArray" to arrayOf("a", "b", "c"),
            "mixedArray" to arrayOf<Any?>(1, "two", true, null),
            "emptyArray" to emptyArray<Any>()
        )
        val result = JsonObjectBuilder.buildJsonObject(data)

        val intArray = result["intArray"]
        assertIs<JsonArray>(intArray)
        assertEquals(3, intArray.size)
        assertEquals(JsonPrimitive(1), intArray[0])
        assertEquals(JsonPrimitive(2), intArray[1])
        assertEquals(JsonPrimitive(3), intArray[2])

        val stringArray = result["stringArray"]
        assertIs<JsonArray>(stringArray)
        assertEquals(3, stringArray.size)
        assertEquals(JsonPrimitive("a"), stringArray[0])
        assertEquals(JsonPrimitive("b"), stringArray[1])
        assertEquals(JsonPrimitive("c"), stringArray[2])

        val mixedArray = result["mixedArray"]
        assertIs<JsonArray>(mixedArray)
        assertEquals(4, mixedArray.size)
        assertEquals(JsonPrimitive(1), mixedArray[0])
        assertEquals(JsonPrimitive("two"), mixedArray[1])
        assertEquals(JsonPrimitive(true), mixedArray[2])
        assertEquals(JsonNull, mixedArray[3])

        val emptyArray = result["emptyArray"]
        assertIs<JsonArray>(emptyArray)
        assertEquals(0, emptyArray.size)
    }

    @Test
    fun `buildJsonObject handles nested Array values`() {
        val data = mapOf(
            "matrix" to arrayOf(
                arrayOf(1, 2, 3),
                arrayOf(4, 5, 6)
            ),
            "arrayWithMap" to arrayOf(
                mapOf("x" to 1, "y" to 2),
                mapOf("x" to 3, "y" to 4)
            )
        )
        val result = JsonObjectBuilder.buildJsonObject(data)

        val matrix = result["matrix"]
        assertIs<JsonArray>(matrix)
        assertEquals(2, matrix.size)

        val row1 = matrix[0]
        assertIs<JsonArray>(row1)
        assertEquals(3, row1.size)
        assertEquals(JsonPrimitive(1), row1[0])
        assertEquals(JsonPrimitive(2), row1[1])
        assertEquals(JsonPrimitive(3), row1[2])

        val arrayWithMap = result["arrayWithMap"]
        assertIs<JsonArray>(arrayWithMap)
        assertEquals(2, arrayWithMap.size)

        val map1 = arrayWithMap[0]
        assertIs<JsonObject>(map1)
        assertEquals(JsonPrimitive(1), map1["x"])
        assertEquals(JsonPrimitive(2), map1["y"])
    }

    @Test
    fun `buildJsonObject handles mixed Array and List`() {
        val data = mapOf(
            "array" to arrayOf(1, 2, 3),
            "list" to listOf(4, 5, 6),
            "nested" to mapOf(
                "innerArray" to arrayOf("a", "b"),
                "innerList" to listOf("c", "d")
            )
        )
        val result = JsonObjectBuilder.buildJsonObject(data)

        val array = result["array"]
        assertIs<JsonArray>(array)
        assertEquals(3, array.size)

        val list = result["list"]
        assertIs<JsonArray>(list)
        assertEquals(3, list.size)

        val nested = result["nested"]
        assertIs<JsonObject>(nested)

        val innerArray = nested["innerArray"]
        assertIs<JsonArray>(innerArray)
        assertEquals(2, innerArray.size)

        val innerList = nested["innerList"]
        assertIs<JsonArray>(innerList)
        assertEquals(2, innerList.size)
    }

    @Test
    fun `buildJsonObject handles Home Assistant light service data`() {
        val data = mapOf(
            "brightness" to 200,
            "color_name" to "red",
            "transition" to 2.5,
            "effect" to "rainbow"
        )
        val result = JsonObjectBuilder.buildJsonObject(data)

        assertEquals(JsonPrimitive(200), result["brightness"])
        assertEquals(JsonPrimitive("red"), result["color_name"])
        assertEquals(JsonPrimitive(2.5), result["transition"])
        assertEquals(JsonPrimitive("rainbow"), result["effect"])
    }

    @Test
    fun `buildJsonObject handles Home Assistant complex service data`() {
        // Example of complex service data with RGB color
        val data = mapOf(
            "brightness_pct" to 80,
            "rgb_color" to listOf(255, 128, 0),
            "flash" to "short",
            "kelvin" to 3000
        )
        val result = JsonObjectBuilder.buildJsonObject(data)

        assertEquals(JsonPrimitive(80), result["brightness_pct"])

        val rgbArray = result["rgb_color"]
        assertIs<JsonArray>(rgbArray)
        assertEquals(3, rgbArray.size)
        assertEquals(JsonPrimitive(255), rgbArray[0])
        assertEquals(JsonPrimitive(128), rgbArray[1])
        assertEquals(JsonPrimitive(0), rgbArray[2])

        assertEquals(JsonPrimitive("short"), result["flash"])
        assertEquals(JsonPrimitive(3000), result["kelvin"])
    }
}