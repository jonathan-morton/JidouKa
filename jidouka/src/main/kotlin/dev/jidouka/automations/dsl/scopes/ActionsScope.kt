package dev.jidouka.automations.dsl.scopes

import dev.jidouka.actions.ActionResponse
import dev.jidouka.actions.ActionTargetBuilder
import dev.jidouka.actions.ActionsManager
import dev.jidouka.network.models.hass.websocket.ActionTarget
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer

/**
 * Scope for calling Home Assistant service actions
 */
public class ActionsScope internal constructor(
    private val actionsManager: ActionsManager
) {
    @PublishedApi
    internal val json: Json get() = actionsManager.json

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
        return actionsManager.callAction(
            domain = domain,
            action = action,
            target = buildTarget(target),
            data = data
        )
    }

    /**
     * Call a Home Assistant service action with a @Serializable data object.
     * This call should not receive a response.
     */
    public suspend inline fun <reified T> call(
        domain: String,
        action: String,
        data: T,
        noinline target: (ActionTargetBuilder.() -> Unit)? = null
    ): Result<Unit> {
        val element = json.encodeToJsonElement(serializer<T>(), data)
        return callWithJsonData(domain, action, element.jsonObject, target)
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
        return actionsManager.callActionWithResponse(
            domain = domain,
            action = action,
            target = buildTarget(target),
            data = data
        )
    }

    /**
     * Call a Home Assistant service action with a @Serializable data object.
     * This call expects a response.
     */
    public suspend inline fun <reified T> callWithResponse(
        domain: String,
        action: String,
        data: T,
        noinline target: (ActionTargetBuilder.() -> Unit)? = null
    ): Result<ActionResponse?> {
        val element = json.encodeToJsonElement(serializer<T>(), data)
        return callWithResponseWithJsonData(domain, action, element.jsonObject, target)
    }

    @PublishedApi
    internal suspend fun callWithJsonData(
        domain: String,
        action: String,
        data: JsonObject?,
        target: (ActionTargetBuilder.() -> Unit)?
    ): Result<Unit> {
        return actionsManager.callAction(
            domain = domain,
            action = action,
            target = buildTarget(target),
            data = data
        )
    }

    @PublishedApi
    internal suspend fun callWithResponseWithJsonData(
        domain: String,
        action: String,
        data: JsonObject?,
        target: (ActionTargetBuilder.() -> Unit)?
    ): Result<ActionResponse?> {
        return actionsManager.callActionWithResponse(
            domain = domain,
            action = action,
            target = buildTarget(target),
            data = data
        )
    }

    public fun <T : ActionResponse> ActionResponse.typed(factory: (Map<String, Any?>, Json) -> T): T {
        return typed(factory, actionsManager.json)
    }

    private fun buildTarget(
        target: (ActionTargetBuilder.() -> Unit)?
    ): ActionTarget? {
        return target?.let {
            ActionTargetBuilder()
                .apply(it)
                .build()
        }
    }
}