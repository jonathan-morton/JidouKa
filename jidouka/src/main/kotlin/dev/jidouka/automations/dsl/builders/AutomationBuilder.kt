package dev.jidouka.automations.dsl.builders

import dev.jidouka.automations.Automation
import dev.jidouka.automations.AutomationMode
import dev.jidouka.automations.dsl.AutomationDsl
import dev.jidouka.automations.dsl.providers.DefaultTimeExtensionsProvider
import dev.jidouka.automations.dsl.providers.EntityProvider
import dev.jidouka.automations.dsl.providers.RegistryEntityProvider
import dev.jidouka.automations.dsl.providers.TimeExtensionsProvider
import dev.jidouka.registry.EntityRegistry
import kotlinx.datetime.TimeZone

/**
 * Block to build an Automation
 * @property name See [Automation.name]
 * @property description See [Automation.description]
 * @property runOnStartup See [Automation.runOnStartup]
 * TODO : define other parameters
 */
@AutomationDsl
public class AutomationBuilder internal constructor(
    private val id: String,
    private val mode: AutomationMode,
    entityRegistry: EntityRegistry,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
) : EntityProvider by RegistryEntityProvider(entityRegistry),
    TimeExtensionsProvider by DefaultTimeExtensionsProvider(timeZone) {

    public var name: String = id
    public var description: String? = null
    public var runOnStartup: Boolean = false

    private var _triggers: (TriggersBuilder.() -> Unit)? = null
    private var _actions: (suspend ActionsBuilder.() -> Unit)? = null
    private var _conditions: (ConditionsBuilder.() -> Unit)? = null

    public fun triggers(block: TriggersBuilder.() -> Unit) {
        check(_triggers == null) { "triggers block already defined — only one triggers block per automation" }
        _triggers = block
    }

    public fun conditions(block: ConditionsBuilder.() -> Unit) {
        check(_conditions == null) { "conditions block already defined — only one conditions block per automation" }
        _conditions = block
    }

    public fun actions(block: suspend ActionsBuilder.() -> Unit) {
        check(_actions == null) { "actions block already defined - only one actions block per automation" }
        _actions = block
    }


    @Throws(IllegalStateException::class)
    internal fun toAutomation(): Automation {
        val triggersBlock = _triggers
            ?: error("Automation id:$id name:$name is missing required 'triggers' block.")

        val actionsBlock = _actions
            ?: error("Automation id:$id name:$name is missing required 'actions' block.")

        val conditionsBlock = _conditions

        return object : Automation(
            id = id,
            mode = mode
        ) {
            override val name: String = this@AutomationBuilder.name
            override val description: String? = this@AutomationBuilder.description
            override val runOnStartup: Boolean = this@AutomationBuilder.runOnStartup
            override val triggers: TriggersBuilder.() -> Unit = triggersBlock
            override val actions: suspend ActionsBuilder.() -> Unit = actionsBlock
            override val conditions: (ConditionsBuilder.() -> Unit)? = conditionsBlock
        }
    }
}