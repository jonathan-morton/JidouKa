package dev.jidouka.api

import dev.jidouka.automations.Automation
import dev.jidouka.automations.AutomationMode
import dev.jidouka.automations.dsl.builders.AutomationBuilder
import dev.jidouka.registry.DomainParserRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import dev.jidouka.automations.automation as internalAutomation

/**
 * The main entry point for creating and starting a Jidouka application
 */
public object Jidouka {
    private val logger = KotlinLogging.logger {}

    /**
     * Initialize the Jidouka application with a configuration builder
     * Configure the application and register automations with Jidouka
     *
     * @return the JidoukaApplication that will run automation
     */
    public suspend fun initialize(
        configure: JidoukaBuilder.() -> Unit
    ): JidoukaApplication {
        logger.info { "Initializing Jidouka application" }
        DomainParserRegistry.installFromClasspath()

        val builder = JidoukaBuilder()
        builder.configure()

        return builder.build()
    }

    /**
     * Creates an automation outside the initialization block
     * The automation is **unregistered** and requires registration in the initialization block
     */
    public fun automation(
        id: String,
        mode: AutomationMode = AutomationMode.Single,
        block: AutomationBuilder.() -> Unit
    ): Automation {
        return internalAutomation(
            id = id,
            mode = mode,
            block = block
        )
    }
}