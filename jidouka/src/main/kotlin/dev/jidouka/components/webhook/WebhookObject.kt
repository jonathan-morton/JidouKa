package dev.jidouka.components.webhook

import kotlinx.serialization.json.JsonObject

internal data class WebhookObject(
    val webhookId: String,
    val jsonData: JsonObject?,
    val formDataRepresentation: String?,
    val queryRepresentation: String?,
)
