package dev.jidouka.automations.dsl.builders

import dev.jidouka.automations.dsl.AutomationDsl
import dev.jidouka.automations.dsl.TimeAccess
import dev.jidouka.automations.dsl.providers.DefaultTimeExtensionsProvider
import dev.jidouka.automations.dsl.providers.EntityProvider
import dev.jidouka.automations.dsl.providers.RegistryEntityProvider
import dev.jidouka.automations.dsl.providers.TimeExtensionsProvider
import dev.jidouka.automations.dsl.scopes.AutomationsQuery
import dev.jidouka.automations.dsl.scopes.AutomationsScope
import dev.jidouka.automations.dsl.scopes.LoggingScope
import dev.jidouka.automations.dsl.scopes.TriggeredScope
import dev.jidouka.automations.dsl.triggers.TriggerContext
import dev.jidouka.automations.registry.AutomationRegistry
import dev.jidouka.registry.EntityRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@AutomationDsl
public class ConditionsBuilder internal constructor(
    triggerContext: TriggerContext,
    automationRegistry: AutomationRegistry,
    private val entityRegistry: EntityRegistry,
    automationId: String,
    clock: Clock,
    timeZone: TimeZone,
) : EntityProvider by RegistryEntityProvider(entityRegistry),
    TimeExtensionsProvider by DefaultTimeExtensionsProvider(timeZone) {
    private val conditions = mutableListOf<suspend () -> Boolean>()
    private val logger = KotlinLogging.logger {}

    public val automations: AutomationsQuery = AutomationsScope(automationRegistry)
    public val triggered: TriggeredScope = TriggeredScope(
        context = triggerContext
    )

    public val time: TimeAccess = TimeAccess(
        clock = clock,
        timeZone = timeZone,
    )

    public val log: LoggingScope = LoggingScope(automationId)

    public fun condition(predicate: suspend () -> Boolean) {
        conditions.add(predicate)
    }

    public operator fun (suspend () -> Boolean).unaryPlus() {
        condition(this)
    }


    internal fun build(): List<suspend () -> Boolean> {
        return conditions.toList()
    }

    internal suspend fun evaluate(): Boolean {
        val conditions = build()

        if (conditions.isEmpty()) {
            logger.debug { "No conditions to evaluate, returning true" }
            return true
        }

        logger.debug { "Evaluating ${conditions.size} condition(s)" }

        try {
            val result = if (conditions.size == 1) {
                conditions.first().invoke()
            } else {
                withTimeoutOrNull(conditionsEvaluationTimeOut) {
                    conditions
                        .asFlow()
                        .flatMapMerge { condition ->
                            flow {
                                try {
                                    emit(condition())
                                } catch (exception: Exception) {
                                    logger.warn(exception) { "Condition threw exception, treating as false" }
                                    emit(false)
                                }
                            }
                        }.first { it }
                } ?: run {
                    logger.warn { "Condition evaluation timed out after ${conditionsEvaluationTimeOut.inWholeSeconds}s" }
                    false
                }
            }

            logger.debug { "Conditions evaluated to: $result" }
            return result
        } catch (e: CancellationException) {
            throw e
        } catch (exception: Exception) {
            logger.error(exception) { "Unexpected exception during condition evaluation" }
            return false
        }
    }

    private companion object {
        private val conditionsEvaluationTimeOut = 30.seconds
    }
}