package dev.jidouka.automations.dsl.providers

import dev.jidouka.aliases.EntityId
import dev.jidouka.components.BaseState
import dev.jidouka.components.Domain
import dev.jidouka.components.Entity
import dev.jidouka.components.GenericState
import dev.jidouka.registry.EntityRegistry

public interface EntityProvider {
    public val all: List<Entity<GenericState>>

    public fun entity(entityId: EntityId): Entity<GenericState>
    public fun <S : BaseState> entity(
        entityId: String,
        domain: Domain<S>,
    ): Entity<S>

    public fun <S : BaseState> byDomain(domain: Domain<S>): List<Entity<S>>
}

internal class RegistryEntityProvider(
    private val entityRegistry: EntityRegistry
) : EntityProvider {
    override val all: List<Entity<GenericState>>
        get() = entityRegistry
            .getAllEntityIds()
            .map { entity(it) }

    override fun entity(
        entityId: EntityId
    ): Entity<GenericState> = entityRegistry.get(entityId)

    override fun <S : BaseState> entity(
        entityId: String,
        domain: Domain<S>
    ): Entity<S> = entityRegistry.get(entityId, domain)

    override fun <S : BaseState> byDomain(domain: Domain<S>): List<Entity<S>> {
        return entityRegistry.getEntityIdsForDomain(domainId = domain.id)
            .map { entity(it, domain) }
    }
}