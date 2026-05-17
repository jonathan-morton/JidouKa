package dev.jidouka.test.registry

import dev.jidouka.components.webhook.WebhookObject
import dev.jidouka.registry.WebhookRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.ConcurrentHashMap

internal class InMemoryWebhookRegistry : WebhookRegistry {
    private val flows = ConcurrentHashMap<String, MutableSharedFlow<WebhookObject>>()
    private val logger = KotlinLogging.logger {}

    override fun getOrCreateFlow(webhookId: String): SharedFlow<WebhookObject> {
        return flows.computeIfAbsent(webhookId) {
            MutableSharedFlow(replay = 0, extraBufferCapacity = WEBHOOK_FLOW_EXTRA_BUFFER_CAPACITY)
        }
    }

    override suspend fun emitWebhook(webhook: WebhookObject) {
        val flow = flows[webhook.webhookId]
        if (flow != null) {
            flow.emit(webhook)
            logger.debug { "Emitted webhook '${webhook.webhookId}'" }
        } else {
            error(
                "Test emitted webhook '${webhook.webhookId}' but no automation is listening. " +
                        "Did you forget to register the automation first?"
            )
        }
    }

    companion object {
        private const val WEBHOOK_FLOW_EXTRA_BUFFER_CAPACITY = 64
    }
}
