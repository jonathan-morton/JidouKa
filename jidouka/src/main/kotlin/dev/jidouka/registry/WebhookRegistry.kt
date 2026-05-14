package dev.jidouka.registry

import dev.jidouka.components.webhook.WebhookObject
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

internal interface WebhookRegistry {
    fun getOrCreateFlow(webhookId: String): SharedFlow<WebhookObject>
    suspend fun emitWebhook(webhook: WebhookObject)
}

@Single(binds = [WebhookRegistry::class])
internal class HomeAssistantWebhookRegistry : WebhookRegistry {
    private val flows = ConcurrentHashMap<String, MutableSharedFlow<WebhookObject>>()
    private val mutex = Mutex()
    private val logger = KotlinLogging.logger {}

    override fun getOrCreateFlow(webhookId: String): SharedFlow<WebhookObject> {
        var wasCreated = false
        val flow = flows.computeIfAbsent(webhookId) {
            wasCreated = true
            MutableSharedFlow(
                replay = 0,
                extraBufferCapacity = WEBHOOK_FLOW_EXTRA_BUFFER_CAPACITY
            )
        }
        if (wasCreated) {
            logger.debug { "Created webhook flow for webhook ID '$webhookId'" }
        }
        return flow
    }

    override suspend fun emitWebhook(webhook: WebhookObject) {
        mutex.withLock {
            val flow = flows[webhook.webhookId]
            if (flow != null) {
                flow.emit(webhook)
                logger.debug { "Emitted webhook object for webhook ID '${webhook.webhookId}'" }
            } else {
                logger.warn { "No subscribers for webhook ID '${webhook.webhookId}', webhook dropped" }
            }
        }
    }

    companion object {
        private const val WEBHOOK_FLOW_EXTRA_BUFFER_CAPACITY = 64
    }
}