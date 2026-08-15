package dev.jidouka.automations.dsl.providers

import dev.jidouka.aliases.EntityId
import dev.jidouka.components.BaseState
import dev.jidouka.components.Domain
import dev.jidouka.components.Entity
import dev.jidouka.components.GenericState
import dev.jidouka.registry.EntityRegistry

public interface EntityProvider {
    public fun entity(entityId: EntityId): Entity<GenericState>
    public fun <S : BaseState> entity(
        entityId: String,
        domain: Domain<S>,
    ): Entity<S>
}

internal class RegistryEntityProvider(
    private val entityRegistry: EntityRegistry
) : EntityProvider {

    override fun entity(
        entityId: EntityId
    ): Entity<GenericState> = entityRegistry.get(entityId)

    override fun <S : BaseState> entity(
        entityId: String,
        domain: Domain<S>
    ): Entity<S> = entityRegistry.get(entityId, domain)
}