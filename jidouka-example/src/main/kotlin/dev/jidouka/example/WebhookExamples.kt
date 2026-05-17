package dev.jidouka.example

import dev.jidouka.api.Jidouka
import dev.jidouka.automations.AutomationMode

object WebhookExamples {

    private const val ECHO_HOOK_ID = "jidouka-test-hook"
    private const val FILTERED_HOOK_ID = "jidouka-test-hook-filtered"

    val webhookEcho = Jidouka.automation(
        id = "webhook_echo",
        mode = AutomationMode.Parallel(10)
    ) {
        triggers {
            webhook(ECHO_HOOK_ID)
        }
        actions {
            val context = triggered.webhook() ?: return@actions

            log.info { "Webhook received: ${context.webhookId}" }
            log.info { "Full JSON map: ${context.jsonData}" }

            val status = context.jsonData?.get("status") as? String
            val count = context.jsonData?.get("count") as? Int
            val enabled = context.jsonData?.get("enabled") as? Boolean
            log.info { "Typed JSON fields - status: $status (${status?.javaClass?.simpleName}), count: $count (${count?.javaClass?.simpleName}), enabled: $enabled (${enabled?.javaClass?.simpleName})" }

            val nested = context.jsonData?.get("nested") as? Map<*, *>
            if (nested != null) {
                log.info { "Nested map: $nested" }
            }

            context.formDataRepresentation?.let { log.info { "dataRepr: $it" } }
            context.queryRepresentation?.let { log.info { "queryRepr: $it" } }
        }
    }

    val webhookFiltered = Jidouka.automation(
        id = "webhook_filtered",
        mode = AutomationMode.Parallel(10)
    ) {
        triggers {
            webhook(FILTERED_HOOK_ID) { payload ->
                val priority = payload.jsonData?.get("priority") as? Int ?: 0
                priority >= 5
            }
        }
        actions {
            val priority = triggered.webhook()?.jsonData?.get("priority") as? Int
            log.info { "Filtered webhook accepted with priority=$priority" }
        }
    }

    val automations = listOf(webhookEcho, webhookFiltered)
}
