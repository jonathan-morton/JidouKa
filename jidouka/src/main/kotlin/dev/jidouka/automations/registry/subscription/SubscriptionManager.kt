package dev.jidouka.automations.registry.subscription

import dev.jidouka.aliases.EntityId
import dev.jidouka.aliases.SubscriptionId
import dev.jidouka.aliases.WebhookId
import dev.jidouka.automations.dsl.triggers.TriggerMetadata
import dev.jidouka.automations.dsl.triggers.WebhookTriggerConfiguration
import dev.jidouka.automations.registry.TriggerKey
import dev.jidouka.client.ConnectionManager
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

internal typealias AutomationId = String

internal interface SubscriptionManager {
    suspend fun subscribe(
        metadata: TriggerMetadata,
        automationId: String
    )

    suspend fun unsubscribe(
        metadata: TriggerMetadata,
        automationId: AutomationId,
    )

    suspend fun resubscribeAll()

    fun hasActiveSubscription(entityId: EntityId): Boolean
    fun getAutomations(key: TriggerKey): Set<AutomationId>
    fun getAllKeys(): Set<TriggerKey>
    fun getStatistics(): SubscriptionStatistics

    companion object {
        const val UNTRACKED_SUBSCRIPTION_ID = "__read__"
    }
}

@Single
internal class WebSocketSubscriptionManager(
    private val connectionManager: ConnectionManager
) : SubscriptionManager {
    private val automationIdsByTriggerKey = ConcurrentHashMap<TriggerKey, MutableSet<AutomationId>>()
    private val subscriptionIds = ConcurrentHashMap<TriggerKey, SubscriptionId>()
    private val webhookConfigurations = ConcurrentHashMap<WebhookId, WebhookSubscriptionConfiguration>()

    private val mutex = Mutex()
    private val websocketRequestsSemaphore = Semaphore(permits = MAX_CONCURRENT_SUBSCRIBE_REQUESTS)
    private val logger = KotlinLogging.logger {}

    override suspend fun subscribe(
        metadata: TriggerMetadata,
        automationId: String
    ) = mutex.withLock {
        validateWebhookConfigurations(metadata)

        val keys: List<TriggerKey> = TriggerKey.getKeys(metadata)
        logger.debug { "Subscribing automation '$automationId' to ${keys.size} trigger key(s)" }

        val newKeys = mutableListOf<TriggerKey>()

        keys.forEach { key ->
            val automationsSet: MutableSet<AutomationId> = automationIdsByTriggerKey.computeIfAbsent(key) {
                ConcurrentHashMap.newKeySet()
            }

            val needsSubscription = subscriptionIds.containsKey(key).not()
            automationsSet.add(automationId)

            if (needsSubscription) {
                newKeys.add(key)
            } else {
                logger.debug { "Added automation '$automationId' to existing subscription for key '$key' (${automationsSet.size} automation(s) total)" }
            }
        }

        if (newKeys.isEmpty()) {
            return@withLock
        }

        logger.info { "Creating ${newKeys.size} new subscription(s) for automation '$automationId'" }

        val results: List<Pair<TriggerKey, Result<SubscriptionId>>> = subscribeToKeys(newKeys)

        results.forEach { (key, result) ->
            result
                .onSuccess { subscriptionId ->
                    subscriptionIds[key] = subscriptionId
                    logger.debug { "Subscribed to ${describeTriggerKey(key)} with subscription ID: $subscriptionId" }
                }
                .onFailure { exception ->
                    logger.error(exception) { "Failed to subscribe to key '$key' for automation '$automationId'" }
                    automationIdsByTriggerKey[key]?.remove(automationId)
                }
        }
    }

    @Throws(IllegalStateException::class)
    private fun validateWebhookConfigurations(metadata: TriggerMetadata) {
        if (metadata !is TriggerMetadata.WebhookTrigger) return

        metadata.webhookConfigurations.forEach { (webhookId, configuration) ->
            val existingConfiguration = webhookConfigurations[webhookId]

            existingConfiguration?.let {
                WebhookTriggerConfiguration.requireWebhookConfigurationMatches(
                    webhookId = webhookId,
                    existingAllowedMethods = existingConfiguration.allowedMethods,
                    incomingAllowedMethods = configuration.allowedMethods,
                    existingIsLocalOnly = existingConfiguration.isLocalOnly,
                    incomingIsLocalOnly = configuration.isLocalOnly

                )
            } ?: run {
                webhookConfigurations[webhookId] = WebhookSubscriptionConfiguration(
                    configuration.allowedMethods,
                    configuration.isLocalOnly
                )
            }
        }
    }

    override suspend fun unsubscribe(
        metadata: TriggerMetadata,
        automationId: AutomationId,
    ) = mutex.withLock {
        val keys: List<TriggerKey> = TriggerKey.getKeys(metadata)
        logger.debug { "Unsubscribing automation '$automationId' from ${keys.size} trigger key(s)" }

        val keysToUnsubscribe = mutableListOf<Pair<TriggerKey, SubscriptionId>>()


        keys.forEach { key ->
            val automationIds = automationIdsByTriggerKey[key] ?: return@forEach
            automationIds.remove(automationId)
            logger.debug { "Removed automation '$automationId' from subscription for key '$key' (${automationIds.size} automation(s) remaining)" }

            if (automationIds.isEmpty()) {
                val subscriptionId = subscriptionIds[key] ?: return@forEach
                keysToUnsubscribe.add(key to subscriptionId)
            }

        }

        if (keysToUnsubscribe.isEmpty()) {
            return@withLock
        }

        logger.info { "Removing ${keysToUnsubscribe.size} subscription(s) with no automations remaining" }

        val results = unsubscribeToKeys(keysToUnsubscribe)

        results.forEach { (key, result) ->
            result
                .onSuccess {
                    automationIdsByTriggerKey.remove(key)

                    if (key is TriggerKey.Webhook) {
                        webhookConfigurations.remove(key.webhookId)
                    }

                    logger.debug { "Removed subscription to ${describeTriggerKey(key)}" }

                    subscriptionIds.remove(key)
                }
                .onFailure { exception ->
                    logger.error(exception) { "Failed to unsubscribe from $key" }
                }
        }
    }

    /**
     * Used after a disconnection to resubscribe
     */
    override suspend fun resubscribeAll() = mutex.withLock {
        logger.info {
            "Re-establishing ${automationIdsByTriggerKey.size} subscriptions after reconnection"
        }

        val oldSubscriptionCount = subscriptionIds.size
        subscriptionIds.clear()
        logger.debug { "Cleared $oldSubscriptionCount previous subscriptions" }

        var successCount = 0
        var failureCount = 0

        val keysToResubscribe: List<TriggerKey> = automationIdsByTriggerKey
            .filterValues { it.isNotEmpty() }
            .keys
            .toList()

        val results: List<Pair<TriggerKey, Result<SubscriptionId>>> = subscribeToKeys(keysToResubscribe)

        results.forEach { (key, result) ->
            result
                .onSuccess { newSubscriptionId ->
                    subscriptionIds[key] = newSubscriptionId
                    successCount++
                    logger.debug { "Resubscribed to ${describeTriggerKey(key)}. New subscription ID: $newSubscriptionId" }
                }
                .onFailure { exception ->
                    failureCount++
                    logger.error(exception) { "Failed to resubscribe to key: $key" }
                }
        }

        if (failureCount > 0) {
            logger.warn {
                """
                Subscription restoration: $successCount succeeded, $failureCount failed 
                (${automationIdsByTriggerKey.size} total, ${subscriptionIds.size} now active)    
                """.trimIndent()
            }
        } else {
            logger.info { "All subscriptions restored successfully (${automationIdsByTriggerKey.size} total, ${subscriptionIds.size} now active)" }
        }
    }

    private suspend fun subscribeToKeys(
        keys: List<TriggerKey>
    ): List<Pair<TriggerKey, Result<SubscriptionId>>> = coroutineScope {
        keys.map { key ->
            async {
                key to websocketRequestsSemaphore.withPermit {
                    runCatching { subscribeToClient(key) }
                }
            }
        }.awaitAll()
    }

    private suspend fun unsubscribeToKeys(
        keysWithSubscriptionIds: List<Pair<TriggerKey, SubscriptionId>>
    ): List<Pair<TriggerKey, Result<Unit>>> = coroutineScope {
        keysWithSubscriptionIds.map { (key, subscriptionId) ->
            async {
                key to websocketRequestsSemaphore.withPermit {
                    runCatching { unsubscribeFromClient(subscriptionId) }
                }
            }
        }.awaitAll()
    }

    private suspend fun subscribeToClient(key: TriggerKey): SubscriptionId {
        val subscriptionId = when (key) {
            is TriggerKey.Entity -> {
                connectionManager.subscribeToEntity(key.entityId)
            }

            is TriggerKey.Event -> {
                connectionManager.subscribeToEvent(key.eventType)
            }

            is TriggerKey.Webhook -> {
                val configuration = webhookConfigurations[key.webhookId]
                    ?: error("No webhook configuration found: ${key.webhookId}")

                connectionManager.subscribeToWebhook(
                    webhookId = key.webhookId,
                    allowedMethods = configuration.allowedMethods,
                    isLocalOnly = configuration.isLocalOnly
                )
            }
        }

        logger.debug { "Subscribed to client for key '$key' with subscription ID: $subscriptionId" }
        return subscriptionId
    }

    private suspend fun unsubscribeFromClient(
        subscriptionId: SubscriptionId
    ) {
        logger.debug { "Unsubscribing from client with subscription ID: $subscriptionId" }
        connectionManager.unsubscribe(subscriptionId)
    }

    private fun describeTriggerKey(key: TriggerKey): String = when (key) {
        is TriggerKey.Entity -> "entity '${key.entityId}'"
        is TriggerKey.Event -> "event type '${key.eventType}'"
        is TriggerKey.Webhook -> "webhook '${key.webhookId}'"
    }

    override fun hasActiveSubscription(entityId: EntityId): Boolean {
        return subscriptionIds.containsKey(TriggerKey.Entity(entityId))
    }

    /**
     * Get all automation IDs that care about a key.
     * Useful for observability and debugging.
     */
    override fun getAutomations(key: TriggerKey): Set<AutomationId> {
        return automationIdsByTriggerKey[key]?.toSet() ?: emptySet()
    }

    /**
     * Get all TriggerKeys.
     */
    override fun getAllKeys(): Set<TriggerKey> {
        return automationIdsByTriggerKey.keys.toSet()
    }

    /**
     * Get subscription statistics for monitoring/debugging.
     */
    override fun getStatistics(): SubscriptionStatistics {
        val entityStats = mutableMapOf<String, Int>()
        val eventStats = mutableMapOf<String, Int>()
        val webhookStats = mutableMapOf<String, Int>()

        automationIdsByTriggerKey.forEach { (key, automations) ->
            when (key) {
                is TriggerKey.Entity -> entityStats[key.entityId] = automations.size
                is TriggerKey.Event -> eventStats[key.eventType] = automations.size
                is TriggerKey.Webhook -> webhookStats[key.webhookId] = automations.size
            }
        }

        return SubscriptionStatistics(
            entities = entityStats,
            events = eventStats,
            webhooks = webhookStats
        )
    }

    companion object {
        private const val MAX_CONCURRENT_SUBSCRIBE_REQUESTS = 32
    }
}