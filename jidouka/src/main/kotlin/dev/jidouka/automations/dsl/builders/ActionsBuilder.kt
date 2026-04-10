package dev.jidouka.automations.dsl.builders

import dev.jidouka.actions.ActionsManager
import dev.jidouka.automations.dsl.AutomationDsl
import dev.jidouka.automations.dsl.TimeAccess
import dev.jidouka.automations.dsl.providers.DefaultTimeExtensionsProvider
import dev.jidouka.automations.dsl.providers.EntityProvider
import dev.jidouka.automations.dsl.providers.RegistryEntityProvider
import dev.jidouka.automations.dsl.providers.TimeExtensionsProvider
import dev.jidouka.automations.dsl.scopes.ActionsScope
import dev.jidouka.automations.dsl.scopes.AutomationsScope
import dev.jidouka.automations.dsl.scopes.LoggingScope
import dev.jidouka.automations.dsl.scopes.TriggeredScope
import dev.jidouka.automations.dsl.triggers.TriggerContext
import dev.jidouka.automations.registry.AutomationRegistry
import dev.jidouka.registry.EntityRegistry
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

@AutomationDsl
public class ActionsBuilder internal constructor(
    triggerContext: TriggerContext,
    automationRegistry: AutomationRegistry,
    private val entityRegistry: EntityRegistry,
    actionsManager: ActionsManager,
    automationId: String,
    clock: Clock,
    timeZone: TimeZone,
) : EntityProvider by RegistryEntityProvider(entityRegistry),
    TimeExtensionsProvider by DefaultTimeExtensionsProvider(timeZone) {

    public val actions: ActionsScope = ActionsScope(actionsManager)
    public val automations: AutomationsScope =
        AutomationsScope(automationRegistry)
    public val triggered: TriggeredScope = TriggeredScope(
        context = triggerContext
    )

    public val time: TimeAccess = TimeAccess(
        clock = clock,
        timeZone = timeZone,
    )

    public val log: LoggingScope = LoggingScope(automationId)
}