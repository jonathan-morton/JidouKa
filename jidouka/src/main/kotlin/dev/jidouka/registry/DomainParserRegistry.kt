package dev.jidouka.registry

import dev.jidouka.aliases.EntityId
import dev.jidouka.components.BaseState
import dev.jidouka.components.Domain
import dev.jidouka.components.DomainParserProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.annotations.VisibleForTesting
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal object DomainParserRegistry {
    private val domainsById = ConcurrentHashMap<String, Domain<*>>()
    private val entityParsersById = ConcurrentHashMap<EntityId, BaseState.Parser<*>>()
    private val logger = KotlinLogging.logger {}

    fun getDomain(domainId: String): Domain<*>? = domainsById[domainId]

    fun getEntityParser(entityId: EntityId): BaseState.Parser<*>? = entityParsersById[entityId]

    fun installFromClasspath() {
        var providerCount = 0
        val serviceLoader = ServiceLoader.load(DomainParserProvider::class.java)
        serviceLoader.forEach { provider ->
            runCatching {
                val domains = provider.domains()
                val entityParsers = provider.entityParsers()

                domains.forEach { domain ->
                    register(domain)
                }

                entityParsers.forEach { (entityId, parser) ->
                    registerEntityParser(entityId, parser)
                }

                providerCount++

                logger.debug {
                    "Successfully loaded domains\n${domains.joinToString(separator = "\n")}\n" +
                            "and entity parsers for\n${entityParsers.keys.joinToString(separator = "\n")}"
                }
            }.onFailure {
                logger.error { "DomainParserProvider ${provider::class} failed; skipping" }
            }
        }
        logger.info {
            "Registered: ${domainsById.size} typed domain parser(s) and ${entityParsersById.size} entity parser(s) " +
                    "from $providerCount provider(s)"
        }
    }

    /**
     * Registers a domain in the domain map.
     *
     * @param domain The domain to register, keyed by its ID, e.g. `light`
     * @return True for a clean registration, false when it replaces a domain already registered with the same ID
     */
    @VisibleForTesting
    internal fun register(domain: Domain<*>): Boolean {
        val previous = domainsById.put(domain.id, domain)
        previous?.let {
            logger.error { "Domain ${domain.id} was already registered, the previous registration will be overwritten" }
        }
        return previous == null
    }

    /**
     * Registers the parser for one entity.
     *
     * @param entityId The entity the parser is for, e.g. `light.chaos_emeralds`
     * @param parser Parses that entity's states
     * @return True for a clean registration, false when it replaces a parser already registered for [entityId]
     */
    @VisibleForTesting
    internal fun registerEntityParser(
        entityId: EntityId,
        parser: BaseState.Parser<*>
    ): Boolean {
        val previousParser = entityParsersById.put(entityId, parser)
        previousParser?.let {
            logger.error { "Entity $entityId already had a parser registered, the previous registration will be overwritten" }
        }

        return previousParser == null
    }

    @VisibleForTesting
    internal fun reset() {
        domainsById.clear()
        entityParsersById.clear()
    }
}