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
    val haHost = System.getenv(HomeAssistantEnvironmentVariables.HOST)
        ?: HomeAssistantConfiguration.DEFAULT_HOST

    val haPort = System.getenv(HomeAssistantEnvironmentVariables.PORT)?.let { value ->
        value.toIntOrNull()
            ?: throw IllegalStateException("${HomeAssistantEnvironmentVariables.PORT} must be a number, got '$value'")
    } ?: HomeAssistantConfiguration.DEFAULT_PORT

    val haSecure = System.getenv(HomeAssistantEnvironmentVariables.SECURE)?.let { value ->
        value.toBooleanStrictOrNull()
            ?: throw IllegalStateException("${HomeAssistantEnvironmentVariables.SECURE} must be true or false, got '$value'")
    } ?: HomeAssistantConfiguration.DEFAULT_IS_SECURE

    val haAccessToken = System.getenv(HomeAssistantEnvironmentVariables.ACCESS_TOKEN) ?: run {
        logger.error { "${HomeAssistantEnvironmentVariables.ACCESS_TOKEN} environment variable is required!" }
        logger.error { "Please set it with your Home Assistant long-lived access token" }
        logger.error { "Example: export ${HomeAssistantEnvironmentVariables.ACCESS_TOKEN}='your-token-here'" }
        throw IllegalStateException("${HomeAssistantEnvironmentVariables.ACCESS_TOKEN} environment variable is required")
    }

    logger.info { "Connecting to Home Assistant at $haHost:$haPort (secure: $haSecure)" }

    val app = Jidouka.initialize {
        haConfiguration = HomeAssistantConfiguration(
            host = haHost,
            port = haPort,
            accessToken = haAccessToken,
            isSecure = haSecure
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
