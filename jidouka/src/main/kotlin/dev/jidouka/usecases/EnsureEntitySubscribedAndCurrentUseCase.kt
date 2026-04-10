package dev.jidouka.usecases

import dev.jidouka.aliases.EntityId
import dev.jidouka.automations.dsl.triggers.TriggerMetadata
import dev.jidouka.automations.registry.subscription.SubscriptionManager
import dev.jidouka.client.ConnectionManager
import dev.jidouka.registry.StateRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.annotation.Single

internal interface EnsureEntitySubscribedAndCurrentUseCase {
    suspend fun ensure(
        entityId: EntityId,
        automationId: String
    )
}

@Single
internal class HomeAssistantEnsureEntitySubscribedAndCurrentUseCase(
    private val connectionManager: ConnectionManager,
    private val subscriptionManager: SubscriptionManager,
    private val stateRegistry: StateRegistry
) : EnsureEntitySubscribedAndCurrentUseCase {
    private val logger = KotlinLogging.logger {}

    override suspend fun ensure(
        entityId: EntityId,
        automationId: String
    ) {
        val hasSubscription = subscriptionManager.hasActiveSubscription(entityId)

        if (hasSubscription) {
            logger.debug { "Entity '$entityId' already has state and subscription" }
            return
        }

        val currentState = connectionManager.fetchState(entityId) ?: run {
            logger.warn { "Failed to fetch state for entity '$entityId'" }
            return
        }
        logger.debug { "Set current state for entity '$entityId'" }
        stateRegistry.setCurrentState(entityId, currentState)

        subscriptionManager.subscribe(
            metadata = TriggerMetadata.StateTrigger(setOf(entityId)),
            automationId = automationId
        )
    }
}