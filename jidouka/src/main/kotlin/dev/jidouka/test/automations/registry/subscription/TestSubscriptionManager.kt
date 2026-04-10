package dev.jidouka.test.automations.registry.subscription

import dev.jidouka.aliases.EntityId
import dev.jidouka.automations.dsl.triggers.TriggerMetadata
import dev.jidouka.automations.registry.TriggerKey
import dev.jidouka.automations.registry.subscription.AutomationId
import dev.jidouka.automations.registry.subscription.SubscriptionManager
import dev.jidouka.automations.registry.subscription.SubscriptionStatistics
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

internal class TestSubscriptionManager : SubscriptionManager {
    private val logger = KotlinLogging.logger {}

    private val subscribedKeys = ConcurrentHashMap.newKeySet<TriggerKey>()
    private val automationIdsByTriggerKey = ConcurrentHashMap<TriggerKey, MutableSet<AutomationId>>()

    override suspend fun subscribe(
        metadata: TriggerMetadata,
        automationId: String
    ) {
        val keys: List<TriggerKey> = TriggerKey.getKeys(metadata)
        keys.forEach { key ->
            subscribedKeys.add(key)

            val automationsSet: MutableSet<AutomationId> = automationIdsByTriggerKey.computeIfAbsent(key) {
                ConcurrentHashMap.newKeySet()
            }
            automationsSet.add(automationId)

            logger.debug { "Test subscribe: automation '$automationId' -> key '$key'" }
        }
    }

    override suspend fun unsubscribe(
        metadata: TriggerMetadata,
        automationId: AutomationId
    ) {
        val keys: List<TriggerKey> = TriggerKey.getKeys(metadata)
        keys.forEach { key ->
            automationIdsByTriggerKey[key]?.remove(automationId)

            if (automationIdsByTriggerKey[key]?.isEmpty() == true) {
                automationIdsByTriggerKey.remove(key)
                subscribedKeys.remove(key)
            }
            logger.debug { "Test unsubscribe: automation '$automationId' -> key '$key'" }
        }
    }

    override fun hasActiveSubscription(entityId: EntityId): Boolean {
        return subscribedKeys.contains(TriggerKey.Entity(entityId))
    }

    override fun getAutomations(key: TriggerKey): Set<AutomationId> {
        return automationIdsByTriggerKey[key]?.toSet() ?: emptySet()
    }

    override fun getAllKeys(): Set<TriggerKey> {
        return subscribedKeys.toSet()
    }

    override fun getStatistics(): SubscriptionStatistics {
        return SubscriptionStatistics(entities = emptyMap(), events = emptyMap())
    }

    override suspend fun resubscribeAll() {
        logger.debug { "Test resubscribeAll called - no action needed for test implementation" }
    }
}