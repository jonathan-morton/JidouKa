package dev.jidouka.automations.dsl.scopes

import dev.jidouka.actions.ActionResponse
import dev.jidouka.actions.ActionTargetBuilder
import dev.jidouka.actions.ActionsManager
import kotlinx.serialization.json.Json

/**
 * Scope for calling Home Assistant service actions
 */
public class ActionsScope internal constructor(
    private val actionsManager: ActionsManager
) {
    /**
     * Call a Home Assistant service action
     * This call should not receive a response
     */
    public suspend fun call(
        domain: String,
        action: String,
        data: Map<String, Any?> = emptyMap(),
        target: (ActionTargetBuilder.() -> Unit)? = null
    ): Result<Unit> {
        val actionTarget = target?.let {
            ActionTargetBuilder()
                .apply(target)
                .build()
        }

        return actionsManager.callAction(
            domain = domain,
            action = action,
            target = actionTarget,
            data = data
        )
    }

    /**
     * Call a Home Assistant service action
     * This call expects a response
     */
    public suspend fun callWithResponse(
        domain: String,
        action: String,
        data: Map<String, Any?> = emptyMap(),
        target: (ActionTargetBuilder.() -> Unit)? = null
    ): Result<ActionResponse?> {
        val actionTarget = target?.let {
            ActionTargetBuilder()
                .apply(target)
                .build()
        }

        return actionsManager.callActionWithResponse(
            domain = domain,
            action = action,
            target = actionTarget,
            data = data
        )
    }

    public fun <T : ActionResponse> ActionResponse.typed(factory: (Map<String, Any?>, Json) -> T): T {
        return typed(factory, actionsManager.json)
    }
}