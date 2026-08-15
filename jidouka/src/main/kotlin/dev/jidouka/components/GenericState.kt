package dev.jidouka.components

import dev.jidouka.network.models.hass.websocket.Context
import kotlin.time.Instant

/**
 * Generic state implementation that can represent any Home Assistant entity state.
 */
public open class GenericState(
    override val stateRaw: String,
    override val attributesRaw: Map<String, Any?>,
    override val lastChanged: Instant,
    override val lastUpdated: Instant,
    override val lastReported: Instant?,
    override val context: Context?,
    override val previous: BaseState? = null,
) : BaseState() {
    @Deprecated("use stateRaw", replaceWith = ReplaceWith("stateRaw"))
    public val state: String
        get() = stateRaw

    public companion object {
        public val parser: Parser<GenericState> = object : Parser<GenericState> {
            override fun parse(stateObject: StateObject, previous: GenericState?): GenericState? {
                if (stateObject.isUnavailable()) {
                    return null
                }

                return GenericState(
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
