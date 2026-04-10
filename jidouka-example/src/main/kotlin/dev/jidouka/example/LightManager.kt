package dev.jidouka.example

import dev.jidouka.automations.dsl.scopes.ActionsScope
import dev.jidouka.automations.dsl.scopes.LoggingScope
import dev.jidouka.components.Entity
import dev.jidouka.components.GenericState
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

enum class LightState(val state: String) {
    ON("on"),
    OFF("off"),
    UNAVAILABLE("unavailable")
}

object LightManager {

    private const val MAX_RETRIES = 3
    private val DEFAULT_RETRY_DELAY = 500.milliseconds

    /**
     * Turn on lights with optional brightness control and retry logic.
     *
     * @param actions The ActionsScope from the automation
     * @param entity A single light entity (optional if entities is provided)
     * @param entities A list of light entities (optional if entity is provided)
     * @param brightness Brightness level (0-255), null for default brightness
     * @param maxRetries Maximum number of retry attempts (default: 3)
     * @param retryDelay Delay between retries (default: 500ms)
     */
    suspend fun turnOn(
        actions: ActionsScope,
        logs: LoggingScope? = null,
        entity: Entity<*>? = null,
        entities: List<Entity<*>> = emptyList(),
        brightness: Int? = null,
        maxRetries: Int = MAX_RETRIES,
        retryDelay: Duration = DEFAULT_RETRY_DELAY
    ): Boolean {
        val lightEntities = validateEntities(entity, entities)
        if (lightEntities.isEmpty()) return false

        val data = buildMap {
            brightness?.let {
                require(it in 0..255) { "Brightness must be between 0 and 255" }
                put("brightness", it)
            }
        }

        repeat(maxRetries) { attempt ->
            val result = actions.call(
                domain = "light",
                action = "turn_on",
                data = data
            ) {
                entities(lightEntities)
            }

            if (result.isSuccess) {
                delay(retryDelay)

                val allOn = allOn(lightEntities)

                if (allOn) {
                    return true
                }

                if (attempt < maxRetries - 1) {
                    logs?.debug { "Not all lights turned on, retrying... (attempt ${attempt + 2}/$maxRetries)" }
                }
            }

            if (attempt < maxRetries - 1) {
                delay(retryDelay)
            }
        }

        return false
    }

    /**
     * Turn off lights with retry logic.
     *
     * @param actions The ActionsScope from the automation
     * @param entity A single light entity (optional if entities is provided)
     * @param entities A list of light entities (optional if entity is provided)
     * @param maxRetries Maximum number of retry attempts (default: 3)
     * @param retryDelay Delay between retries (default: 500ms)
     */
    suspend fun turnOff(
        actions: ActionsScope,
        logs: LoggingScope? = null,
        entity: Entity<*>? = null,
        entities: List<Entity<*>> = emptyList(),
        maxRetries: Int = MAX_RETRIES,
        retryDelay: Duration = DEFAULT_RETRY_DELAY
    ): Boolean {
        val lightEntities = validateEntities(entity, entities)
        if (lightEntities.isEmpty()) return false

        repeat(maxRetries) { attempt ->
            val result = actions.call(
                domain = "light",
                action = "turn_off"
            ) {
                entities(lightEntities)
            }

            if (result.isSuccess) {
                delay(retryDelay)

                val anyOn = anyOn(lightEntities)

                if (anyOn.not()) {
                    return true
                }

                if (attempt < maxRetries - 1) {
                    logs?.debug { "Not all lights turned off, retrying... (attempt ${attempt + 2}/$maxRetries)" }
                }
            }

            if (attempt < maxRetries - 1) {
                delay(retryDelay)
            }
        }

        return false
    }

    /**
     * Check if a light entity is currently on.
     */
    suspend fun isOn(entity: Entity<*>): Boolean {
        return entity.state()?.let { state ->
            (state as? GenericState)?.state == LightState.ON.state
        } ?: false
    }

    /**
     * Check if any of the provided light entities are on.
     */
    suspend fun anyOn(entities: List<Entity<*>>): Boolean {
        return entities.any { isOn(it) }
    }

    /**
     * Check if all of the provided light entities are on.
     */
    suspend fun allOn(entities: List<Entity<*>>): Boolean {
        return entities.all { isOn(it) }
    }

    /**
     * Toggle lights - turn on if off, turn off if on.
     *
     * @param actions The ActionsScope from the automation
     * @param entity A single light entity (optional if entities is provided)
     * @param entities A list of light entities (optional if entity is provided)
     * @param brightness Brightness level when turning on (0-255), null for default
     */
    suspend fun toggle(
        actions: ActionsScope,
        entity: Entity<*>? = null,
        entities: List<Entity<*>> = emptyList(),
        brightness: Int? = null
    ): Boolean {
        val lightEntities = validateEntities(entity, entities)
        if (lightEntities.isEmpty()) return false

        // Check if any lights are on
        return if (anyOn(lightEntities)) {
            turnOff(actions, entities = lightEntities)
        } else {
            turnOn(actions, entities = lightEntities, brightness = brightness)
        }
    }

    /**
     * Set brightness for lights (turns them on if they're off).
     *
     * @param actions The ActionsScope from the automation
     * @param brightness Brightness level (0-255)
     * @param entity A single light entity (optional if entities is provided)
     * @param entities A list of light entities (optional if entity is provided)
     */
    suspend fun setBrightness(
        actions: ActionsScope,
        brightness: Int,
        entity: Entity<*>? = null,
        entities: List<Entity<*>> = emptyList()
    ): Boolean {
        require(brightness in 0..255) { "Brightness must be between 0 and 255" }
        return turnOn(
            actions = actions,
            entity = entity,
            entities = entities,
            brightness = brightness
        )
    }

    /**
     * Validate that either entity or entities is provided, but not both.
     * Returns the list of entities to operate on.
     */
    private fun validateEntities(
        entity: Entity<*>?,
        entities: List<Entity<*>>
    ): List<Entity<*>> {
        return when {
            entity != null && entities.isEmpty() -> listOf(entity)
            entity == null && entities.isNotEmpty() -> entities
            entity == null -> {
                println("Warning: No entities provided to LightManager")
                emptyList()
            }

            else -> {
                throw IllegalArgumentException(
                    "Must provide either a single entity or a list of entities, not both"
                )
            }
        }
    }
}