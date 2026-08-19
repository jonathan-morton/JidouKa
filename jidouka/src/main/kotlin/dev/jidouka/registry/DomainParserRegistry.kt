package dev.jidouka.registry

import dev.jidouka.components.Domain
import dev.jidouka.components.DomainParserProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.annotations.VisibleForTesting
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal object DomainParserRegistry {
    private val domainsById = ConcurrentHashMap<String, Domain<*>>()
    private val logger = KotlinLogging.logger {}

    fun getDomain(domainId: String): Domain<*>? = domainsById[domainId]

    fun installFromClasspath() {
        var providerCount = 0
        val serviceLoader = ServiceLoader.load(DomainParserProvider::class.java)
        serviceLoader.forEach { provider ->
            runCatching { provider.domains() }
                .onSuccess { domains ->
                    providerCount++
                    domains.forEach { register(it) }
                    logger.debug { "Successfully loaded domains\n${domains.joinToString(separator = "\n")}" }
                }
                .onFailure {
                    logger.error { "DomainParserProvider ${provider::class} failed; skipping" }
                }
        }
        logger.info { "Registered: ${domainsById.size} typed domain parser(s) from $providerCount provider(s)" }
    }

    @VisibleForTesting
    /**
     * Registers a domain in the domain map
     * @return returns true if it is a clean registration, false if we are reregistering an already added domain
     */
    internal fun register(domain: Domain<*>): Boolean {
        val previous = domainsById.put(domain.id, domain)
        previous?.let {
            logger.error { "Domain ${domain.id} was already registered, the previous registration will be overwritten" }
        }
        return previous == null
    }

    @VisibleForTesting
    internal fun reset() {
        domainsById.clear()
    }
}