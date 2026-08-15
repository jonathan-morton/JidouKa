package dev.jidouka.state

import dev.jidouka.BaseUnitTest
import dev.jidouka.components.GenericState
import dev.jidouka.network.models.hass.websocket.Context
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class CovariantPreviousStateTests : BaseUnitTest() {
    @Test
    fun `subclass previous is covariantly typed and preserves a depth of 1 chain`() {
        val oldest = TestLightState(stateRaw = "off")
        val current = TestLightState(stateRaw = "on", previous = oldest)

        val previous: TestLightState? = current.previous

        assertEquals("off", previous?.stateRaw)
        assertNull(previous?.previous, "previous.previous must be null")
    }
}

private class TestLightState(
    stateRaw: String,
    attributesRaw: Map<String, Any?> = emptyMap(),
    lastChanged: Instant = Instant.fromEpochMilliseconds(0),
    lastUpdated: Instant = Instant.fromEpochMilliseconds(0),
    lastReported: Instant? = null,
    context: Context? = null,
    previous: TestLightState? = null
) : GenericState(
    stateRaw = stateRaw,
    attributesRaw = attributesRaw,
    lastChanged = lastChanged,
    lastUpdated = lastUpdated,
    lastReported = lastReported,
    context = context,
    previous = previous
) {
    @Suppress("UNCHECKED_CAST")
    override val previous: TestLightState?
        get() = super.previous as TestLightState?
}