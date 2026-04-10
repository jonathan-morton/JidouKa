package dev.jidouka.test

import dev.jidouka.aliases.EntityId
import dev.jidouka.api.JidoukaApplication
import dev.jidouka.api.automation.AutomationRegistrationScope
import dev.jidouka.automations.Automation
import dev.jidouka.automations.registry.AutomationRegistry
import dev.jidouka.automations.registry.subscription.AutomationId
import dev.jidouka.components.StateObject
import dev.jidouka.components.event.EventObject
import dev.jidouka.network.models.hass.websocket.Context
import dev.jidouka.test.di.testModule
import dev.jidouka.test.monitors.TestTimeMonitor
import dev.jidouka.test.registry.InMemoryEventRegistry
import dev.jidouka.test.registry.InMemoryStateRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.koin.plugin.module.dsl.koinApplication
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Test environment for automations.
 * @param testScope The coroutine test scope for controlling coroutine execution
 * @param timeZone Timezone for time-based operations (defaults to UTC)
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class)
public class AutomationTestEnvironment(
    private val testScope: TestScope,
    private val timeZone: TimeZone = TimeZone.UTC
) {
    private val logger = KotlinLogging.logger {}

    /**
     * All events recorded during test execution.
     */
    public val recordedEvents: List<RecordedEvent>
        field = mutableListOf<RecordedEvent>()

    /**
     * Hot flow of events as they occur during test execution.
     */
    public val recordedEventsFlow: SharedFlow<RecordedEvent>
        field = MutableSharedFlow<RecordedEvent>(
            replay = 0,
            extraBufferCapacity = 128
        )

    private val koin = koinApplication<JidoukaApplication> {
        modules(
            testModule(testScope, timeZone) { event ->
                recordedEvents.add(event)
                recordedEventsFlow.tryEmit(event)
            }
        )
    }.koin

    private val stateRegistry: InMemoryStateRegistry = koin.get()
    private val eventRegistry: InMemoryEventRegistry = koin.get()
    private val timeMonitor: TestTimeMonitor = koin.get()

    private val automationRegistry: AutomationRegistry = koin.get()
    private val registrationScope: AutomationRegistrationScope = koin.get()

    /**
     * Sets the entity state without trigger an automation
     * Used to set up test environment before registering automations
     * @param entityId The entity to set state for
     * @param state The state value
     * @param attributes Optional entity attributes
     */
    public fun setEntityState(
        entityId: EntityId,
        state: String,
        attributes: Map<String, Any?> = emptyMap()
    ) {
        val currentTime = timeMonitor.currentTime
        val stateObject = StateObject(
            entityId = entityId,
            state = state,
            rawAttributes = attributes,
            lastChanged = currentTime,
            lastUpdated = currentTime,
            lastReported = currentTime
        )

        stateRegistry.setCurrentState(entityId = entityId, stateObject = stateObject)
        logger.debug { "Set entity '$entityId' to state '$state'" }
    }

    /**
     * Sets the test clock to a specific time.
     *
     * This affects all time-based triggers and [TimeAccess] queries.
     * Use [advanceClock] to move time forward from the current point.
     *
     * @param time The local date/time to set
     */
    public suspend fun setClockTime(time: LocalDateTime) {
        val instant = time.toInstant(timeZone)
        timeMonitor.setTime(instant)
        logger.debug { "Clock set to $time" }
    }

    /**
     * Registers an automation in the test environment.
     *
     * After registration, the automation will respond to state changes,
     * events, and time triggers. Always call this after setting up initial
     * state with [setEntityState].
     *
     * This function suspends until all registration side effects complete
     *
     * @param automation The automation to register
     */
    public suspend fun register(automation: Automation) {
        automationRegistry.register(automation)

        testScope.testScheduler.advanceUntilIdle()

        logger.info { "Automation '${automation.id}' registered in test environment" }
    }

    /**
     * Emits a state change, triggering any listening automations.
     * @param entityId The entity to set state for
     * @param state The state value
     * @param attributes Optional entity attributes
     */
    public fun emitState(
        entityId: EntityId,
        state: String,
        attributes: Map<String, Any?> = emptyMap()
    ) {
        val previousState = stateRegistry.getCurrentState(entityId)

        val currentTime = timeMonitor.currentTime
        val newState = StateObject(
            entityId = entityId,
            state = state,
            rawAttributes = attributes,
            lastChanged = currentTime,
            lastUpdated = currentTime,
            lastReported = currentTime
        )

        stateRegistry.updateState(
            entityId = entityId,
            newState = newState,
            previousState = previousState
        )

        testScope.testScheduler.runCurrent()
        logger.debug { "Emitted new state for entity '$entityId' \nPrevious\n${previousState?.state}  \nNew\n${newState.state}" }
    }

    /**
     * Emits an event, triggering any listening automations.
     * @param eventType The Home Assistant event type (e.g., "call_service")
     * @param data Optional event data payload
     */
    public suspend fun emitEvent(
        eventType: String,
        data: Map<String, Any?> = emptyMap()
    ) {
        val context = Context(
            id = "test-context-${Uuid.generateV7()}",
            parentId = null,
            userId = null
        )

        val eventObject = EventObject(
            eventType = eventType,
            data = data,
            timeFired = timeMonitor.currentTime,
            origin = "Test",
            context = context
        )

        eventRegistry.emitEvent(eventObject)
        testScope.testScheduler.runCurrent()
        logger.debug { "Emitting event '$eventType' with data $data" }
    }

    /**
     * Advances the test clock by the specified duration.
     */
    public suspend fun advanceClock(duration: Duration) {
        timeMonitor.advance(duration)

        testScope.testScheduler.advanceTimeBy(duration)
        testScope.testScheduler.runCurrent()

        logger.debug { "Advanced clock by $duration" }
    }

    /**
     * Determines if an automation is currently running
     */
    public fun isRunning(automationId: AutomationId): Boolean {
        return automationRegistry.isRunning(automationId)
    }

    /**
     * Clears all recorded events
     */
    public fun clearEvents() {
        recordedEvents.clear()
        logger.debug { "Recorded events cleared" }
    }

    /**
     * Advances the test scheduler until all pending work completes.
     */
    public fun awaitIdle() {
        val beforeMilliseconds = testScope.testScheduler.currentTime
        testScope.testScheduler.advanceUntilIdle()
        val elapsedMilliseconds = testScope.testScheduler.currentTime - beforeMilliseconds

        if (elapsedMilliseconds > 0) {
            timeMonitor.syncClockToScheduler(elapsedMilliseconds.milliseconds)
        }
    }

    /**
     * Suspend until the automation finishes
     */
    public suspend fun awaitCompletion(automationId: AutomationId) {
        automationRegistry.await(automationId)
    }

    /**
     * Cleanup test environment - cancel all automations
     * Call this in test tearDown to prevent uncompleted coroutines
     */
    public suspend fun cleanup() {
        val registeredAutomations = automationRegistry.getAll()
        registeredAutomations.forEach { automation ->
            automationRegistry.unregister(automation.id)
        }
        logger.debug { "Test environment cleaned up (unregistered ${registeredAutomations.size} automation(s))" }
    }

    /**
     * Filters recorded events by type.
     */
    public inline fun <reified T : RecordedEvent> events(): List<T> {
        return recordedEvents.filterIsInstance<T>()
    }

    public companion object {
        /**
         * Creates a test environment with automatic cleanup.
         */
        public suspend fun test(
            testScope: TestScope,
            block: suspend (AutomationTestEnvironment) -> Unit
        ) {
            val environment = AutomationTestEnvironment(testScope)
            try {
                block(environment)
            } finally {
                environment.cleanup()
            }
        }
    }
}