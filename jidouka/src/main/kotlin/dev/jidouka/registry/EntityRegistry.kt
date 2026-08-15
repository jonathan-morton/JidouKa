package dev.jidouka.registry

import dev.jidouka.aliases.EntityId
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
        val parts = parseEntityId(entityId)
        val domain = Domain<GenericState>(parts.domainId)
        return get(entityId, domain)
    }

    fun <S : BaseState> get(
        entityId: String,
        domain: Domain<S>,
    ): Entity<S> {
        val parts = parseEntityId(entityId)
        require(parts.domainId == domain.id) {
            "Domain of entity ID ($entityId) does not match the expected domain (${domain.id})"
        }

        var wasCreated = false

        @Suppress("UNCHECKED_CAST")
        val entity = entities.computeIfAbsent(entityId) {
            wasCreated = true
            val parser = domain.entityParser ?: GenericState.parser as BaseState.Parser<S>
            createTypedEntity(
                entityId = entityId,
                parser = parser,
                domain = domain
            )
        } as Entity<S>

        if (wasCreated) {
            logger.debug { "Created entity for '$entityId' with domain '${domain.id}'" }
        }

        return entity
    }

    private fun parseEntityId(entityId: EntityId): EntityIdParts {
        val idParts = entityId.split(".")

        require(idParts.size == 2) {
            "Entity ID must be in format 'domain.object_id', got: '$entityId'"
        }

        return EntityIdParts(
            domainId = idParts.first(),
            objectId = idParts.last()
        )
    }

    private fun <S : BaseState> createTypedEntity(
        entityId: String,
        parser: BaseState.Parser<S>,
        domain: Domain<S>,
    ): Entity<S> {
        val parts = parseEntityId(entityId)

        return Entity(
            objectId = parts.objectId,
            domain = domain,
            parser = parser,
            stateRegistry = stateRegistry,
            ensureSubscribedUseCase = ensureSubscribedUseCase,
            scope = scope,
        )
    }

    private data class EntityIdParts(
        val domainId: String,
        val objectId: String
    )
}