package dev.jidouka.common.network.models.hass.websocket

import dev.jidouka.common.network.JsonManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class StateDataTest {
    private val json = JsonManager().json

    @Test
    fun `decodes a websocket payload with last_reported and context`() {
        val state = json.decodeFromJsonElement<StateData>(
            statePayload(lastReportedInstant = lastReportedInstant, contextId = contextId)
        )

        assertEquals(entityId, state.entityId)
        assertEquals(lastReportedInstant, state.lastReported)
        assertEquals(contextId, state.context?.id)
    }

    @Test
    fun `decodes a REST payload without last_reported or context`() {
        val state = json.decodeFromJsonElement<StateData>(statePayload())

        assertNull(state.lastReported)
        assertNull(state.context)
    }

    private fun statePayload(
        lastReportedInstant: Instant? = null,
        contextId: String? = null
    ): JsonObject = buildJsonObject {
        put("entity_id", entityId)
        put("state", "on")
        put("attributes", buildJsonObject { put("brightness", 255) })
        put("last_changed", lastChangedInstant.toString())
        put("last_updated", lastChangedInstant.toString())
        lastReportedInstant?.let { put("last_reported", it.toString()) }
        contextId?.let { put("context", buildJsonObject { put("id", it) }) }
    }

    private companion object {
        private const val entityId = "light.kitchen"
        private const val contextId = "abc"
        private val lastChangedInstant = Instant.parse("2024-01-01T00:00:00Z")
        private val lastReportedInstant = Instant.parse("2024-01-01T00:00:01Z")
    }
}