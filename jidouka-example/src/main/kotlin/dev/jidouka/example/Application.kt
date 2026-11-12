package dev.jidouka.example

import dev.jidouka.api.Jidouka
import dev.jidouka.common.configuration.HomeAssistantConfiguration
import dev.jidouka.common.configuration.HomeAssistantEnvironmentVariables
import dev.jidouka.configuration.JidoukaConfiguration
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DayOfWeek
import kotlin.time.Duration.Companion.minutes

private val logger = KotlinLogging.logger {}

fun main() = runBlocking {
    logger.info { "Starting Jidouka Example Application" }

    // Load configuration from environment variables
    val haHost = System.getenv(HomeAssistantEnvironmentVariables.HOST) ?: run {
        logger.warn { "${HomeAssistantEnvironmentVariables.HOST} not set, using default" }
        HomeAssistantConfiguration.DEFAULT_HOST
    }

    val haPort = System.getenv(HomeAssistantEnvironmentVariables.PORT)?.toIntOrNull() ?: run {
        logger.warn { "${HomeAssistantEnvironmentVariables.PORT} not set or invalid, using default" }
        HomeAssistantConfiguration.DEFAULT_PORT
    }

    val haAccessToken = System.getenv(HomeAssistantEnvironmentVariables.ACCESS_TOKEN) ?: run {
        logger.error { "${HomeAssistantEnvironmentVariables.ACCESS_TOKEN} environment variable is required!" }
        logger.error { "Please set it with your Home Assistant long-lived access token" }
        logger.error { "Example: export ${HomeAssistantEnvironmentVariables.ACCESS_TOKEN}='your-token-here'" }
        throw IllegalStateException("${HomeAssistantEnvironmentVariables.ACCESS_TOKEN} environment variable is required")
    }

    logger.info { "Connecting to Home Assistant at $haHost:$haPort" }

    val app = Jidouka.initialize {
        haConfiguration = HomeAssistantConfiguration(
            host = haHost,
            port = haPort,
            accessToken = haAccessToken
        )
        jidoukaConfiguration = JidoukaConfiguration(
            logging = JidoukaConfiguration.Logging(logLevel = JidoukaConfiguration.Logging.LogLevel.Debug)
        )

        automations {
            automation(id = "print_hello_world") {
                val contactSensorEntity = entity("binary_sensor.living_room_door_contact")

                triggers {
                    time.every(10.minutes)

                    state(contactSensorEntity) {
                        val contactSensorOpenState = "on"
                        it?.stateRaw.equals(contactSensorOpenState, ignoreCase = true)
                    }
                }

                conditions {
                    condition {
                        time.isBetween(
                            start = DayOfWeek.MONDAY,
                            end = DayOfWeek.FRIDAY
                        )
                    }
                }

                actions {
                    log.info { "Hello world!" }
                }
            }
            register(
                ExampleAutomations.automations
            )
            register(
                WebhookExamples.automations
            )
        }
    }

    logger.info { "Starting Jidouka application..." }
    app.start()
}
