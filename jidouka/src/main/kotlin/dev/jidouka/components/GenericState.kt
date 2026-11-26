package dev.jidouka.components

import dev.jidouka.aliases.EntityId
import dev.jidouka.common.network.models.hass.websocket.Context
import kotlin.time.Instant

/**
 * Generic state implementation that can represent any Home Assistant entity state.
 */
public open class GenericState(
    override val entityId: EntityId,
    override val stateRaw: String,
    override val attributesRaw: Map<String, Any?>,
    override val lastChanged: Instant,
    override val lastUpdated: Instant,
    override val lastReported: Instant?,
    override val context: Context?,
    override val previous: BaseState? = null,
) : BaseState() {

    public companion object {
        public val parser: Parser<GenericState> = object : Parser<GenericState> {
            override fun parse(stateObject: StateObject, previous: GenericState?): GenericState? {
                if (stateObject.isUnavailable()) {
                    return null
                }

                return GenericState(
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
