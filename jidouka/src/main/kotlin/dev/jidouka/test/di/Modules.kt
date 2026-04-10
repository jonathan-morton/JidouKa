package dev.jidouka.test.di

import dev.jidouka.actions.ActionsManager
import dev.jidouka.automations.registry.AutomationExecutor
import dev.jidouka.automations.registry.subscription.SubscriptionManager
import dev.jidouka.monitors.TimeMonitor
import dev.jidouka.registry.EventRegistry
import dev.jidouka.registry.StateRegistry
import dev.jidouka.test.RecordedEvent
import dev.jidouka.test.actions.RecordingActionsManager
import dev.jidouka.test.automations.registry.subscription.TestSubscriptionManager
import dev.jidouka.test.monitors.TestTimeMonitor
import dev.jidouka.test.registry.InMemoryEventRegistry
import dev.jidouka.test.registry.InMemoryStateRegistry
import dev.jidouka.test.usecases.TestEnsureEntitySubscribedAndCurrentUseCase
import dev.jidouka.usecases.EnsureEntitySubscribedAndCurrentUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.TimeZone
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single
import kotlin.time.Clock
import kotlin.time.Instant

internal fun testModule(
    testScope: TestScope,
    timeZone: TimeZone = TimeZone.UTC,
    onEvent: (RecordedEvent) -> Unit
): Module = module {
    single<CoroutineScope> {
        CoroutineScope(StandardTestDispatcher(testScope.testScheduler) + SupervisorJob())
    }

    single<Clock> {
        val testTimeMonitor: TestTimeMonitor = get()
        object : Clock {
            override fun now(): Instant = testTimeMonitor.currentTime
        }
    }

    single<TimeZone> { timeZone }

    single<InMemoryEventRegistry>() bind EventRegistry::class
    single<InMemoryStateRegistry>() bind StateRegistry::class

    single<TestSubscriptionManager>() bind SubscriptionManager::class
    single {
        RecordingActionsManager(jsonManager = get(), onActionCalled = onEvent)
    } bind ActionsManager::class

    single { AutomationExecutor(executionScope = get(), eventRecorder = onEvent) }

    single<TestTimeMonitor>() bind TimeMonitor::class

    //region use cases
    single<TestEnsureEntitySubscribedAndCurrentUseCase>() bind EnsureEntitySubscribedAndCurrentUseCase::class
    //endregion
}