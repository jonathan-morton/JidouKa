package dev.jidouka.components

import dev.jidouka.automations.AutomationContext
import dev.jidouka.automations.registry.subscription.SubscriptionManager
import dev.jidouka.registry.StateRegistry
import dev.jidouka.usecases.EnsureEntitySubscribedAndCurrentUseCase
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlin.time.Instant

public class Entity<S : BaseState<S>> internal constructor(
    objectId: String,
    public val domain: Domain<S>,
    private val parser: BaseState.Parser<S>,
    private val stateRegistry: StateRegistry,
    private val ensureSubscribedUseCase: EnsureEntitySubscribedAndCurrentUseCase,
    private val scope: CoroutineScope
) {
    private val logger = KotlinLogging.logger {}

    public val entityId: String = "${domain.id}.${objectId}"

    internal val rawStateFlow: SharedFlow<StateTransition>
        get() = stateRegistry.getStateFlow(entityId)

    public val stateFlow: Flow<S?> = rawStateFlow.map { transition ->
        val state = parseTransition(transition)
        return@map state
    }.shareIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        replay = 1
    )

    /**
     * Returns the most recent state from the replay cache, or null if no state
     * has been received yet. Unlike [state], this does not ensure the entity
     * is subscribed. Prefer [state] when writing automations.
     */
    public val cachedState: S?
        get() = (stateFlow as? SharedFlow)?.replayCache?.firstOrNull()

    public val stateValue: String?
        get() = cachedState?.stateRaw

    internal val changeFlow: SharedFlow<StateTransition>
        get() = stateRegistry.getChangeFlow(entityId)

    public suspend fun state(): S? {
        val automationId = currentCoroutineContext()[AutomationContext]?.automationId
            ?: run {
                logger.warn {
                    "Entity.state() called for '$entityId' without AutomationContext. " +
                            "Subscription cleanup cannot be tracked per-automation. " +
                            "Ensure entity.state() is called within an automation scope."
                }

                SubscriptionManager.UNTRACKED_SUBSCRIPTION_ID
            }
        ensureSubscribedUseCase.ensure(entityId, automationId)
        return (stateFlow as? SharedFlow)?.replayCache?.firstOrNull()
    }

    public fun parseTransition(transition: StateTransition): S? {
        val state = parser.parse((transition.toState))
        val previousState = transition.fromState?.let { parser.parse(transition.fromState) }
        state?.previous = previousState
        return state
    }
}

internal interface State {
    val previous: State? get() = null
}

public abstract class BaseState<S : BaseState<S>> : State {
    final override var previous: S? = null
        internal set

    public abstract val stateRaw: String

    public abstract val attributesRaw: Map<String, Any?>

    @Deprecated("use attributesRaw", replaceWith = ReplaceWith("attributesRaw"))
    public val rawAttributes: Map<String, Any?>
        get() = attributesRaw

    public abstract val lastChanged: Instant?
    public abstract val lastUpdated: Instant?
    public abstract val lastReported: Instant?

    /**
     * Parser for converting raw StateObject into typed BaseState instances.
     */
    public interface Parser<S : BaseState<*>> {
        public fun parse(stateObject: StateObject): S?
    }
}

public data class StateTransition(
    val toState: StateObject,
    val fromState: StateObject?
)

public data class StateObject(
    val entityId: String,
    val state: String,
    val attributesRaw: Map<String, Any?>,
    val lastChanged: Instant,
    val lastUpdated: Instant,
    val lastReported: Instant?
) {
    @Deprecated("use attributesRaw", replaceWith = ReplaceWith("attributesRaw"))
    val rawAttributes: Map<String, Any?>
        get() = attributesRaw

    private val logger = KotlinLogging.logger {}

    /**
     * Check if this state represents an unavailable or unknown entity.
     * Home Assistant uses "unavailable" when entities are offline/disconnected,
     * and "unknown" when the state cannot be determined.
     *
     * Parsers should return null for these states without logging,
     * as they represent expected conditions, not parsing failures.
     */
    public fun isUnavailable(): Boolean {
        when {
            state.equals("unavailable", ignoreCase = true) -> {
                logger.info { "$entityId is unavailable" }
                return true
            }

            state.equals("unknown", ignoreCase = true) -> {
                logger.info { "$entityId has unknown state" }
                return true
            }
        }
        return false
    }
}