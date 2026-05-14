package dev.jidouka.client

import dev.jidouka.actions.ActionResponse
import dev.jidouka.aliases.SubscriptionId
import dev.jidouka.components.StateObject
import dev.jidouka.components.event.EventObject
import dev.jidouka.configuration.HomeAssistantConfiguration
import dev.jidouka.network.JsonManager
import dev.jidouka.network.models.hass.websocket.ActionTarget
import dev.jidouka.network.models.hass.websocket.EventData
import dev.jidouka.network.models.hass.websocket.EventResponse
import dev.jidouka.network.models.hass.websocket.HaRequest
import dev.jidouka.network.models.hass.websocket.HaResponse
import dev.jidouka.network.models.hass.websocket.MessageBase
import dev.jidouka.network.models.hass.websocket.ResultResponse
import dev.jidouka.network.models.hass.websocket.StateData
import dev.jidouka.network.models.hass.websocket.TriggerConfiguration
import dev.jidouka.network.utils.toNativeMap
import dev.jidouka.registry.EventRegistry
import dev.jidouka.registry.StateRegistry
import dev.jidouka.usecases.AuthenticationUseCase
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.*
import io.ktor.websocket.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

private typealias MessageId = Int

@OptIn(ExperimentalAtomicApi::class)
@Single
internal class HomeAssistantWebSocketClient(
    private val authenticationUseCase: AuthenticationUseCase,
    private val stateRegistry: StateRegistry,
    private val jsonManager: JsonManager,
    private val eventRegistry: EventRegistry
) : HomeAssistantWebSocket {
    private var session: WebSocketSession? = null

    private var messageIdCounter = AtomicInt(1)
    private val sendMutex = Mutex()

    private val pendingSubscriptions = ConcurrentHashMap<MessageId, PendingOperation>()

    private var heartbeatJob: Job? = null

    private val logger = KotlinLogging.logger {}

    @OptIn(ExperimentalTime::class)
    suspend fun connectToWebSocket(
        client: HttpClient,
        configuration: HomeAssistantConfiguration,
        onConnected: (suspend () -> Unit)? = null
    ) {
        client.webSocket(
            host = configuration.host,
            port = configuration.port,
            path = configuration.websocketPath
        ) {
            session = this

            val authenticationResult = authenticationUseCase.authenticate(
                accessToken = configuration.accessToken,
                receiveMessage = { receiveTextFrame() },
                sendMessage = { json ->
                    send(Frame.Text(json))
                }
            )

            authenticationResult.fold(
                onSuccess = { haVersion ->
                    logger.info { "Authenticated successfully (HA version: $haVersion)" }
                },
                onFailure = { error ->
                    logger.error(error) { "Authentication failed" }
                    throw error
                }
            )

            val connectionLost = CompletableDeferred<Unit>()
            heartbeatJob = launch {
                try {
                    monitorHeartbeat()
                } finally {
                    connectionLost.complete(Unit)
                }
            }

            val frames = MutableSharedFlow<Frame>(
                replay = 0,
                extraBufferCapacity = 100,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )

            launch { handleMessages(frames.asSharedFlow()) }

            launch {
                for (frame in incoming) {
                    frames.emit(frame)
                }
                connectionLost.complete(Unit)
            }

            onConnected?.invoke()

            connectionLost.await()
            coroutineContext.cancelChildren()
        }
    }

    fun disconnect() {
        logger.info { "Disconnecting from WebSocket..." }

        heartbeatJob?.cancel()
        heartbeatJob = null

        session = null

        val orphanedPendingSubscriptionCount = pendingSubscriptions.size

        pendingSubscriptions.forEach { (_, operation) ->
            operation.deferred.completeExceptionally(
                IllegalStateException("Websocket disconnected")
            )
        }
        pendingSubscriptions.clear()

        if (orphanedPendingSubscriptionCount > 0) {
            logger.debug { "Cancelled $orphanedPendingSubscriptionCount pending operation(s)" }
        }

        logger.info { "Disconnected from WebSocket" }
    }

    private suspend fun monitorHeartbeat() {
        logger.debug { "Starting heartbeat monitor (interval: $heartbeatInterval, timeout: $pongTimeout" }

        while (true) {
            delay(heartbeatInterval)

            val pongReceived = withTimeoutOrNull(pongTimeout) {
                val deferred = CompletableDeferred<Boolean>()

                val pingId = sendRequest(
                    createOperation = { PendingOperation.Ping(deferred) },
                    createRequest = { messageId ->
                        HaRequest.PingRequest(id = messageId)
                    }
                )

                logger.trace { "Sent ping (ID: $pingId), awaiting pong..." }
                deferred.await()
            }

            if (pongReceived == null) {
                logger.error { "Websocket heartbeat failure: no pong received within $pongTimeout after ping" }
                return
            }

            logger.trace { "Heartbeat OK (pong received)" }
        }
    }

    /**
     * Sends a request with synchronized ID allocation to ensure messages are always sent
     * with strictly increasing IDs, preventing "Identifier values have to increase" errors.
     *
     * @param createOperation Create a PendingOperation for tracking responses if needed.
     * @param createRequest Create the HaRequest with the allocated message ID.
     * @return The message ID that was used for the request.
     */
    private suspend fun sendRequest(
        createOperation: (() -> PendingOperation)? = null,
        createRequest: (messageId: Int) -> HaRequest,
    ): Int {
        return sendMutex.withLock {
            val messageId = messageIdCounter.fetchAndAdd(1)
            createOperation?.let {
                pendingSubscriptions[messageId] = it()
            }
            val request = createRequest(messageId)
            sendMessage(request)
            messageId
        }
    }

    @Throws(IllegalStateException::class)
    override suspend fun subscribeToEntity(entityId: String): SubscriptionId {
        val subscriptionIdDeferred = CompletableDeferred<SubscriptionId>()

        sendRequest(
            createOperation = { PendingOperation.Subscription(subscriptionIdDeferred) },
            createRequest = { messageId ->
                HaRequest.SubscribeTriggerRequest(
                    id = messageId,
                    trigger = TriggerConfiguration(
                        entityId = entityId,
                        platform = TriggerConfiguration.Platform.State,
                    )
                )
            }
        )

        return subscriptionIdDeferred.await()
    }

    override suspend fun unsubscribe(
        subscriptionId: SubscriptionId
    ) {
        sendRequest(
            createRequest = { messageId ->
                HaRequest.UnsubscribeEventsRequest(
                    id = messageId,
                    subscriptionId
                )
            }
        )
    }

    override suspend fun subscribeToEvent(eventType: String): SubscriptionId {
        val subscriptionIdDeferred = CompletableDeferred<SubscriptionId>()

        sendRequest(
            createOperation = { PendingOperation.Subscription(subscriptionIdDeferred) },
            createRequest = { messageId ->
                HaRequest.SubscribeTriggerRequest(
                    id = messageId,
                    trigger = TriggerConfiguration(
                        platform = TriggerConfiguration.Platform.Event,
                        eventType = eventType
                    )
                )
            }
        )

        return subscriptionIdDeferred.await()
    }

    override suspend fun callServiceAction(
        domain: String,
        service: String,
        target: ActionTarget?,
        serviceData: JsonObject?,
        returnResponse: Boolean
    ): Result<ActionResponse?> {
        return runCatching {
            val deferred = CompletableDeferred<ActionResponse?>()

            sendRequest(
                createOperation = { PendingOperation.ServiceActionCall(deferred) },
                createRequest = { messageId ->
                    HaRequest.CallServiceActionRequest(
                        id = messageId,
                        domain = domain,
                        action = service,
                        target = target,
                        actionData = serviceData,
                        returnResponse = returnResponse
                    )
                }
            )

            deferred.await()
        }
    }

    override suspend fun getStates(): Result<List<StateData>> {
        return runCatching {
            val deferred = CompletableDeferred<List<StateData>>()

            sendRequest(
                createOperation = { PendingOperation.GetStates(deferred) },
                createRequest = { messageId ->
                    HaRequest.GetStatesRequest(messageId)
                }
            )

            deferred.await()
        }
    }

    private fun shouldLogMessage(message: MessageBase): Boolean {
        return when (message) {
            is HaRequest.PingRequest -> false
            is HaResponse.Pong -> false
            else -> true
        }
    }

    private suspend fun sendMessage(message: MessageBase) {
        val jsonRequest = jsonManager.json.encodeToString(message)

        if (shouldLogMessage(message)) {
            logger.debug { "WebSocket sending: ${message::class.simpleName}" }
        }

        session?.send(Frame.Text(jsonRequest))
            ?: run {
                logger.error { "Cannot send message - no WebSocket session" }
                throw IllegalStateException("No websocket")
            }
    }

    private suspend fun handleMessages(frames: SharedFlow<Frame>) {
        frames
            .filterIsInstance<Frame.Text>()
            .map { it.readText() }
            .collect { json ->
                try {
                    val message = parseMessage(json)

                    if (shouldLogMessage(message)) {
                        logger.debug { "WebSocket received message\n$json" }
                        logger.debug { "Parsed message type: ${message::class.simpleName}" }
                    }

                    routeMessage(message)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to parse WebSocket message" }
                }
            }
    }

    private fun parseMessage(rawJson: String): MessageBase {
        return try {
            jsonManager.json.decodeFromString<MessageBase>(rawJson)
        } catch (exception: Exception) {
            logger.error(exception) { "Message parsing failed" }
            throw exception
        }
    }

    private suspend fun routeMessage(message: MessageBase) {
        when (message) {
            is EventResponse -> handleEventResponse(message)
            is ResultResponse -> handleResultResponse(message)
            is HaResponse.Pong -> handlePongResponse(message)
            is HaRequest -> {
                logger.error { "Unexpected HaRequest message from WebSocket\n$message" }
            }
        }
    }

    private suspend fun handleEventResponse(eventResponse: EventResponse) {
        suspend fun handleTriggerEvent(response: EventResponse.TriggerEventResponse) {
            val trigger = response.event.variables.trigger

            when (trigger.platform) {
                TriggerConfiguration.Platform.State -> {
                    if (trigger.entityId != null && trigger.toState != null) {
                        stateRegistry.updateState(
                            entityId = trigger.entityId,
                            newState = trigger.toState.toStateObject(),
                            previousState = trigger.fromState?.toStateObject()
                        )
                    } else {
                        logger.warn { "State trigger missing entityId or toState" }
                    }
                }

                TriggerConfiguration.Platform.Event -> {
                    if (trigger.event != null) {
                        logger.debug { "Event trigger received: ${trigger.event.eventType}" }
                        eventRegistry.emitEvent(trigger.event.toEventObject())
                    } else {
                        logger.warn { "Event trigger missing event data" }
                    }
                }

                TriggerConfiguration.Platform.Unknown -> {
                    logger.error { "Unknown trigger platform received" }
                }
            }
        }

        when (eventResponse) {
            is EventResponse.TriggerEventResponse -> handleTriggerEvent(eventResponse)
        }
    }

    private fun handleResultResponse(response: ResultResponse) {
        val operation = pendingSubscriptions.remove(response.id)
        when (operation) {
            is PendingOperation.Subscription -> {
                when (response) {
                    is ResultResponse.Success -> {
                        logger.debug { "Subscription successful (ID: ${response.id})" }
                        operation.deferred.complete(response.id)
                    }

                    is ResultResponse.Error -> {
                        logger.error { "Subscription failed (ID: ${response.id}): ${response.error}" }
                        operation.deferred.completeExceptionally(
                            Exception("Subscription failed: ${response.error}")
                        )
                    }
                }
            }

            is PendingOperation.ServiceActionCall -> {
                when (response) {
                    is ResultResponse.Success -> {
                        val actionResponse = response.result?.let { jsonElement ->
                            val data = jsonManager.json.decodeFromJsonElement<ResultResponse.Success.Data>(jsonElement)
                            data.response?.let { responseJson -> ActionResponse(responseJson.toNativeMap()) }
                        }

                        logger.debug { "Service action call successful (ID: ${response.id})" }
                        operation.deferred.complete(actionResponse)
                    }

                    is ResultResponse.Error -> {
                        logger.error { "Service action call failed (ID: ${response.id}): ${response.error}" }
                        operation.deferred.completeExceptionally(
                            Exception("Service call failed: ${response.error}")
                        )
                    }
                }
            }

            is PendingOperation.GetStates -> {
                when (response) {
                    is ResultResponse.Success -> {
                        val statesData = response.result?.let { jsonElement ->
                            jsonManager.json.decodeFromJsonElement<List<StateData>>(jsonElement)
                        } ?: emptyList()

                        logger.debug { "get_states successful (ID: ${response.id}): ${statesData.size} states" }
                        operation.deferred.complete(statesData)
                    }

                    is ResultResponse.Error -> {
                        logger.error { "get_states failed (ID: ${response.id}): ${response.error}" }
                        operation.deferred.completeExceptionally(
                            Exception("get_states failed: ${response.error}")
                        )
                    }
                }
            }

            is PendingOperation.Ping -> {
                logger.warn {
                    "Received unexpected result response for ping operation (ID: ${response.id}). "
                }

                operation.deferred.completeExceptionally(
                    IllegalStateException("Ping received result response instead of pong response")
                )
            }

            null -> logger.warn { "Received result for unknown message ID: ${response.id}" }
        }
    }

    private fun handlePongResponse(message: HaResponse.Pong) {
        val pongId = message.id
        val operation = pendingSubscriptions.remove(pongId)
        when (operation) {
            is PendingOperation.Ping -> {
                logger.trace { "Received pong (ID: $pongId" }
                operation.deferred.complete(true)
            }

            null -> logger.warn { "Received pong for unknown ping ID: $pongId" }
            is PendingOperation.GetStates,
            is PendingOperation.ServiceActionCall,
            is PendingOperation.Subscription -> logger.error { "Unexpected operation type for pong response: ${operation::class.simpleName}" }
        }
    }

    private fun StateData.toStateObject(): StateObject {
        return StateObject(
            entityId = this.entityId,
            state = this.state,
            rawAttributes = this.attributes.toNativeMap(),
            lastChanged = this.lastChanged,
            lastUpdated = this.lastUpdated,
            lastReported = this.lastReported
        )
    }

    private fun EventData.toEventObject(): EventObject {
        return EventObject(
            eventType = this.eventType,
            data = this.data.toNativeMap(),
            timeFired = this.timeFired,
            origin = this.origin,
            context = this.context
        )
    }

    private suspend fun WebSocketSession.receiveTextFrame(): String {
        for (frame in incoming) {
            if (frame is Frame.Text) {
                return frame.readText()
            }
        }
        throw Exception("Connection closed while waiting for message")
    }

    companion object {
        private val heartbeatInterval = 30.seconds
        private val pongTimeout = 10.seconds
    }
}


internal fun HttpClientConfig<CIOEngineConfig>.configureSockets() {
    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }
}

