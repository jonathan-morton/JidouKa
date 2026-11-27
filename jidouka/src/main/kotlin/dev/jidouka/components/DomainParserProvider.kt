package dev.jidouka.components

import dev.jidouka.aliases.EntityId

public interface DomainParserProvider {
    public fun domains(): List<Domain<*>>

    /**
     * Parsers for single entities whose state class is more specific than their domain's, such as a generated
     * `LightState.ChaosEmeralds`.
     *
     * An entity listed here is always parsed with its own parser, whichever [Domain] it's first asked for with.
     *
     * @return Each parser keyed by the ID of the entity it parses, e.g. `light.chaos_emeralds`. Empty by default
     */
    public fun entityParsers(): Map<EntityId, BaseState.Parser<*>> = emptyMap()
}