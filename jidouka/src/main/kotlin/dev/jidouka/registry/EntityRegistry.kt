package dev.jidouka.registry

import dev.jidouka.aliases.EntityId
import dev.jidouka.common.network.utils.EntityIdParts
import dev.jidouka.components.BaseState
import dev.jidouka.components.Domain
import dev.jidouka.components.Entity
import dev.jidouka.components.GenericState
import dev.jidouka.usecases.EnsureEntitySubscribedAndCurrentUseCase
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

@Single
internal class EntityRegistry internal constructor(
    private val stateRegistry: StateRegistry,
    private val ensureSubscribedUseCase: EnsureEntitySubscribedAndCurrentUseCase,
    private val scope: CoroutineScope
) {
    private val entities = ConcurrentHashMap<EntityId, Entity<*>>()
    private val logger = KotlinLogging.logger {}

    fun get(entityId: EntityId): Entity<GenericState> {
        val parts = EntityIdParts.parse(entityId)
        val domain = Domain<GenericState>(parts.domainId)
        return get(entityId, domain)
    }

    /**
     * Returns the cached entity for [entityId], building it on the first call.
     *
     * The first call picks the parser, most specific first: the parser registered for [entityId], then the
     * registered domain's, then [domain]'s own, then [GenericState.parser]. Every lookup asks for that most specific
     * type or a supertype of it, so the cast to [S] holds whichever overload or caller asks first.
     *
     * @param entityId The entity to look up, e.g. `light.chaos_emeralds`
     * @param domain The domain [entityId] belongs to. Its parser is used only when no more specific one is registered
     * @return The cached entity, typed as [S]. If [S] is unrelated to the parser the entity was built with, such as a
     * sibling per-entity class, using its state throws [ClassCastException]
     * @throws IllegalArgumentException If [entityId]'s domain isn't [domain]
     */
    @Throws(IllegalArgumentException::class)
    fun <S : BaseState> get(
        entityId: String,
        domain: Domain<S>,
    ): Entity<S> {
        val parts = EntityIdParts.parse(entityId)
        require(parts.domainId == domain.id) {
            "Domain of entity ID ($entityId) does not match the expected domain (${domain.id})"
        }

        var wasCreated = false

        @Suppress("UNCHECKED_CAST")
        val entity = entities.computeIfAbsent(entityId) {
            wasCreated = true

            val registeredDomain = DomainParserRegistry.getDomain(domainId = domain.id) ?: domain

            val parser = DomainParserRegistry.getEntityParser(entityId)
                ?: registeredDomain.entityParser
                ?: GenericState.parser

            createTypedEntity(
                entityId = entityId,
                parser = parser as BaseState.Parser<S>,
                domain = registeredDomain
            )
        } as Entity<S>

        if (wasCreated) {
            logger.debug { "Created entity for '$entityId' with domain '${domain.id}'" }
        }

        return entity
    }

    fun getAllEntityIds(): Set<EntityId> = stateRegistry.getAllEntityIds()

    fun getEntityIdsForDomain(domainId: String): Set<EntityId> {
        val entityIdsForDomain = getAllEntityIds()
            .filterTo(mutableSetOf()) { entityId ->
                EntityIdParts.parse(entityId).domainId == domainId
            }

        return entityIdsForDomain
    }

    private fun <S : BaseState> createTypedEntity(
        entityId: String,
        parser: BaseState.Parser<S>,
        domain: Domain<*>,
    ): Entity<S> {
        val parts = EntityIdParts.parse(entityId)

        return Entity(
            objectId = parts.objectId,
            domain = domain,
            parser = parser,
            stateRegistry = stateRegistry,
            ensureSubscribedUseCase = ensureSubscribedUseCase,
            scope = scope,
        )
    }
}