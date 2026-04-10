package dev.jidouka.client

import dev.jidouka.actions.ActionResponse
import dev.jidouka.aliases.SubscriptionId
import dev.jidouka.network.models.hass.websocket.StateData
import kotlinx.coroutines.CompletableDeferred

/**
 * Pending websocket calls
 */
internal sealed class PendingOperation {
    abstract val deferred: CompletableDeferred<*>

    internal data class Subscription(
        override val deferred: CompletableDeferred<SubscriptionId>
    ) : PendingOperation()

    internal data class ServiceActionCall(
        override val deferred: CompletableDeferred<ActionResponse?>
    ) : PendingOperation()

    internal data class GetStates(
        override val deferred: CompletableDeferred<List<StateData>>
    ) : PendingOperation()

    internal data class Ping(
        override val deferred: CompletableDeferred<Boolean>
    ) : PendingOperation()
}