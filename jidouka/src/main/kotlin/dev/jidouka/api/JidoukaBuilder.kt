package dev.jidouka.api

import dev.jidouka.api.automation.AutomationRegistrationScope
import dev.jidouka.client.ConnectionManager
import dev.jidouka.configuration.HomeAssistantConfiguration
import dev.jidouka.configuration.JidoukaConfiguration
import dev.jidouka.di.AppModule
import dev.jidouka.di.NetworkModule
import dev.jidouka.di.createConfigurationModule
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import org.koin.logger.slf4jLogger
import org.koin.plugin.module.dsl.startKoin

/**
 * Builder to define the configuration of the application and registers the automations that will run
 */
@JidoukaDSL
public class JidoukaBuilder internal constructor() {
    private val logger = KotlinLogging.logger {}

    public var haConfiguration: HomeAssistantConfiguration? = null
    public var jidoukaConfiguration: JidoukaConfiguration = JidoukaConfiguration()
    private var automationsBlock: (suspend AutomationRegistrationScope.() -> Unit)? = null

    public fun automations(block: suspend AutomationRegistrationScope.() -> Unit) {
        automationsBlock = block
    }

    internal suspend fun build(): JidoukaApplication {
        val haConfiguration = this.haConfiguration
            ?: error("Home Assistant configuration required")

        JidoukaConfiguration.configureLogging(jidoukaConfiguration)
        logger.debug { "Jidouka configured: $jidoukaConfiguration" }

        logger.info { "Building Jidouka application" }

        val koinApplication = startKoin<JidoukaApplication> {
            slf4jLogger()
            modules(
                AppModule,
                NetworkModule,
                createConfigurationModule(
                    homeAssistantConfiguration = haConfiguration,
                    jidoukaConfiguration = jidoukaConfiguration
                ),
            )
        }

        logger.debug { "Koin started: $koinApplication" }

        val koin = koinApplication.koin

        val connectionManager: ConnectionManager = koin.get()
        val registrationScope: AutomationRegistrationScope = koin.get()

        val currentCoroutineContext = currentCoroutineContext()
        val jidoukaScope = CoroutineScope(
            currentCoroutineContext +
                    SupervisorJob(currentCoroutineContext[Job]) +
                    Dispatchers.IO +
                    CoroutineExceptionHandler { _, throwable ->
                        logger.error(throwable) { "Uncaught exception in Jidouka scope: ${throwable.message}" }
                    }
        )

        return JidoukaApplication(
            connectionManager = connectionManager,
            jidoukaScope = jidoukaScope,
            automationsBlock = automationsBlock,
            registrationScope = registrationScope,
        )
    }
}