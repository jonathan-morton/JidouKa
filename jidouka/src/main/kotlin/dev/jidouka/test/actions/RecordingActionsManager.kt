package dev.jidouka.test.actions

import dev.jidouka.actions.ActionResponse
import dev.jidouka.actions.ActionsManager
import dev.jidouka.automations.AutomationContext
import dev.jidouka.network.JsonManager
import dev.jidouka.network.models.hass.websocket.ActionTarget
import dev.jidouka.test.RecordedEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.json.Json

internal class RecordingActionsManager(
    private val jsonManager: JsonManager,
    private val onActionCalled: (RecordedEvent.Action) -> Unit
) : ActionsManager {
    private val logger = KotlinLogging.logger {}

    override val json: Json
        get() = jsonManager.json

    override suspend fun callAction(
        domain: String,
        action: String,
        target: ActionTarget,
        data: Map<String, Any?>
    ): Result<Unit> {
        recordAction(domain, action, target, data)
        return Result.success(Unit)
    }

    override suspend fun callActionWithResponse(
        domain: String,
        action: String,
        target: ActionTarget,
        data: Map<String, Any?>
    ): Result<ActionResponse?> {
        recordAction(domain, action, target, data)
        return Result.success(null)
    }

    private suspend fun recordAction(
        domain: String,
        action: String,
        target: ActionTarget,
        data: Map<String, Any?>
    ) {
        val automationId = getAutomationId(domain, action)
        logger.info { "Recording action '$domain.$action' for automation '$automationId'" }

        val recordedAction = RecordedEvent.Action(
            automationId = automationId,
            domainId = domain,
            action = action,
            target = target,
            data = data
        )

        onActionCalled(recordedAction)
    }

    private suspend fun getAutomationId(domain: String, action: String): String {
        return currentCoroutineContext()[AutomationContext]?.automationId
            ?: run {
                logger.warn { "'$domain.$action' had no automationId. " }
                "unknown"
            }
    }
}