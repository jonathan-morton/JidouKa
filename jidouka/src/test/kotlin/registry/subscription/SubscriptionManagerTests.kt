package dev.jidouka.registry.subscription

import dev.jidouka.BaseUnitTest
import dev.jidouka.automations.dsl.triggers.TriggerMetadata
import dev.jidouka.automations.registry.TriggerKey
import dev.jidouka.automations.registry.subscription.WebSocketSubscriptionManager
import dev.jidouka.client.ConnectionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionManagerTests : BaseUnitTest() {

    private fun entityIds(count: Int) = (1..count).map { "sensor.bench_$it" }.toSet()

    private fun expectedWebSocketRoundTrips(keyCount: Int = LARGE_KEY_COUNT): Int {
        return keyCount / MAX_CONCURRENT_WEBSOCKET_REQUESTS +
                if (keyCount % MAX_CONCURRENT_WEBSOCKET_REQUESTS == 0) {
                    0
                } else {
                    1
                }
    }

    @Test
    fun `subscribing to N keys completes in roughly one round-trip, not N`() = runTestScope {
        val connectionManager = mockk<ConnectionManager>()
        var nextId = 0
        coEvery { connectionManager.subscribeToEntity(any()) } coAnswers {
            delay(websocketRoundTripDelay)
            nextId++
        }

        val manager = WebSocketSubscriptionManager(connectionManager)

        manager.subscribe(TriggerMetadata.StateTrigger(entityIds(LARGE_KEY_COUNT)), automationId = "test")

        assertTrue(
            testScheduler.currentTime <
                    websocketRoundTripDelay.inWholeMilliseconds * (expectedWebSocketRoundTrips(LARGE_KEY_COUNT) + ROUND_TRIP_MARGIN_ROUNDS)
        )
    }

    @Test
    fun `one key failing does not prevent the others from subscribing`() = runTestScope {
        val connectionManager = mockk<ConnectionManager>()
        val failingEntity = "sensor.bad"
        var nextId = 0
        coEvery { connectionManager.subscribeToEntity(failingEntity) } throws IllegalStateException("bad!")
        coEvery { connectionManager.subscribeToEntity(neq(failingEntity)) } coAnswers { nextId++ }

        val manager = WebSocketSubscriptionManager(connectionManager)
        val ids = entityIds(SMALL_KEY_COUNT) + failingEntity

        manager.subscribe(TriggerMetadata.StateTrigger(ids), automationId = "test")

        assertFalse(manager.hasActiveSubscription(failingEntity))
        assertTrue(manager.getAutomations(TriggerKey.Entity(failingEntity)).isEmpty())
    }

    @Test
    fun `two automations sharing a key produce only one underlying subscribe call`() = runTestScope {
        val connectionManager = mockk<ConnectionManager>()
        coEvery { connectionManager.subscribeToEntity(any()) } coAnswers { 1 }

        val manager = WebSocketSubscriptionManager(connectionManager)
        val entityId = "light.kitchen"
        val metadata = TriggerMetadata.StateTrigger(setOf(entityId))

        manager.subscribe(metadata, automationId = "automation_a")
        manager.subscribe(metadata, automationId = "automation_b")

        coVerify(exactly = 1) { connectionManager.subscribeToEntity(entityId) }
        assertEquals(setOf("automation_a", "automation_b"), manager.getAutomations(TriggerKey.Entity(entityId)))
    }

    @Test
    fun `concurrent subscribe requests never exceed the configured bound`() = runTestScope {
        val maxObservedConcurrency = AtomicInteger(0)
        val currentConcurrency = AtomicInteger(0)

        val connectionManager = mockk<ConnectionManager>()
        var nextId = 0
        coEvery { connectionManager.subscribeToEntity(any()) } coAnswers {
            val current = currentConcurrency.incrementAndGet()
            maxObservedConcurrency.updateAndGet { maxOf(it, current) }
            delay(websocketRoundTripDelay)
            currentConcurrency.decrementAndGet()
            nextId++
        }

        val manager = WebSocketSubscriptionManager(connectionManager)
        manager.subscribe(TriggerMetadata.StateTrigger(entityIds(LARGER_KEY_COUNT)), automationId = "test")

        assertTrue(maxObservedConcurrency.get() <= MAX_CONCURRENT_WEBSOCKET_REQUESTS)
    }

    @Test
    fun `resubscribeAll and a concurrent subscribe for the same key never both call subscribeToClient`() =
        runTestScope {
            val connectionManager = mockk<ConnectionManager>()
            var nextId = 0
            coEvery { connectionManager.subscribeToEntity(any()) } coAnswers {
                delay(websocketRoundTripDelay)
                nextId++
            }

            val manager = WebSocketSubscriptionManager(connectionManager)
            val entityId = "light.kitchen"
            val metadata = TriggerMetadata.StateTrigger(setOf(entityId))

            manager.subscribe(metadata, automationId = "automation_a")

            val subscribeJob = launch { manager.subscribe(metadata, automationId = "automation_b") }
            val resubscribeJob = launch { manager.resubscribeAll() }
            subscribeJob.join()
            resubscribeJob.join()

            coVerify(exactly = 2) { connectionManager.subscribeToEntity(entityId) }
        }

    @Test
    fun `unsubscribing from N keys completes in roughly one round-trip, not N`() = runTestScope {
        val connectionManager = mockk<ConnectionManager>()
        coEvery { connectionManager.subscribeToEntity(any()) } coAnswers { 1 }
        coEvery { connectionManager.unsubscribe(any()) } coAnswers { delay(websocketRoundTripDelay) }

        val manager = WebSocketSubscriptionManager(connectionManager)
        val ids = entityIds(LARGE_KEY_COUNT)
        val metadata = TriggerMetadata.StateTrigger(ids)
        manager.subscribe(metadata, automationId = "test")

        val before = testScheduler.currentTime
        manager.unsubscribe(metadata, automationId = "test")
        val elapsed = testScheduler.currentTime - before

        assertTrue(elapsed < websocketRoundTripDelay.inWholeMilliseconds * (expectedWebSocketRoundTrips(LARGE_KEY_COUNT) + ROUND_TRIP_MARGIN_ROUNDS))
    }

    @Test
    fun `one key failing to unsubscribe does not prevent the others from unsubscribing`() = runTestScope {
        val connectionManager = mockk<ConnectionManager>()
        val failingSubscriptionId = 1

        coEvery { connectionManager.subscribeToEntity(any()) } returnsMany (1..SMALL_KEY_COUNT).toList()
        coEvery { connectionManager.unsubscribe(failingSubscriptionId) } throws IllegalStateException("failed!")
        coEvery { connectionManager.unsubscribe(neq(failingSubscriptionId)) } coAnswers { }

        val manager = WebSocketSubscriptionManager(connectionManager)
        val ids = entityIds(SMALL_KEY_COUNT)
        val metadata = TriggerMetadata.StateTrigger(ids)
        manager.subscribe(metadata, automationId = "test")

        manager.unsubscribe(metadata, automationId = "test")

        coVerify(exactly = SMALL_KEY_COUNT) { connectionManager.unsubscribe(any()) }
    }

    @Test
    fun `a failed unsubscribe leaves the subscription tracked as active`() = runTestScope {
        val connectionManager = mockk<ConnectionManager>()
        coEvery { connectionManager.subscribeToEntity(any()) } coAnswers { 1 }
        coEvery { connectionManager.unsubscribe(any()) } throws IllegalStateException("yuck!")

        val manager = WebSocketSubscriptionManager(connectionManager)
        val entityId = "light.kitchen"
        val metadata = TriggerMetadata.StateTrigger(setOf(entityId))
        manager.subscribe(metadata, automationId = "only_automation")

        manager.unsubscribe(metadata, automationId = "only_automation")

        assertTrue(manager.hasActiveSubscription(entityId))
    }

    @Test
    fun `subscribing again after a failed unsubscribe rejoins the existing subscription instead of duplicating it`() =
        runTestScope {
            val connectionManager = mockk<ConnectionManager>()
            coEvery { connectionManager.subscribeToEntity(any()) } coAnswers { 1 }
            coEvery { connectionManager.unsubscribe(any()) } throws IllegalStateException("lame!")

            val manager = WebSocketSubscriptionManager(connectionManager)
            val entityId = "light.kitchen"
            val metadata = TriggerMetadata.StateTrigger(setOf(entityId))

            manager.subscribe(metadata, automationId = "automation_a")
            manager.unsubscribe(metadata, automationId = "automation_a")
            manager.subscribe(metadata, automationId = "automation_b")

            coVerify(exactly = 1) { connectionManager.subscribeToEntity(entityId) }
            assertEquals(setOf("automation_b"), manager.getAutomations(TriggerKey.Entity(entityId)))
        }

    @Test
    fun `concurrent unsubscribe requests never exceed the configured bound`() = runTestScope {
        val maxObservedConcurrency = AtomicInteger(0)
        val currentConcurrency = AtomicInteger(0)

        val connectionManager = mockk<ConnectionManager>()
        coEvery { connectionManager.subscribeToEntity(any()) } coAnswers { 1 }
        coEvery { connectionManager.unsubscribe(any()) } coAnswers {
            val current = currentConcurrency.incrementAndGet()
            maxObservedConcurrency.updateAndGet { maxOf(it, current) }
            delay(websocketRoundTripDelay)
            currentConcurrency.decrementAndGet()
        }

        val manager = WebSocketSubscriptionManager(connectionManager)
        val ids = entityIds(LARGER_KEY_COUNT)
        val metadata = TriggerMetadata.StateTrigger(ids)
        manager.subscribe(metadata, automationId = "test")

        manager.unsubscribe(metadata, automationId = "test")

        assertTrue(maxObservedConcurrency.get() <= MAX_CONCURRENT_WEBSOCKET_REQUESTS)
    }

    @Test
    fun `resubscribeAll and a concurrent unsubscribe of the last automation for a key never race`() = runTestScope {
        val connectionManager = mockk<ConnectionManager>()
        coEvery { connectionManager.subscribeToEntity(any()) } coAnswers { delay(websocketRoundTripDelay); 1 }
        coEvery { connectionManager.unsubscribe(any()) } coAnswers { delay(websocketRoundTripDelay) }

        val manager = WebSocketSubscriptionManager(connectionManager)
        val entityId = "light.kitchen"
        val metadata = TriggerMetadata.StateTrigger(setOf(entityId))
        manager.subscribe(metadata, automationId = "only_automation")

        val unsubscribeJob = launch { manager.unsubscribe(metadata, automationId = "only_automation") }
        val resubscribeJob = launch { manager.resubscribeAll() }
        unsubscribeJob.join()
        resubscribeJob.join()

        assertTrue(manager.getAutomations(TriggerKey.Entity(entityId)).isEmpty())
    }

    private companion object {
        private val websocketRoundTripDelay = 10.milliseconds
        private const val MAX_CONCURRENT_WEBSOCKET_REQUESTS = 32
        private const val LARGE_KEY_COUNT = 100
        private const val LARGER_KEY_COUNT = 200
        private const val SMALL_KEY_COUNT = 10
        private const val ROUND_TRIP_MARGIN_ROUNDS = 1
    }
}