package dev.jidouka.example

import dev.jidouka.aliases.EntityId
import dev.jidouka.api.Jidouka
import dev.jidouka.automations.AutomationMode
import dev.jidouka.automations.dsl.scopes.LoggingScope
import dev.jidouka.components.Domain
import dev.jidouka.components.Entity
import dev.jidouka.components.GenericState
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object ExampleAutomations {

    private const val MOTION_SENSOR_ID = "binary_sensor.main_bathroom_motion_occupancy"
    private const val CONTACT_SENSOR_ID = "binary_sensor.main_bathroom_door_contact"
    private const val OUTLET_HEATER_ID = "switch.main_bathroom_outlet"
    private const val TEMPERATURE_ID = "sensor.main_bathroom_temperature_temperature"
    private const val WEATHER_FORECAST_HOME_ID: EntityId = "weather.forecast_home"

    private const val BATHROOM_HEATER_ID = "turnOnBathroomHeaterWhenCold"
    private const val bathroomIsWarmTemperatureF = 78

    val turnOnBathroomHeaterWhenCold = Jidouka.automation(
        id = BATHROOM_HEATER_ID,
        mode = AutomationMode.Single,
    ) {
        val motionEntity = entity(MOTION_SENSOR_ID)
        val contactEntity = entity(CONTACT_SENSOR_ID)
        val temperatureEntity = entity(TEMPERATURE_ID)
        val weatherEntity = entity(WEATHER_FORECAST_HOME_ID)

        fun bathroomIsCool(temperatureState: GenericState?): Boolean {
            val bathroomTemperatureF = temperatureState?.state?.toFloatOrNull()
            val bathroomIsCool = bathroomTemperatureF?.let { it < bathroomIsWarmTemperatureF }
                ?: false // Will rely just on cold outside

            return bathroomIsCool
        }

        triggers {
            combineState(
                motionEntity,
                contactEntity,
                temperatureEntity
            ) { motion, contact, temperature ->
                val hasMotionAndDoorClosed = (motion?.state == "on")
                        && (contact?.state == "off")

                hasMotionAndDoorClosed && bathroomIsCool(temperature)
            }
        }

        conditions {
            +suspend { weatherIsColdOutside(weatherEntity) }
        }

        actions {
            while (contactEntity.state()?.state == "off" && bathroomIsCool(temperatureEntity.state())) {
                actions.call(
                    domain = Domain.Switch.id,
                    action = "turn_on",
                    target = {
                        entity(OUTLET_HEATER_ID)
                    }
                )

                delay(3.minutes) //TODO check electrical usage

                actions.call(
                    domain = Domain.Switch.id,
                    action = "turn_off",
                    target = {
                        entity(OUTLET_HEATER_ID)
                    }
                )
                delay(1.minutes)
            }

            actions.call(
                domain = Domain.Switch.id,
                action = "turn_off",
                target = {
                    entity(OUTLET_HEATER_ID)
                }
            )
        }
    }


    private val TURN_OFF_HEATER_WHEN_DOOR_IS_OPENED_ID = "turnOffHeaterWhenDoorIsOpenedId"
    val turnOffHeaterWhenDoorIsOpened = Jidouka.automation(
        id = TURN_OFF_HEATER_WHEN_DOOR_IS_OPENED_ID,
    ) {
        val contactSensorEntity = entity(CONTACT_SENSOR_ID)
        val outletEntity = entity(OUTLET_HEATER_ID)

        suspend fun outletOn(log: LoggingScope): Boolean {
            val predicate = outletEntity.state()?.state?.equals("on", ignoreCase = true) == true
            log.info { "Outlet is on: $predicate" }
            return predicate
        }

        triggers {
            state(contactSensorEntity) {
                it?.state.equals("on", ignoreCase = true)
            }
        }

        actions {
            val outletOnBoolean = outletOn(log)
            log.debug { "Bathroom outlet is on: $outletOnBoolean" }

            while (outletOn(log)) {
                val result = actions.call(
                    domain = "switch",
                    action = "turn_off"
                ) {
                    entity(outletEntity)
                }
                log.debug { "Switch turn off result: ${if (result.isSuccess) "SUCCESS" else "FAILED: ${result.exceptionOrNull()?.message}"}" }
                if (result.isSuccess) {
                    automations.cancel(BATHROOM_HEATER_ID)
                } else {
                    delay(5.seconds)
                }
            }
        }
    }

    val `turn on hallway lights at different brightness levels on motion` = Jidouka.automation(
        id = "motion_lights_hallway",
        mode = AutomationMode.Restart // Restart timer if motion detected again
    ) {
        val motionSensor = entity("binary_sensor.hallway_motion")
        val hallwayLights = listOf(
            entity("light.hallway_1"),
            entity("light.hallway_2")
        )

        triggers {
            state(motionSensor) { motion ->
                motion?.state == "on"
            }
        }

        conditions {
            condition { LightManager.anyOn(hallwayLights) }
        }

        actions {
            log.info { "Motion detected in hallway" }

            val currentHour = time.now
                .toLocalDateTime(time.timeZone)
                .hour

            val brightness = when (currentHour) {
                in 6..8 -> 150    // Morning: medium brightness
                in 9..17 -> 255   // Daytime: full brightness
                in 18..22 -> 200  // Evening: slightly dimmed
                else -> 50        // Night: very dim
            }

            log.info { "Turning on hallway lights with brightness: $brightness" }

            val turnOnSuccess = LightManager.turnOn(
                actions = actions,
                entities = hallwayLights,
                brightness = brightness
            )

            if (turnOnSuccess) {
                log.info { "Lights turned on successfully" }
            } else {
                log.error { "Failed to turn on hallway lights" }
            }
        }
    }

    val `dim lights gradually in the evening` = Jidouka.automation(
        id = "evening_dimmer"
    ) {
        val livingRoomLights = listOf(
            entity("light.living_room_main"),
            entity("light.living_room_accent")
        )

        triggers {
            time.at(LocalTime(hour = 21, minute = 0))
        }

        conditions {
            condition { LightManager.anyOn(livingRoomLights) }
        }

        actions {
            log.info { "Starting evening dimming sequence" }

            val steps = 6
            val initialBrightness = 255
            val finalBrightness = 50

            val brightnessSteps = (0 until steps).map { step ->
                initialBrightness - ((initialBrightness - finalBrightness) * step / (steps - 1))
            }

            brightnessSteps.forEachIndexed { index, brightness ->
                log.info { "Setting brightness to $brightness (step ${index + 1}/$steps)" }

                LightManager.setBrightness(
                    actions = actions,
                    brightness = brightness,
                    entities = livingRoomLights
                )

                if (index < brightnessSteps.lastIndex) {
                    delay(5.minutes)
                }
            }

            log.info { "Evening dimming sequence complete" }
        }
    }

    val automations = listOf(
        turnOnBathroomHeaterWhenCold,
        turnOffHeaterWhenDoorIsOpened,
        `turn on hallway lights at different brightness levels on motion`,
        `dim lights gradually in the evening`
    )

    private suspend fun weatherIsColdOutside(weatherEntity: Entity<GenericState>): Boolean {
        val coldWeatherTemperatureF = 55
        val temperatureF = weatherEntity.state()?.rawAttributes?.get("temperature") as? Int ?: return false
        return temperatureF < coldWeatherTemperatureF

    }
}
