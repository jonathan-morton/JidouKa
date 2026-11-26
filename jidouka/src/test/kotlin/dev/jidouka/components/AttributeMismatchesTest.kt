package dev.jidouka.components

import dev.jidouka.common.attributes.AttributeParser
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttributeMismatchesTest {
    @Test
    fun `each entity, key and kind reports once`() {
        val mismatches = AttributeMismatches()

        assertTrue(mismatches.isFirstReport(ENTITY_ID, KEY, INT_KIND))
        assertFalse(mismatches.isFirstReport(ENTITY_ID, KEY, INT_KIND))
        assertTrue(mismatches.isFirstReport(ENTITY_ID, KEY, DOUBLE_KIND))
        assertTrue(mismatches.isFirstReport(ENTITY_ID, OTHER_KEY, INT_KIND))
        assertTrue(mismatches.isFirstReport(OTHER_ENTITY_ID, KEY, INT_KIND))
    }

    private companion object {
        private const val ENTITY_ID = "light.desk_zone"
        private const val OTHER_ENTITY_ID = "light.reading_lamp"
        private const val KEY = "brightness"
        private const val OTHER_KEY = "color_temp"
        private val INT_KIND = AttributeParser.nameOf(AttributeParser.Int)
        private val DOUBLE_KIND = AttributeParser.nameOf(AttributeParser.Double)
    }
}
