package dev.jidouka.automations.registry

import dev.jidouka.automations.AutomationContext
import dev.jidouka.automations.AutomationMode
import dev.jidouka.automations.dsl.triggers.TriggerContext
import dev.jidouka.automations.registry.subscription.AutomationId
import dev.jidouka.test.RecordedEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

@Single
internal class AutomationExecutor(
    private val executionScope: CoroutineScope, //Should have a SupervisorJob
    private val eventRecorder: ((RecordedEvent) -> Unit)? = null
) {
    /**
     * Supervisor parent of the jobs running each automation's actions: action launches and the queue consumer.
     * Trigger collection is in [collectorJobs].
     */
    private val runningJobs = ConcurrentHashMap<AutomationId, Job>()
    private val collectorJobs = ConcurrentHashMap<AutomationId, Job>()
    private val actionQueues = ConcurrentHashMap<AutomationId, Channel<TriggerContext>>()

    private val logger = KotlinLogging.logger {}

    private fun createExceptionHandler(
        automationId: AutomationId,
        operation: String
    ) = CoroutineExceptionHandler { _, exception ->
        if (exception !is CancellationException) {
            logger.error(exception) { "Automation '$automationId' error in $operation" }
        }
    }

    private fun getParentJob(automationId: AutomationId): Job {
        return runningJobs.computeIfAbsent(automationId) {
            SupervisorJob(executionScope.coroutineContext.job)
        }
    }

    private fun activeActionJobs(automationId: AutomationId): Sequence<Job> {
        return runningJobs[automationId]?.children?.filter { it.isActive }
            ?: emptySequence()
    }

    private fun cancelActionJobs(
        automationId: AutomationId,
        cause: CancellationException? = null
    ): Int {
        val cancelledCount = activeActionJobs(automationId).count()
        runningJobs[automationId]?.cancelChildren(cause)
        return cancelledCount
    }

    fun execute(
        registeredAutomation: RegisteredAutomation,
    ) {
        val collectorJob =
            executionScope.launch(createExceptionHandler(registeredAutomation.id, "Trigger Flow Collection")) {
                logger.info { "Beginning trigger flow collection for ${registeredAutomation.id}" }

                if (registeredAutomation.runOnStartup) {
                    val startupContext = withContext(AutomationContext(registeredAutomation.id)) {
                        registeredAutomation.evaluateStartup()
                    }

                    if (startupContext != null) {
                        try {
                            handleTrigger(registeredAutomation, startupContext)
                        } catch (e: CancellationException) {
                            logger.warn { "Start up automation ${registeredAutomation.id} cancelled" }
                            throw e
                        } catch (e: Exception) {
                            logger.error(e) { "Startup evaluation failure for ${registeredAutomation.id}" }
                        }
                    }
                }

                registeredAutomation.triggerFlow.collect { context ->
                    try {
                        handleTrigger(registeredAutomation, context)
                    } catch (exception: CancellationException) {
                        logger.warn { "Automation '${registeredAutomation.id}' cancelled during trigger flow collection" }
                        throw exception
                    } catch (_: Exception) {
                        // Already logged by handleTrigger
                    }
                }
            }

        collectorJobs[registeredAutomation.id] = collectorJob

        collectorJob.invokeOnCompletion {
            collectorJobs.remove(registeredAutomation.id)
        }
    }

    private suspend fun handleTrigger(
        registeredAutomation: RegisteredAutomation,
        triggerContext: TriggerContext
    ) {
        val conditionsPassed: Boolean = try {
            logger.debug { "Automation '${registeredAutomation.id}' triggered" }

            eventRecorder?.invoke(RecordedEvent.TriggerFired(registeredAutomation.id))

            registeredAutomation.evaluateConditions?.let {
                withContext(AutomationContext(registeredAutomation.id)) {
                    it(triggerContext)
                }
            } ?: true

        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.error(exception) { "Automation '${registeredAutomation.id}' failed during trigger handling" }
            return
        }

        if (conditionsPassed.not()) {
            logger.debug { "Automation '${registeredAutomation.id}' conditions not met, skipping trigger" }
            eventRecorder?.invoke(RecordedEvent.ConditionFailed(registeredAutomation.id))
            return
        }

        when (val mode = registeredAutomation.mode) {
            is AutomationMode.Single -> runSingle(registeredAutomation, triggerContext)
            is AutomationMode.Restart -> runRestart(registeredAutomation, triggerContext)
            is AutomationMode.Queued -> runQueued(registeredAutomation, mode.max, triggerContext)
            is AutomationMode.Parallel -> runParallel(registeredAutomation, mode.maximum, triggerContext)
        }
    }

    private fun runSingle(registeredAutomation: RegisteredAutomation, triggerContext: TriggerContext) {
        val hasActiveActionJobs = activeActionJobs(registeredAutomation.id).any()
        if (hasActiveActionJobs) {
            logger.warn { "Automation '${registeredAutomation.id}' already running in Single mode, skipping trigger" }
            return
        }

        launchAction(
            registeredAutomation = registeredAutomation,
            triggerContext = triggerContext
        )
    }

    private fun runRestart(registeredAutomation: RegisteredAutomation, triggerContext: TriggerContext) {
        val cancelledCount = cancelActionJobs(
            automationId = registeredAutomation.id,
            cause = CancellationException("Restarted by new trigger")
        )

        if (cancelledCount > 0) {
            logger.info { "Automation '${registeredAutomation.id}' restarting, cancelled $cancelledCount running job(s)" }
        }

        launchAction(registeredAutomation, triggerContext)
    }

    private fun runParallel(registeredAutomation: RegisteredAutomation, maxJobs: Int, triggerContext: TriggerContext) {
        val activeCount = activeActionJobs(registeredAutomation.id).count()

        if (activeCount >= maxJobs) {
            logger.warn { "Automation '${registeredAutomation.id}' parallel limit reached ($activeCount/$maxJobs), skipping trigger" }
            return
        }

        launchAction(registeredAutomation, triggerContext)
    }

    private fun runQueued(registeredAutomation: RegisteredAutomation, maxQueued: Int, triggerContext: TriggerContext) {
        val queueChannel = actionQueues.computeIfAbsent(registeredAutomation.id) {
            Channel<TriggerContext>(
                capacity = maxQueued
            ).also { channel ->
                startQueueConsumer(registeredAutomation, channel)
            }
        }

        val result = queueChannel.trySend(triggerContext)
        when {
            result.isSuccess -> Unit
            result.isFailure -> logger.warn {
                "Automation '${registeredAutomation.id}' queue full (max: $maxQueued), trigger dropped"
            }

            result.isClosed -> {
                val exception = result.exceptionOrNull() ?: return
                logger.error(exception) {
                    "Automation '${registeredAutomation.id}' queue channel closed unexpectedly"
                }
            }
        }
    }

    private fun startQueueConsumer(
        registeredAutomation: RegisteredAutomation,
        channel: Channel<TriggerContext>
    ) {
        val context =
            getParentJob(registeredAutomation.id) + createExceptionHandler(registeredAutomation.id, "Queue Consumer")
        val consumerJob = executionScope.launch(
            context = context
        ) {
            channel.consumeAsFlow().collect { triggerContext ->
                runActionsRecorded(registeredAutomation, triggerContext)
            }
        }

        consumerJob.invokeOnCompletion { throwable ->
            actionQueues.remove(registeredAutomation.id, channel)
            eventRecorder ?: return@invokeOnCompletion
            throwable?.let {
                if (throwable !is CancellationException) {
                    eventRecorder(RecordedEvent.AutomationFailed(registeredAutomation.id, throwable))
                }
            }
        }
    }

    fun cancel(automationId: AutomationId): Boolean {
        val cancelledActionCount = cancelActionJobs(automationId)

        actionQueues.remove(automationId)?.close()

        if (cancelledActionCount > 0) {
            logger.info { "Cancelled $cancelledActionCount action(s) for automation '$automationId'" }
        }

        return cancelledActionCount > 0
    }

    fun shutdown(automationId: AutomationId): Boolean {
        val collectorJob = collectorJobs.remove(automationId)
        val wasCollectorActive = collectorJob?.isActive == true
        if (wasCollectorActive) {
            collectorJob.cancel()
            return true
        }
        return false
    }

    private fun launchAction(registeredAutomation: RegisteredAutomation, triggerContext: TriggerContext) {
        logger.debug { "Launching action for automation '${registeredAutomation.id}' (mode: ${registeredAutomation.mode})" }
        val context =
            getParentJob(registeredAutomation.id) + createExceptionHandler(registeredAutomation.id, "Queue Consumer")
        executionScope.launch(
            context = context
        ) {
            runActionsRecorded(registeredAutomation, triggerContext)
        }
    }

    private suspend fun runActionsRecorded(
        registeredAutomation: RegisteredAutomation,
        triggerContext: TriggerContext
    ) {
        try {
            runActions(registeredAutomation, triggerContext)
            eventRecorder?.invoke(RecordedEvent.AutomationCompleted(registeredAutomation.id, cancelled = false))
        } catch (cancellationException: CancellationException) {
            eventRecorder?.invoke(RecordedEvent.AutomationCompleted(registeredAutomation.id, cancelled = true))
            throw cancellationException
        } catch (exception: Exception) {
            logger.error(exception) { "Automation '${registeredAutomation.id}' action failed" }
            eventRecorder?.invoke(RecordedEvent.AutomationFailed(registeredAutomation.id, exception))
        }
    }

    private suspend fun runActions(
        registeredAutomation: RegisteredAutomation,
        triggerContext: TriggerContext
    ) {
        logger.info { "Automation '${registeredAutomation.id}' starting actions" }
        withContext(AutomationContext(registeredAutomation.id)) {
            registeredAutomation.executeActions(triggerContext)
        }
        logger.info { "Automation '${registeredAutomation.id}' completed actions" }
    }

    /**
     * Check if an automation has any active jobs.
     *
     * @param automationId The automation to check
     * @return true if any jobs are active
     */
    fun isRunning(automationId: AutomationId): Boolean {
        return activeActionJobs(automationId).any()
    }

    /**
     * Wait for all instances of an automation to complete.
     * Used for cross-automation coordination.
     *
     * @param automationId The automation to wait for
     */
    suspend fun await(automationId: AutomationId) {
        runningJobs[automationId]?.children?.toList()?.joinAll()
    }
}