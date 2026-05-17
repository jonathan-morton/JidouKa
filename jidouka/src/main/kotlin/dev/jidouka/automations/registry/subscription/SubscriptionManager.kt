package dev.jidouka.automations.registry.subscription

import dev.jidouka.aliases.EntityId
import dev.jidouka.aliases.SubscriptionId
import dev.jidouka.aliases.WebhookId
import dev.jidouka.automations.dsl.triggers.TriggerMetadata
import dev.jidouka.automations.dsl.triggers.WebhookTriggerConfiguration
import dev.jidouka.automations.registry.TriggerKey
import dev.jidouka.client.ConnectionManager
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val logger = KotlinLogging.logger {}

    override suspend fun subscribe(
        metadata: TriggerMetadata,
        automationId: String
    ) = mutex.withLock {
        validateWebhookConfigurations(metadata)

        val keys: List<TriggerKey> = TriggerKey.getKeys(metadata)
        logger.debug { "Subscribing automation '$automationId' to ${keys.size} trigger key(s)" }

        keys.forEach { key ->
            val automationsSet: MutableSet<AutomationId> = automationIdsByTriggerKey.computeIfAbsent(key) {
                ConcurrentHashMap.newKeySet()
            }

            val wasEmpty = automationsSet.isEmpty()
            automationsSet.add(automationId)

            if (wasEmpty) {
                try {
                    val keyDescription = when (key) {
                        is TriggerKey.Entity -> "entity '${key.entityId}'"
                        is TriggerKey.Event -> "event type '${key.eventType}'"
                        is TriggerKey.Webhook -> "webhook '${key.webhookId}'"
                    }
                    logger.info { "Creating new subscription to $keyDescription for automation '$automationId'" }
                    val subscriptionId = subscribeToClient(key)
                    subscriptionIds[key] = subscriptionId
                } catch (exception: Exception) {
                    logger.error(exception) { "Failed to subscribe to key '$key' for automation '$automationId'" }
                    automationsSet.remove(automationId)
                }
            } else {
                logger.debug { "Added automation '$automationId' to existing subscription for key '$key' (${automationsSet.size} automation(s) total)" }
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

        keys.forEach { key ->
            val automationIds = automationIdsByTriggerKey[key] ?: return@forEach
            automationIds.remove(automationId)
            logger.debug { "Removed automation '$automationId' from subscription for key '$key' (${automationIds.size} automation(s) remaining)" }

            if (automationIds.isEmpty()) {
                automationIdsByTriggerKey.remove(key)

                if (key is TriggerKey.Webhook) {
                    webhookConfigurations.remove(key.webhookId)
                }

                val subscriptionId = subscriptionIds.remove(key) ?: return@forEach
                val keyDescription = when (key) {
                    is TriggerKey.Entity -> "entity '${key.entityId}'"
                    is TriggerKey.Event -> "event type '${key.eventType}'"
                    is TriggerKey.Webhook -> "webhook '${key.webhookId}'"
                }
                logger.info { "Removing subscription to $keyDescription (no automations remaining)" }
                unsubscribeFromClient(subscriptionId)
            }
        }
    }

    /**
     * Used after a disconnection to resubscribe
     */
    override suspend fun resubscribeAll() {
        logger.info {
            "Re-establishing ${automationIdsByTriggerKey.size} subscriptions after reconnection"
        }

        val oldSubscriptionCount = subscriptionIds.size
        subscriptionIds.clear()
        logger.debug { "Cleared $oldSubscriptionCount previous subscriptions" }

        var successCount = 0
        var failureCount = 0

        automationIdsByTriggerKey.forEach { (key, automationIds) ->
            if (automationIds.isNotEmpty()) {
                try {
                    val newSubscriptionId = subscribeToClient(key)
                    subscriptionIds[key] = newSubscriptionId

                    logger.debug {
                        val keyDescription = when (key) {
                            is TriggerKey.Entity -> "entity ${key.entityId}"
                            is TriggerKey.Event -> "event ${key.eventType}"
                            is TriggerKey.Webhook -> "webhook '${key.webhookId}'"
                        }
                        """
                        Resubscribed to $keyDescription for ${automationIds.size} automations
                        New subscription ID: $newSubscriptionId
                        """.trimIndent()
                    }

                    successCount++
                } catch (exception: Exception) {
                    failureCount++
                    logger.error(exception) { "Failed to resubscribe to key: $key" }
                }
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
}