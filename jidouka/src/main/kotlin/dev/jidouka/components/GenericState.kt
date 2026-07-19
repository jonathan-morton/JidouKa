package dev.jidouka.components

import kotlin.time.Instant

/**
 * Generic state implementation that can represent any Home Assistant entity state.
 */
public class GenericState(
    override val stateRaw: String,
    override val attributesRaw: Map<String, Any?>,
    override val lastChanged: Instant,
    override val lastUpdated: Instant,
    override val lastReported: Instant?
) : BaseState<GenericState>() {
    @Deprecated("use stateRaw", replaceWith = ReplaceWith("stateRaw"))
    public val state: String
        get() = stateRaw

    public companion object {
        public val parser: Parser<GenericState> = object : Parser<GenericState> {
            override fun parse(stateObject: StateObject): GenericState? {
                if (stateObject.isUnavailable()) {
                    return null
                }

                return GenericState(
                    stateRaw = stateObject.state,
                    attributesRaw = stateObject.attributesRaw,
                    lastChanged = stateObject.lastChanged,
                    lastUpdated = stateObject.lastUpdated,
                    lastReported = stateObject.lastReported
                )
            }
        }
    }
}
