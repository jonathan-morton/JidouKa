package dev.jidouka.state

import dev.jidouka.components.Domain
import dev.jidouka.components.DomainParserProvider
import dev.jidouka.components.GenericState
import dev.jidouka.components.StateObject
import dev.jidouka.network.models.hass.websocket.Context
import kotlin.time.Instant

class TestState(
    stateRaw: String,
    attributesRaw: Map<String, Any?> = emptyMap(),
    lastChanged: Instant = Instant.fromEpochMilliseconds(0),
    lastUpdated: Instant = Instant.fromEpochMilliseconds(0),
    lastReported: Instant? = null,
    context: Context? = null,
    previous: TestState? = null
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
    override val previous: TestState?
        get() = super.previous as TestState?

    companion object {
        val parser: Parser<TestState> = object : Parser<TestState> {
            override fun parse(stateObject: StateObject, previous: TestState?): TestState? {
                if (stateObject.isUnavailable()) {
                    return null
                }

                return TestState(
                    stateRaw = stateObject.state,
                    attributesRaw = stateObject.attributesRaw,
                    lastChanged = stateObject.lastChanged,
                    lastUpdated = stateObject.lastUpdated,
                    lastReported = stateObject.lastReported,
                    context = stateObject.context,
                    previous = previous

                )
            }
        }

        const val TEST_DOMAIN = "test"
    }
}

class TestDomainParserProvider : DomainParserProvider {
    override fun domains(): List<Domain<*>> = listOf(Domain(TestState.TEST_DOMAIN, TestState.parser))
}