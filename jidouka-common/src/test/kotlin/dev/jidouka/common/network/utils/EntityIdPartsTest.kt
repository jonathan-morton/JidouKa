package dev.jidouka.common.network.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EntityIdPartsTest {
    @Test
    fun `parses a valid entity id`() {
        val parts = EntityIdParts.parse("light.kitchen")

        assertEquals("light", parts.domainId)
        assertEquals("kitchen", parts.objectId)
    }

    @Test
    fun `throws on malformed entity id`() {
        assertFailsWith<IllegalArgumentException> {
            EntityIdParts.parse("not-a-valid-entity-id")
        }
    }
}