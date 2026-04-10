package dev.jidouka.di

import dev.jidouka.automations.registry.subscription.SubscriptionManager
import dev.jidouka.client.ConnectionManager
import dev.jidouka.client.HomeAssistantConnectionManager
import dev.jidouka.client.configureSockets
import dev.jidouka.configuration.HomeAssistantConfiguration
import dev.jidouka.configuration.JidoukaConfiguration
import dev.jidouka.configureSerialization
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.TimeZone
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.dsl.module
import kotlin.time.Clock


@Module
@ComponentScan("dev.jidouka")
internal class JidoukaModule

internal val AppModule = module {
    single<CoroutineScope> {
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            val logger = KotlinLogging.logger {}
            logger.error(throwable) { "Uncaught exception in application scope: ${throwable.message}" }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    }

    single<Clock> { Clock.System }
}

internal val NetworkModule = module {
    single {
        HttpClient(CIO) {
            configureSockets()
            configureSerialization()
        }
    }

    single<ConnectionManager> {
        val koin = getKoin()
        HomeAssistantConnectionManager(
            webSocketClient = get(),
            restClient = get(),
            stateRegistry = get(),
            configuration = get(),
            httpClient = get(),
            onReconnected = { koin.get<SubscriptionManager>().resubscribeAll() }
        )
    }
}

internal fun createConfigurationModule(
    homeAssistantConfiguration: HomeAssistantConfiguration,
    jidoukaConfiguration: JidoukaConfiguration = JidoukaConfiguration()
) = module {
    single { homeAssistantConfiguration }
    single { jidoukaConfiguration }
    single<TimeZone> { jidoukaConfiguration.timeZone }
}