package dev.jidouka

import dev.jidouka.api.Jidouka
import dev.jidouka.automations.AutomationMode
import dev.jidouka.test.AutomationTestEnvironment
import dev.jidouka.test.RecordedEvent
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EventTriggerTests : BaseUnitTest() {

    private val lightId = "light.bedroom"
    private val sceneId = "scene.movie_mode"
    private val notifyId = "notify.mobile_app"
    private val personEntityId = "person.owner"

    private fun buttonEventData(
        entityId: String = "event.room_button_01_action",
        eventType: String = "single"
    ) = mapOf(
        "entity_id" to entityId,
        "new_state" to mapOf(
            "entity_id" to entityId,
            "state" to "2026-04-08T00:07:15.628+00:00",
            "attributes" to mapOf(
                "event_types" to listOf("single", "double", "long"),
                "event_type" to eventType,
                "friendly_name" to "Room Button 01 Action"
            )
        ),
        "old_state" to mapOf(
            "entity_id" to entityId,
            "state" to "2026-04-08T00:06:43.223+00:00",
            "attributes" to mapOf(
                "event_types" to listOf("single", "double", "long"),
                "event_type" to eventType,
                "friendly_name" to "Room Button 01 Action"
            )
        )
    )

    @Test
    fun `event trigger fires action`() = runTest {
        val `button single press toggles light` = Jidouka.automation(
            id = "button_toggle",
            mode = AutomationMode.Single
        ) {
            triggers {
                events.on("state_changed")
            }
            actions {
                actions.call("light", "toggle") {
                    entity(lightId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`button single press toggles light`)

            env.emitEvent("state_changed", data = buttonEventData())

            val actions = env.events<RecordedEvent.Action>()
            assertEquals(1, actions.size)
            assertEquals("light", actions[0].domainId)
            assertEquals("toggle", actions[0].action)
            assertTrue(actions[0].target?.entityIds?.contains(lightId) == true)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `event trigger with condition filters events`() = runTest {
        val `button single press turns on light` = Jidouka.automation(
            id = "button_single_light",
            mode = AutomationMode.Single
        ) {
            triggers {
                // Filter: only fire when the button's event_type attribute is "single"
                events.on("state_changed") { event ->
                    val newState = event.rawData["new_state"] as? Map<*, *>
                    val attributes = newState?.get("attributes") as? Map<*, *>
                    attributes?.get("event_type") == "single"
                }
            }
            actions {
                actions.call("light", "turn_on") {
                    entity(lightId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`button single press turns on light`)

            env.emitEvent("state_changed", data = buttonEventData(eventType = "single"))

            val actions = env.events<RecordedEvent.Action>()
            assertEquals(1, actions.size)
            assertEquals("light", actions[0].domainId)
            assertEquals("turn_on", actions[0].action)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `event trigger condition rejects non-matching events`() = runTest {
        val `button double press activates scene` = Jidouka.automation(
            id = "button_double_scene",
            mode = AutomationMode.Single
        ) {
            triggers {
                // Only fire on double press
                events.on("state_changed") { event ->
                    val newState = event.rawData["new_state"] as? Map<*, *>
                    val attributes = newState?.get("attributes") as? Map<*, *>
                    attributes?.get("event_type") == "double"
                }
            }
            actions {
                actions.call("scene", "turn_on") {
                    entity(sceneId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`button double press activates scene`)

            env.emitEvent("state_changed", data = buttonEventData(eventType = "single"))

            assertTrue(env.events<RecordedEvent.Action>().isEmpty())
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `triggered event data accessible in actions`() = runTest {
        val `button press notification` = Jidouka.automation(
            id = "button_notify",
            mode = AutomationMode.Single
        ) {
            triggers {
                events.on("state_changed")
            }
            actions {
                val event = triggered.event()
                val newState = event?.rawData?.get("new_state") as? Map<*, *>
                val attributes = newState?.get("attributes") as? Map<*, *>
                val pressType = attributes?.get("event_type") as? String ?: "unknown"

                actions.call(
                    "notify", "mobile_app",
                    data = mapOf("message" to "Button pressed: $pressType")
                ) {
                    entity(notifyId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`button press notification`)

            env.emitEvent("state_changed", data = buttonEventData(eventType = "long"))

            val actions = env.events<RecordedEvent.Action>()
            assertEquals(1, actions.size)
            assertEquals("notify", actions[0].domainId)
            assertEquals("mobile_app", actions[0].action)
            assertEquals("Button pressed: long", actions[0].data["message"])
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `multiple event triggers use OR logic`() = runTest {
        val `button any press indicator light` = Jidouka.automation(
            id = "button_any_press",
            mode = AutomationMode.Single
        ) {
            triggers {
                events.on("state_changed")
                events.on("manual_button_press")
            }
            actions {
                actions.call("light", "turn_on") {
                    entity(lightId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`button any press indicator light`)

            env.emitEvent("state_changed", data = buttonEventData())

            assertEquals(1, env.events<RecordedEvent.Action>().size)

            env.clearEvents()

            env.emitEvent("manual_button_press", data = mapOf("source" to "physical"))

            assertEquals(1, env.events<RecordedEvent.Action>().size)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `event trigger blocked by failing condition`() = runTest {
        val `button press when home` = Jidouka.automation(
            id = "button_when_home",
            mode = AutomationMode.Single
        ) {
            triggers {
                events.on("state_changed")
            }
            conditions {
                condition {
                    entity(personEntityId).state()?.state == "home"
                }
            }
            actions {
                actions.call("light", "turn_on") {
                    entity(lightId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setEntityState(personEntityId, "not_home")

            env.register(`button press when home`)

            env.emitEvent("state_changed", data = buttonEventData())

            assertTrue(env.events<RecordedEvent.Action>().isEmpty())
            assertTrue(env.events<RecordedEvent.TriggerFired>().any { it.automationId == "button_when_home" })
            assertTrue(env.events<RecordedEvent.ConditionFailed>().any { it.automationId == "button_when_home" })
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }
}