package dev.jidouka.api

import dev.jidouka.api.automation.AutomationRegistrationScope
import dev.jidouka.client.ConnectionManager
import dev.jidouka.di.JidoukaModule
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import org.koin.core.annotation.KoinApplication
import kotlin.time.Duration.Companion.seconds

/**
 * The application that runs and manages the automations and lifecycle of the application
 */
@KoinApplication(modules = [JidoukaModule::class])
public class JidoukaApplication internal constructor(
    private val connectionManager: ConnectionManager,
    private val jidoukaScope: CoroutineScope,
    private val registrationScope: AutomationRegistrationScope,
    private val automationsBlock: (suspend AutomationRegistrationScope.() -> Unit)?
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Starts the application
     * Connects to Home Assistant, initializes the state of the application, and registers automations
     */
    public suspend fun start() {
        logger.info { "Starting Jidouka…" }

        try {
            logger.info { "Connecting to Home Assistant…" }
            connectionManager.connect(jidoukaScope)
            delay(5.seconds)
            logger.info { "Connected to Home Assistant" }

            logger.info { "Initializing state entities…" }
            connectionManager.initializeStates()
            logger.info { "State entities initialized" }

            logger.info { "Registering automations…" }
            automationsBlock?.invoke(registrationScope)
            logger.info { "Automations registered" }

            logger.info { "Jidouka application started" }

            connectionManager.run()
        } catch (exception: Exception) {
            logger.error(exception) { "Error starting Jidouka application" }
            throw exception
        } finally {
            shutdown()
        }
    }

    /**
     * Shutdown Jidouka
     * Cancels automations and automation monitoring and disconnects from Home Assistant
     */
    public fun shutdown() {
        logger.info { "Shutting down Jidouka application…" }

        try {
            logger.debug { "Cancelling Jidouka scope…" }
            jidoukaScope.cancel()

            logger.debug { "Disconnecting from Home Assistant…" }
            connectionManager.disconnect()
        } catch (exception: Exception) {
            logger.error(exception) { "Error shutting down Jidouka application" }
        }
    }
}