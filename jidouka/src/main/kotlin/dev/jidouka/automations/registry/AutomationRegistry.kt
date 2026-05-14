package dev.jidouka.automations.registry

import dev.jidouka.actions.ActionsManager
import dev.jidouka.automations.Automation
import dev.jidouka.automations.DeferredBuilderAutomation
import dev.jidouka.automations.dsl.builders.ActionsBuilder
import dev.jidouka.automations.dsl.builders.AutomationBuilder
import dev.jidouka.automations.dsl.builders.ConditionsBuilder
import dev.jidouka.automations.dsl.builders.TriggersBuilder
import dev.jidouka.automations.dsl.triggers.TriggerContext
import dev.jidouka.automations.registry.subscription.AutomationId
import dev.jidouka.automations.registry.subscription.SubscriptionManager
import dev.jidouka.monitors.TimeMonitor
import dev.jidouka.registry.EntityRegistry
import dev.jidouka.registry.EventRegistry
import dev.jidouka.registry.WebhookRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal typealias AutomationIdKey = String

@OptIn(ExperimentalTime::class)
@Single
internal class AutomationRegistry(
    private val subscriptionManager: SubscriptionManager,
    private val timeMonitor: TimeMonitor,
    private val executor: AutomationExecutor,
    private val entityRegistry: EntityRegistry,
    private val eventRegistry: EventRegistry,
    private val webhookRegistry: WebhookRegistry,
    private val actionsManager: ActionsManager,
    private val clock: Clock,
    private val timeZone: TimeZone
) {
    private val automations = ConcurrentHashMap<AutomationIdKey, RegisteredAutomation>()
    private val mutex = Mutex()

    private val logger = KotlinLogging.logger {}


    suspend fun register(automation: Automation) {
        logger.info { "Registering automation '${automation.id}' (mode: ${automation.mode})" }

        require(automations.contains(automation.id).not()) {
            "Automation '${automation.id}' is already registered"
        }

        val registeredAutomation = automation.toRegisteredAutomation()

        mutex.withLock {
            logger.debug { "Subscribing automation '${automation.id}' to ${registeredAutomation.triggerMetadata.size} trigger(s)" }
            registeredAutomation.triggerMetadata.forEach { metadata ->
                subscriptionManager.subscribe(metadata, automation.id)
            }
            automations[automation.id] = registeredAutomation
            logger.debug { "Automation '${automation.id}' stored in registry" }
        }

        logger.debug { "Starting executor for automation '${automation.id}'" }
        executor.execute(registeredAutomation)

        logger.info { "Automation '${automation.id}' registered successfully" }
    }

    suspend fun unregister(automationId: String): RegisteredAutomation? {
        logger.info { "Unregistering automation '$automationId'" }

        return mutex.withLock {
            val removedAutomation = automations.remove(automationId)

            if (removedAutomation == null) {
                logger.warn { "Automation '$automationId' not found in registry, cannot unregister" }
                return@withLock null
            }

            logger.debug { "Shutting down executor for automation '$automationId'" }
            executor.shutdown(automationId)

            logger.debug { "Unsubscribing automation '$automationId' from ${removedAutomation.triggerMetadata.size} trigger(s)" }
            removedAutomation.triggerMetadata.forEach { metadata ->
                subscriptionManager.unsubscribe(metadata, automationId)
            }

            logger.info { "Automation '$automationId' unregistered successfully" }
            removedAutomation
        }
    }

    /**
     * Get all registered automations.
     */
    fun getAll(): List<RegisteredAutomation> = automations.values.toList()

    /**
     * Get automation by ID.
     */
    fun get(automationId: String): RegisteredAutomation? = automations[automationId]

    /**
     * Cancel a running automation.
     * Used by other automations for coordination.
     */
    fun cancel(automationId: String): Boolean {
        return executor.cancel(automationId)
    }

    fun isRunning(automationId: AutomationId): Boolean {
        return executor.isRunning(automationId)
    }

    suspend fun await(automationId: AutomationId) {
        executor.await(automationId)
    }

    private fun Automation.toRegisteredAutomation(): RegisteredAutomation {
        if (this is DeferredBuilderAutomation) {
            val builder = AutomationBuilder(
                id = id,
                mode = mode,
                entityRegistry = entityRegistry,
                timeZone = timeZone
            )
            builder.builderBlock()
            return builder.toAutomation().toRegisteredAutomation()
        }
        val triggersBuilder = TriggersBuilder(
            clockFlow = timeMonitor.clock,
            clock = clock,
            entityRegistry = entityRegistry,
            eventRegistry = eventRegistry,
            automationId = this.id,
            timeZone = timeZone,
            webhookRegistry = webhookRegistry
        )
        triggersBuilder.triggers()

        val triggersFlow = triggersBuilder.buildFlow()
        val triggersMetadata = triggersBuilder.buildMetadata()

        val startupEvaluator: suspend () -> TriggerContext? = {
            triggersBuilder.evaluateCurrentState()
        }

        val conditionsBlock = this.conditions
        val actionsBlock = this.actions

        val boundConditions: (suspend (TriggerContext) -> Boolean)? = conditionsBlock?.let { block ->
            { triggerContext: TriggerContext ->
                val builder = ConditionsBuilder(
                    triggerContext = triggerContext,
                    automationRegistry = this@AutomationRegistry,
                    entityRegistry = entityRegistry,
                    automationId = this.id,
                    clock = clock,
                    timeZone = timeZone,
                )

                builder.block()
                builder.evaluate()
            }
        }

        val boundActions: suspend (TriggerContext) -> Unit = { triggerContext: TriggerContext ->
            val builder = ActionsBuilder(
                triggerContext = triggerContext,
                automationRegistry = this@AutomationRegistry,
                entityRegistry = entityRegistry,
                actionsManager = actionsManager,
                automationId = this.id,
                clock = clock,
                timeZone = timeZone,
            )

            builder.actionsBlock()
        }

        return RegisteredAutomation(
            id = this.id,
            mode = this.mode,
            name = this.name,
            description = this.description,
            runOnStartup = this.runOnStartup,
            triggerFlow = triggersFlow,
            triggerMetadata = triggersMetadata,
            evaluateStartup = startupEvaluator,
            evaluateConditions = boundConditions,
            executeActions = boundActions,
        )
    }
}