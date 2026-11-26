package dev.jidouka.common.network.models.hass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaceholderStateTest {
    @Test
    fun `of reads Home Assistant's placeholders, ignoring case`() {
        assertEquals(PlaceholderState.Unavailable, PlaceholderState.of(UNAVAILABLE))
        assertEquals(PlaceholderState.Unknown, PlaceholderState.of(UNKNOWN_CAPITALIZED))
        assertNull(PlaceholderState.of(REAL_STATE))
        assertNull(PlaceholderState.of(EMPTY_TEXT))
    }

    private companion object {
        private const val UNAVAILABLE = "unavailable"
        private const val UNKNOWN_CAPITALIZED = "Unknown"
        private const val REAL_STATE = "on"
        private const val EMPTY_TEXT = ""
    }
}
