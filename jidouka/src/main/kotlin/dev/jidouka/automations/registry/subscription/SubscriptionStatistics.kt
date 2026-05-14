package dev.jidouka.automations.registry.subscription

/**
 * Subscription statistics for observability.
 * @param entities subscription count by entity id
 * @param events subscription count by event type
 */
internal data class SubscriptionStatistics(
    val entities: Map<String, Int>,
    val events: Map<String, Int>,
    val webhooks: Map<String, Int> = emptyMap()
)