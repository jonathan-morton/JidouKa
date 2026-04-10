package dev.jidouka.network.utils

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class JsonExtensionsTest {

    @Test
    fun `toNativeValue converts JsonPrimitive string correctly`() {
        val json = JsonPrimitive("hello")
        val result = json.toNativeValue()

        assertEquals("hello", result)
        assertIs<String>(result)
    }

    @Test
    fun `toNativeValue converts JsonPrimitive boolean correctly`() {
        val jsonTrue = JsonPrimitive(true)
        val jsonFalse = JsonPrimitive(false)

        assertEquals(true, jsonTrue.toNativeValue())
        assertEquals(false, jsonFalse.toNativeValue())
        assertIs<Boolean>(jsonTrue.toNativeValue())
    }

    @Test
    fun `toNativeValue converts JsonPrimitive integer correctly`() {
        val json = JsonPrimitive(42)
        val result = json.toNativeValue()

        assertEquals(42, result)
        assertIs<Int>(result)
    }

    @Test
    fun `toNativeValue converts JsonPrimitive long correctly`() {
        val largeNumber = 9223372036854775807L // Max Long value
        val json = JsonPrimitive(largeNumber)
        val result = json.toNativeValue()

        assertEquals(largeNumber, result)
        assertIs<Long>(result)
    }

    @Test
    fun `toNativeValue converts JsonPrimitive double correctly`() {
        val json = JsonPrimitive(3.14159)
        val result = json.toNativeValue()

        assertEquals(3.14159, result)
        assertIs<Double>(result)
    }

    @Test
    fun `toNativeValue converts JsonNull to null`() {
        val json = JsonNull
        val result = json.toNativeValue()

        assertNull(result)
    }

    @Test
    fun `toNativeValue converts JsonArray correctly`() {
        val json = buildJsonArray {
            add("string")
            add(42)
            add(true)
            add(3.14)
            add(JsonNull)
        }

        val result = json.toNativeValue()
        assertIs<List<*>>(result)

        assertEquals(5, result.size)
        assertEquals("string", result[0])
        assertEquals(42, result[1])
        assertEquals(true, result[2])
        assertEquals(3.14, result[3])
        assertNull(result[4])
    }

    @Test
    fun `toNativeValue converts nested JsonArray correctly`() {
        val json = buildJsonArray {
            add("outer")
            addJsonArray {
                add("inner1")
                add("inner2")
            }
            add(42)
        }

        val result = json.toNativeValue() as List<*>

        assertEquals(3, result.size)
        assertEquals("outer", result[0])

        val innerList = result[1] as List<*>
        assertEquals(2, innerList.size)
        assertEquals("inner1", innerList[0])
        assertEquals("inner2", innerList[1])

        assertEquals(42, result[2])
    }

    @Test
    fun `toNativeMap converts simple JsonObject correctly`() {
        val json = buildJsonObject {
            put("name", "John")
            put("age", 30)
            put("active", true)
            put("balance", 123.45)
            put("optional", JsonNull)
        }

        val result = json.toNativeMap()

        assertEquals("John", result["name"])
        assertEquals(30, result["age"])
        assertEquals(true, result["active"])
        assertEquals(123.45, result["balance"])
        assertNull(result["optional"])
    }

    @Test
    fun `toNativeMap converts nested JsonObject correctly`() {
        val json = buildJsonObject {
            put("user", buildJsonObject {
                put("name", "Alice")
                put("id", 1)
            })
            put("metadata", buildJsonObject {
                put("created", "2024-01-01")
                put("version", 2)
            })
        }

        val result = json.toNativeMap()

        val user = result["user"] as Map<String, Any?>
        assertEquals("Alice", user["name"])
        assertEquals(1, user["id"])

        val metadata = result["metadata"] as Map<String, Any?>
        assertEquals("2024-01-01", metadata["created"])
        assertEquals(2, metadata["version"])
    }

    @Test
    fun `toNativeMap handles complex nested structures`() {
        val json = buildJsonObject {
            put("temperature", 72.5)
            put("unit", "fahrenheit")
            put("forecast", buildJsonArray {
                addJsonObject {
                    put("day", "Monday")
                    put("high", 75)
                    put("low", 60)
                }
                addJsonObject {
                    put("day", "Tuesday")
                    put("high", 78)
                    put("low", 62)
                }
            })
            put("location", buildJsonObject {
                put("city", "New York")
                put("coordinates", buildJsonArray {
                    add(40.7128)
                    add(-74.0060)
                })
            })
        }

        val result = json.toNativeMap()

        assertEquals(72.5, result["temperature"])
        assertEquals("fahrenheit", result["unit"])

        val forecast = result["forecast"] as List<*>
        assertEquals(2, forecast.size)

        val monday = forecast[0] as Map<String, Any?>
        assertEquals("Monday", monday["day"])
        assertEquals(75, monday["high"])
        assertEquals(60, monday["low"])

        val location = result["location"] as Map<String, Any?>
        assertEquals("New York", location["city"])

        val coordinates = location["coordinates"] as List<*>
        assertEquals(2, coordinates.size)
        assertEquals(40.7128, coordinates[0])
        assertEquals(-74.0060, coordinates[1])
    }

    @Test
    fun `toNativeMap preserves empty structures`() {
        val json = buildJsonObject {
            put("emptyObject", buildJsonObject {})
            put("emptyArray", buildJsonArray {})
            put("emptyString", "")
            put("zero", 0)
            put("false", false)
        }

        val result = json.toNativeMap()

        val emptyObject = result["emptyObject"] as Map<String, Any?>
        assertEquals(0, emptyObject.size)

        val emptyArray = result["emptyArray"] as List<*>
        assertEquals(0, emptyArray.size)

        assertEquals("", result["emptyString"])
        assertEquals(0, result["zero"])
        assertEquals(false, result["false"])
    }

    @Test
    fun `toNativeValue handles numeric edge cases`() {
        val smallInt = JsonPrimitive(100)
        assertIs<Int>(smallInt.toNativeValue())

        val largeInt = JsonPrimitive(2147483648L) // Just above Int.MAX_VALUE
        assertIs<Long>(largeInt.toNativeValue())

        val decimal = JsonPrimitive(1.0)
        assertIs<Double>(decimal.toNativeValue())

        val negative = JsonPrimitive(-42)
        assertEquals(-42, negative.toNativeValue())
        assertIs<Int>(negative.toNativeValue())
    }

    @Test
    fun `real world example - weather entity attributes`() {
        val json = buildJsonObject {
            put("temperature", 72)
            put("temperature_unit", "°F")
            put("humidity", 65)
            put("pressure", 29.92)
            put("pressure_unit", "inHg")
            put("wind_bearing", 180)
            put("wind_speed", 5.5)
            put("wind_speed_unit", "mph")
            put("visibility", 10)
            put("visibility_unit", "mi")
            put("precipitation_unit", "in")
            put("forecast", buildJsonArray {
                addJsonObject {
                    put("condition", "sunny")
                    put("temperature", 75)
                    put("templow", 60)
                    put("datetime", "2024-01-01T12:00:00")
                    put("wind_bearing", 180)
                    put("wind_speed", 5)
                    put("precipitation", JsonNull)
                    put("precipitation_probability", 0)
                }
            })
            put("attribution", "Data provided by National Weather Service")
            put("friendly_name", "Weather Home")
        }

        val result = json.toNativeMap()

        val temperature = result["temperature"] as? Number
        assertEquals(72, temperature?.toInt())

        val humidity = result["humidity"] as? Number
        assertEquals(65, humidity?.toInt())

        val pressure = result["pressure"] as? Number
        assertEquals(29.92, pressure?.toDouble())

        val forecast = result["forecast"] as? List<*>
        val firstDay = forecast?.firstOrNull() as? Map<String, Any?>
        assertEquals("sunny", firstDay?.get("condition"))
        assertNull(firstDay?.get("precipitation"))
    }
}