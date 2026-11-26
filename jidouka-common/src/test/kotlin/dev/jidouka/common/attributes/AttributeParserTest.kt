@file:Suppress("KotlinMisorderedAssertEqualsArguments")

package dev.jidouka.common.attributes

import dev.jidouka.common.attributes.models.AttributeKind
import dev.jidouka.common.attributes.models.AttributeValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttributeParserTest {
    @Test
    fun `String reads text and whole numbers as text`() {
        assertEquals(TEXT, AttributeParser.String.parse(TEXT))
        assertEquals(WHOLE.toString(), AttributeParser.String.parse(WHOLE))
        assertEquals(PAST_INT_MAX.toString(), AttributeParser.String.parse(PAST_INT_MAX))
        assertNull(AttributeParser.String.parse(true))
    }

    @Test
    fun `Int reads whole numbers that fit`() {
        assertEquals(WHOLE, AttributeParser.Int.parse(WHOLE))
        assertEquals(WHOLE, AttributeParser.Int.parse(WHOLE.toDouble()))
        assertEquals(WHOLE, AttributeParser.Int.parse(WHOLE.toLong()))
        assertNull(AttributeParser.Int.parse(FRACTION))
        assertNull(AttributeParser.Int.parse(PAST_INT_MAX))
        assertNull(AttributeParser.Int.parse(WHOLE.toString()))
    }

    @Test
    fun `Long reads whole numbers`() {
        assertEquals(WHOLE.toLong(), AttributeParser.Long.parse(WHOLE))
        assertEquals(PAST_INT_MAX, AttributeParser.Long.parse(PAST_INT_MAX))
        assertEquals(WHOLE.toLong(), AttributeParser.Long.parse(WHOLE.toDouble()))
        assertNull(AttributeParser.Long.parse(FRACTION))
        assertNull(AttributeParser.Long.parse(PAST_LONG_MAX))
    }

    @Test
    fun `Double reads any number`() {
        assertEquals(WHOLE.toDouble(), AttributeParser.Double.parse(WHOLE))
        assertEquals(FRACTION, AttributeParser.Double.parse(FRACTION))
        assertNull(AttributeParser.Double.parse(FRACTION.toString()))
    }

    @Test
    fun `Boolean reads only booleans`() {
        assertEquals(true, AttributeParser.Boolean.parse(true))
        assertNull(AttributeParser.Boolean.parse(true.toString()))
    }

    @Test
    fun `list reads every element or returns null`() {
        val ints = AttributeParser.list(AttributeParser.Int)

        assertEquals(listOf(WHOLE, OTHER_WHOLE), ints.parse(listOf(WHOLE, OTHER_WHOLE)))
        assertEquals(emptyList<Int>(), ints.parse(emptyList<Int>()))
        assertNull(ints.parse(listOf(WHOLE, null, OTHER_WHOLE)))
        assertNull(ints.parse(listOf(WHOLE, TEXT, OTHER_WHOLE)))
        assertNull(AttributeParser.list(AttributeParser.String).parse(listOf(TEXT, UNKNOWN)))
        assertNull(ints.parse(WHOLE))
    }

    @Test
    fun `Any reads every kind except objects`() {
        assertEquals(AttributeValue.Boolean(false), AttributeParser.Any.parse(false))
        assertEquals(AttributeValue.Number(WHOLE.toDouble()), AttributeParser.Any.parse(WHOLE))
        assertEquals(AttributeValue.String(TEXT), AttributeParser.Any.parse(TEXT))
        assertEquals(
            AttributeValue.ListOf(listOf(AttributeValue.Number(WHOLE.toDouble()), null, AttributeValue.String(TEXT))),
            AttributeParser.Any.parse(listOf(WHOLE, null, TEXT)),
        )
        assertNull(AttributeParser.Any.parse(mapOf(TEXT to WHOLE)))
    }

    @Test
    fun `placeholders are unknown, unavailable, empty and blank`() {
        assertTrue(AttributeParser.isPlaceholder(UNKNOWN))
        assertTrue(AttributeParser.isPlaceholder(UNAVAILABLE_CAPITALIZED))
        assertTrue(AttributeParser.isPlaceholder(EMPTY_TEXT))
        assertTrue(AttributeParser.isPlaceholder(BLANK_TEXT))
        assertFalse(AttributeParser.isPlaceholder(TEXT))
        assertFalse(AttributeParser.isPlaceholder(WHOLE))
    }

    @Test
    fun `forKind returns the built-in parser for each kind`() {
        assertEquals(AttributeParser.Int, AttributeParser.forKind(AttributeKind.Int))
        assertEquals(AttributeParser.Any, AttributeParser.forKind(AttributeKind.Any))
        assertEquals(
            listOf(WHOLE.toDouble(), OTHER_WHOLE.toDouble()),
            AttributeParser.forKind(AttributeKind.ListOf(AttributeKind.Double)).parse(listOf(WHOLE, OTHER_WHOLE)),
        )
    }

    @Test
    fun `parsers built separately share a name and different kinds don't`() {
        fun custom() = AttributeParser { it as? String }

        assertEquals(AttributeParser.nameOf(custom()), AttributeParser.nameOf(custom()))
        assertEquals(
            AttributeParser.nameOf(AttributeParser.list(custom())),
            AttributeParser.nameOf(AttributeParser.list(custom()))
        )
        assertNotEquals(AttributeParser.nameOf(AttributeParser.Int), AttributeParser.nameOf(AttributeParser.Long))
        assertNotEquals(
            AttributeParser.nameOf(AttributeParser.Int),
            AttributeParser.nameOf(AttributeParser.list(AttributeParser.Int))
        )
    }

    private companion object {
        private const val TEXT = "on"
        private const val WHOLE = 2
        private const val OTHER_WHOLE = 3
        private const val FRACTION = 1.5
        private const val PAST_INT_MAX = Int.MAX_VALUE + 1L

        /** `Long.MAX_VALUE` rounds up to 2^63 as a double, one past the largest `Long`. */
        private const val PAST_LONG_MAX = Long.MAX_VALUE.toDouble()

        private const val UNKNOWN = "unknown"
        private const val UNAVAILABLE_CAPITALIZED = "Unavailable"
        private const val EMPTY_TEXT = ""
        private const val BLANK_TEXT = "   "
    }
}
