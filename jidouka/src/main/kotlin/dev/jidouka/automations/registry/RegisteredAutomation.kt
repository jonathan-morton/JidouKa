package dev.jidouka.automations.registry

import dev.jidouka.automations.AutomationMode
import dev.jidouka.automations.dsl.triggers.TriggerContext
import dev.jidouka.automations.dsl.triggers.TriggerMetadata
import kotlinx.coroutines.flow.Flow

internal data class RegisteredAutomation(
    val id: String,
    val mode: AutomationMode,
    val name: String,
    val description: String?,
    val runOnStartup: Boolean,
    val triggerFlow: Flow<TriggerContext>,
    val triggerMetadata: List<TriggerMetadata>,
    val evaluateStartup: suspend () -> TriggerContext?,
    val evaluateConditions: (suspend (TriggerContext) -> Boolean)?,
    val executeActions: suspend (TriggerContext) -> Unit
)