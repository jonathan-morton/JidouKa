package dev.jidouka.client

import dev.jidouka.aliases.EntityId
import dev.jidouka.aliases.EventTypeId
import dev.jidouka.aliases.SubscriptionId
import dev.jidouka.components.StateObject
import dev.jidouka.configuration.HomeAssistantConfiguration
import dev.jidouka.network.NetworkResponse
import dev.jidouka.network.models.hass.websocket.StateData
import dev.jidouka.network.utils.toNativeMap
import dev.jidouka.registry.StateRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal interface ConnectionManager {

    /**
     * Initialize WebSocket connection to Home Assistant.
     * Returns after connection is established and authenticated.
     */
    suspend fun connect(scope: CoroutineScope)

    /**
     * Bulk load all entity states at startup
     * Does NOT trigger changeFlow.
     */
    suspend fun initializeStates()

    /**
     * Fetch a single entity's state
     * @return null if entity doesn't exist or network error occurs.
     */
    suspend fun fetchState(entityId: EntityId): StateObject?

    /**
     * Subscribe to state updates for an entity
     */
    suspend fun subscribeToEntity(entityId: EntityId): SubscriptionId

    /**
     * Subscribe to events of a specific type
     */
    suspend fun subscribeToEvent(eventTypeId: EventTypeId): SubscriptionId

    /**
     * Unsubscribe from updates
     */
    suspend fun unsubscribe(subscriptionId: SubscriptionId)

    /**
     * Disconnect from Home Assistant.
     */
    fun disconnect()

    /**
     * Run indefinitely.
     */
    suspend fun run()
}

@OptIn(ExperimentalAtomicApi::class)
internal class HomeAssistantConnectionManager(
    private val webSocketClient: HomeAssistantWebSocketClient,
    private val restClient: RestClient,
    private val stateRegistry: StateRegistry,
    private val configuration: HomeAssistantConfiguration,
    private val httpClient: HttpClient,
    private val onReconnected: (suspend () -> Unit)? = null
) : ConnectionManager {

    private var connectionJob: Job? = null

    private val logger = KotlinLogging.logger {}

    override suspend fun connect(scope: CoroutineScope) {
        connectionJob = scope.launch {
            connectWithRetry()
        }
        logger.info { "Connecting to Home Assistant Websocket..." }
    }

    private suspend fun connectWithRetry() {
        var connectionAttempt = 0
        var needsRestore = false

        while (true) {
            if (needsRestore && connectionAttempt > 0) {
                logger.info { "Reconnecting to Home Assistant (attempt ${connectionAttempt + 1})" }
            } else if (needsRestore.not()) {
                logger.info { "Connecting to Home Assistant" }
            }
            try {
                webSocketClient.connectToWebSocket(
                    client = httpClient,
                    configuration = configuration,
                    onConnected = if (needsRestore) {
                        { restoreAfterReconnect() }
                    } else {
                        null
                    }
                )
                logger.warn { "WebSocket connection ended, will reconnect..." }
                connectionAttempt = 0
            } catch (_: CancellationException) {
                logger.debug { "Connection to websocket was cancelled" }
                currentCoroutineContext().ensureActive()
                logger.warn { "WebSocket cancelled but parent active, will reconnect..." }
                connectionAttempt = 0
            } catch (exception: Exception) {
                logger.debug { "Connection to websocket had an exception" }
                connectionAttempt++

                val backoffDelay = calculateBackoffDelay(connectionAttempt)


                logger.warn(exception) {
                    "Connection failed (attempt $connectionAttempt), retrying in $backoffDelay"
                }

                delay(backoffDelay)
            }

            logger.warn { "WebSocket connection ended, will reconnect..." }
            webSocketClient.disconnect()
            stateRegistry.markAllStatesStale()
            needsRestore = true
        }
    }

    private suspend fun restoreAfterReconnect() {
        logger.info { "Restoring Jidouka application state after reconnection" }

        try {
            initializeStates()
            logger.debug { "Entity states reinitialized" }

            onReconnected?.invoke()
            logger.info { "Reconnection restoration completed" }
        } catch (exception: Exception) {
            logger.error(exception) { "Failed to restore state after reconnection" }
            throw exception
        }
    }

    private fun calculateBackoffDelay(attempt: Int): Duration {
        val initialReconnectBackoff = 5.seconds
        val maxBackoffDelay = 5.minutes
        val backoffMultiplier = 2.0
        val maxBackoffExponent = 5

        val exponent = (attempt - 1).coerceIn(0, maxBackoffExponent)
        val multiplier = backoffMultiplier.pow(exponent)

        val backoffDelay = initialReconnectBackoff * multiplier
        return minOf(backoffDelay, maxBackoffDelay)
    }

    override suspend fun initializeStates() {
        val result = webSocketClient.getStates()
        result.fold(
            onSuccess = { stateDataList ->
                stateDataList.forEach { stateData ->
                    stateRegistry.setInitialState(
                        entityId = stateData.entityId,
                        stateObject = stateData.toStateObject()
                    )
                }
                logger.info { "Initialized ${stateDataList.size} entity states" }
            },
            onFailure = { error ->
                logger.error(error) { "Failed to initialize entity states" }
            }
        )
    }

    override suspend fun fetchState(entityId: EntityId): StateObject? {
        val response = restClient.getState(entityId)
        return when (response) {
            is NetworkResponse.Success -> {
                response.data.toStateObject()
            }

            is NetworkResponse.Failure -> {
                logger.warn { "Failed to fetch state for '$entityId': ${response.error.message}" }
                null
            }
        }
    }

    override suspend fun subscribeToEntity(entityId: EntityId): SubscriptionId {
        return webSocketClient.subscribeToEntity(entityId)
    }

    override suspend fun subscribeToEvent(eventTypeId: EventTypeId): SubscriptionId {
        return webSocketClient.subscribeToEvent(eventTypeId)
    }

    override suspend fun unsubscribe(subscriptionId: SubscriptionId) {
        webSocketClient.unsubscribe(subscriptionId)
    }

    override fun disconnect() {
        logger.info { "Disconnecting from Home Assistant" }
        connectionJob?.cancel()
        webSocketClient.disconnect()
        connectionJob = null
    }

    override suspend fun run() {
        try {
            awaitCancellation()
        } finally {
            disconnect()
        }
    }

    private fun StateData.toStateObject() = StateObject(
        entityId = entityId,
        state = state,
        rawAttributes = attributes.toNativeMap(),
        lastChanged = lastChanged,
        lastUpdated = lastUpdated,
        lastReported = null
    )
}

