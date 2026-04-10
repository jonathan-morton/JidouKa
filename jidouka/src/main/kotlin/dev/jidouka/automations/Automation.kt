package dev.jidouka.automations

import dev.jidouka.automations.dsl.builders.ActionsBuilder
import dev.jidouka.automations.dsl.builders.AutomationBuilder
import dev.jidouka.automations.dsl.builders.ConditionsBuilder
import dev.jidouka.automations.dsl.builders.TriggersBuilder

/**
 * The set of instructions that define which triggers to react to and the actions that should follow
 * Automations have an optional conditions block that can define predicates to ensure when actions in an automation should run
 *
 * @param id The unique identifier for the automation
 * @param mode The run mode of the automation, see [AutomationMode] for the different types
 */
public abstract class Automation(
    public val id: String,
    public val mode: AutomationMode
) {
    /**
     * Human-readable name for the automation
     */
    public open val name: String = id

    /**
     * Description of the automation
     */
    public open val description: String? = null

    /**
     * Boolean that defines whether the automation should wait to be triggered after start up
     * If `runOnStartup` is true, the automation will read the current state of the conditions in the trigger and run if the conditions are met
     * When false, the automation will wait until a trigger is activated from a change after start up
     */
    public open val runOnStartup: Boolean = false

    /**
     * Builder that defines the triggers that will cause the action to run
     * Multiple triggers use OR logic. If ANY trigger is true the automation will run
     */
    public abstract val triggers: TriggersBuilder.() -> Unit

    /**
     * The builder to define the actions that should run
     */
    public abstract val actions: suspend ActionsBuilder.() -> Unit

    /**
     * Builder to add optional checks that are checked after a trigger occurs. Multiple conditions are ORed and the action will run
     * if ANY condition passes.
     */
    public open val conditions: (ConditionsBuilder.() -> Unit)? = null
}

internal class DeferredBuilderAutomation(
    id: String,
    mode: AutomationMode,
    internal val builderBlock: AutomationBuilder.() -> Unit
) : Automation(id, mode) {
    override val triggers: TriggersBuilder.() -> Unit
        get() = error("DeferredBuilderAutomation must be materialized before accessing triggers")
    override val actions: suspend ActionsBuilder.() -> Unit
        get() = error("DeferredBuilderAutomation must be materialized before accessing actions")
}

internal fun automation(
    id: String,
    mode: AutomationMode = AutomationMode.Single,
    block: AutomationBuilder.() -> Unit
): Automation = DeferredBuilderAutomation(
    id,
    mode,
    block
)