package dev.jidouka.components

import dev.jidouka.aliases.EntityId
import dev.jidouka.common.network.models.hass.websocket.Context
import kotlin.time.Instant

open class TestState(
    entityId: String = "$TEST_DOMAIN.entity",
    stateRaw: String,
    attributesRaw: Map<String, Any?> = emptyMap(),
    lastChanged: Instant = Instant.fromEpochMilliseconds(0),
    lastUpdated: Instant = Instant.fromEpochMilliseconds(0),
    lastReported: Instant? = null,
    context: Context? = null,
    previous: TestState? = null
) : GenericState(
    entityId = entityId,
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

    /** A per-entity state class, shaped like a generated one such as `LightState.ChaosEmeralds`. */
    class Zone(
        entityId: String = ZONE_ENTITY_ID,
        stateRaw: String,
        attributesRaw: Map<String, Any?> = emptyMap(),
        lastChanged: Instant = Instant.fromEpochMilliseconds(0),
        lastUpdated: Instant = Instant.fromEpochMilliseconds(0),
        lastReported: Instant? = null,
        context: Context? = null,
        previous: Zone? = null
    ) : TestState(
        entityId = entityId,
        stateRaw = stateRaw,
        attributesRaw = attributesRaw,
        lastChanged = lastChanged,
        lastUpdated = lastUpdated,
        lastReported = lastReported,
        context = context,
        previous = previous
    ) {
        override val previous: Zone?
            get() = super.previous as Zone?

        companion object {
            val parser: Parser<Zone> = object : Parser<Zone> {
                override fun parse(stateObject: StateObject, previous: Zone?): Zone? {
                    if (stateObject.isUnavailable()) {
                        return null
                    }

                    return Zone(
                        entityId = stateObject.entityId,
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
        }
    }

    companion object {
        val parser: Parser<TestState> = object : Parser<TestState> {
            override fun parse(stateObject: StateObject, previous: TestState?): TestState? {
                if (stateObject.isUnavailable()) {
                    return null
                }

                return TestState(
                    entityId = stateObject.entityId,
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
        const val ZONE_ENTITY_ID = "$TEST_DOMAIN.zone"
    }
}

class TestDomainParserProvider : DomainParserProvider {
    override fun domains(): List<Domain<*>> = listOf(Domain(TestState.TEST_DOMAIN, TestState.parser))

    override fun entityParsers(): Map<EntityId, BaseState.Parser<*>> =
        mapOf(TestState.ZONE_ENTITY_ID to TestState.Zone.parser)
}
