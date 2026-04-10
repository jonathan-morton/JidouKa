package dev.jidouka.automations.registry

import dev.jidouka.automations.AutomationContext
import dev.jidouka.automations.AutomationMode
import dev.jidouka.automations.dsl.triggers.TriggerContext
import dev.jidouka.automations.registry.subscription.AutomationId
import dev.jidouka.extensions.isTrue
import dev.jidouka.test.RecordedEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
@Single
internal class AutomationExecutor(
    private val executionScope: CoroutineScope, //Should have a SupervisorJob
    private val eventRecorder: ((RecordedEvent) -> Unit)? = null
) {
    private val runningJobs = ConcurrentHashMap<AutomationId, MutableList<Job>>()
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
        val jobs = runningJobs[registeredAutomation.id]

        val hasRunningJobs = jobs?.any { it.isActive } == true
        if (hasRunningJobs) {
            logger.warn { "Automation '${registeredAutomation.id}' already running in Single mode, skipping trigger" }
            return
        }

        launchAction(
            registeredAutomation = registeredAutomation,
            triggerContext = triggerContext
        )
    }

    private fun runRestart(registeredAutomation: RegisteredAutomation, triggerContext: TriggerContext) {
        val jobs = runningJobs[registeredAutomation.id]

        val cancelledCount = jobs?.count { job ->
            if (job.isActive) {
                job.cancel(CancellationException("Restarted by new trigger"))
                true
            } else false
        } ?: 0

        if (cancelledCount > 0) {
            logger.info { "Automation '${registeredAutomation.id}' restarting, cancelled $cancelledCount running job(s)" }
        }

        jobs?.clear()

        launchAction(registeredAutomation, triggerContext)
    }

    private fun runParallel(registeredAutomation: RegisteredAutomation, maxJobs: Int, triggerContext: TriggerContext) {
        val jobs = runningJobs.computeIfAbsent(registeredAutomation.id) {
            mutableListOf()
        }
        jobs.removeIf { it.isActive.not() }

        val activeCount = jobs.count { it.isActive }
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
        val consumerJob = executionScope.launch(
            createExceptionHandler(registeredAutomation.id, "Queue Consumer")
        ) {
            channel.consumeAsFlow().collect { context ->
                runActionsRecorded(registeredAutomation, context)
            }
        }

        runningJobs.computeIfAbsent(registeredAutomation.id) {
            mutableListOf()
        }.add(consumerJob)

        consumerJob.invokeOnCompletion { throwable ->
            actionQueues.remove(registeredAutomation.id)
            runningJobs[registeredAutomation.id]?.remove(consumerJob)

            eventRecorder ?: return@invokeOnCompletion
            throwable?.let {
                if (throwable !is CancellationException) {
                    eventRecorder(RecordedEvent.AutomationFailed(registeredAutomation.id, throwable))
                }
            }
        }
    }

    fun cancel(automationId: AutomationId): Boolean {
        val cancelledActionCount = runningJobs[automationId]?.count { job ->
            if (job.isActive) {
                job.cancel()
                true
            } else false
        } ?: 0

        runningJobs.remove(automationId)

        runningJobs[automationId]?.removeIf { job ->
            job.isActive.not()
        }

        if (runningJobs[automationId]?.isEmpty().isTrue()) {
            runningJobs.remove(automationId)
        }

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

        val job = executionScope.launch(
            createExceptionHandler(registeredAutomation.id, "Running Action")
        ) {
            runActionsRecorded(registeredAutomation, triggerContext)
        }

        runningJobs.computeIfAbsent(registeredAutomation.id) {
            mutableListOf()
        }.add(job)

        job.invokeOnCompletion {
            runningJobs[registeredAutomation.id]?.remove(job)
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
        val jobs = runningJobs[automationId]
        return jobs?.any { it.isActive } == true
    }

    /**
     * Wait for all instances of an automation to complete.
     * Used for cross-automation coordination.
     *
     * @param automationId The automation to wait for
     */
    suspend fun await(automationId: AutomationId) {
        runningJobs[automationId]?.joinAll()
    }


}