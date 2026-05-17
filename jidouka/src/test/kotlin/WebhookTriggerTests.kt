@file:Suppress("LocalVariableName")

package dev.jidouka

import dev.jidouka.api.Jidouka
import dev.jidouka.automations.AutomationMode
import dev.jidouka.automations.dsl.triggers.TriggerContext
import dev.jidouka.test.AutomationTestEnvironment
import dev.jidouka.test.RecordedEvent
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

class WebhookTriggerTests : BaseUnitTest() {

    private val deployHookId = "deploy-hook"
    private val alertHookId = "alert-hook"
    private val notifyId = "notify.mobile_app"
    private val lightId = "light.bedroom"
    private val personEntityId = "person.owner"

    @Test
    fun `webhook trigger fires action`() = runTest {
        val `notify on deploy webhook` = Jidouka.automation(
            id = "deploy_notify",
            mode = AutomationMode.Single
        ) {
            triggers {
                webhook(deployHookId)
            }
            actions {
                actions.call("notify", "mobile_app", data = mapOf("message" to "deploy")) {
                    entity(notifyId)
                }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`notify on deploy webhook`)

            env.emitWebhook(deployHookId)

            val actions = env.events<RecordedEvent.Action>()
            assertEquals(1, actions.size)
            assertEquals("notify", actions[0].domainId)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `webhook predicate filters out low-priority payloads`() = runTest {
        val `high priority alerts only` = Jidouka.automation(
            id = "high_priority_alert",
            mode = AutomationMode.Single
        ) {
            triggers {
                webhook(alertHookId) { payload ->
                    (payload.jsonData?.get("priority") as? Int)?.let { it >= 8 } ?: false
                }
            }
            actions {
                actions.call("notify", "mobile_app") { entity(notifyId) }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`high priority alerts only`)

            env.emitWebhook(
                alertHookId,
                json = buildJsonObject { put("priority", 3) }
            )

            assertTrue(env.events<RecordedEvent.Action>().isEmpty())
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `webhook predicate accepts matching payload`() = runTest {
        val `high priority alerts only` = Jidouka.automation(
            id = "high_priority_alert_match",
            mode = AutomationMode.Single
        ) {
            triggers {
                webhook(alertHookId) { payload ->
                    (payload.jsonData?.get("priority") as? Int)?.let { it >= 8 } ?: false
                }
            }
            actions {
                actions.call("notify", "mobile_app") { entity(notifyId) }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`high priority alerts only`)

            env.emitWebhook(
                alertHookId,
                json = buildJsonObject { put("priority", 9) }
            )

            assertEquals(1, env.events<RecordedEvent.Action>().size)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `webhook json values come through as native Kotlin types`() = runTest {
        var capturedStatus: String? = "not-set"
        var capturedCount: Int? = -1
        var capturedEnabled: Boolean? = null

        val `capture webhook json` = Jidouka.automation(
            id = "capture_webhook_json",
            mode = AutomationMode.Single
        ) {
            triggers {
                webhook(deployHookId)
            }
            actions {
                val json = triggered.webhook()?.jsonData
                capturedStatus = json?.get("status") as? String
                capturedCount = json?.get("count") as? Int
                capturedEnabled = json?.get("enabled") as? Boolean
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`capture webhook json`)

            env.emitWebhook(
                deployHookId,
                json = buildJsonObject {
                    put("status", "ok")
                    put("count", 42)
                    put("enabled", true)
                }
            )

            assertEquals("ok", capturedStatus)
            assertEquals(42, capturedCount)
            assertEquals(true, capturedEnabled)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `webhook json converts nested objects and arrays recursively`() = runTest {
        var capturedJson: Map<String, Any?>? = null

        val `capture nested webhook json` = Jidouka.automation(
            id = "capture_nested_json",
            mode = AutomationMode.Single
        ) {
            triggers {
                webhook(deployHookId)
            }
            actions {
                capturedJson = triggered.webhook()?.jsonData
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`capture nested webhook json`)

            env.emitWebhook(
                deployHookId,
                json = buildJsonObject {
                    putJsonObject("nested") {
                        put("key", "value")
                    }
                    putJsonArray("tags") {
                        add("a")
                        add("b")
                    }
                    putJsonArray("objects") {
                        addJsonObject { put("n", 1) }
                        addJsonObject { put("n", 2) }
                    }
                }
            )

            val jsonData = capturedJson ?: fail("Capture json was null")
            assertNotNull(jsonData)
            val nested = jsonData["nested"] as? Map<*, *>
            assertNotNull(nested)
            assertEquals("value", nested["key"])
            assertEquals(listOf("a", "b"), jsonData["tags"])
            val objects = jsonData["objects"] as? List<*>
            assertNotNull(objects)
            assertEquals(2, objects.size)
            assertEquals(1, (objects[0] as Map<*, *>)["n"])
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `triggered webhook returns context with matching id`() = runTest {
        var capturedContext: TriggerContext.Webhook? = null

        val `capture webhook context` = Jidouka.automation(
            id = "capture_context",
            mode = AutomationMode.Single
        ) {
            triggers {
                webhook(deployHookId)
            }
            actions {
                capturedContext = triggered.webhook()
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`capture webhook context`)

            env.emitWebhook(deployHookId, json = buildJsonObject { put("x", 1) })

            assertNotNull(capturedContext)
            val context = capturedContext ?: fail("Webhook context was null")
            assertEquals(deployHookId, context.webhookId)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `triggered webhook with id matches only on exact id`() = runTest {
        var matchedById: TriggerContext.Webhook? = null
        var matchedByWrongId: TriggerContext.Webhook? = null

        val `id-filtered access` = Jidouka.automation(
            id = "id_filtered_access",
            mode = AutomationMode.Single
        ) {
            triggers {
                webhook(deployHookId)
            }
            actions {
                matchedById = triggered.webhook(deployHookId)
                matchedByWrongId = triggered.webhook("some-other-hook")
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`id-filtered access`)

            env.emitWebhook(deployHookId)

            assertNotNull(matchedById)
            assertNull(matchedByWrongId)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `byWebhook accessors discriminate trigger source`() = runTest {
        var byWebhook = false
        var byMatchingId = false
        var byWrongId = false
        var byState = false
        var byTime = false
        var byFlow = false

        val `discriminator automation` = Jidouka.automation(
            id = "by_webhook_discriminator",
            mode = AutomationMode.Single
        ) {
            triggers {
                webhook(deployHookId)
            }
            actions {
                byWebhook = triggered.byWebhook()
                byMatchingId = triggered.byWebhook(deployHookId)
                byWrongId = triggered.byWebhook("wrong")
                byState = triggered.byState()
                byTime = triggered.byTime()
                byFlow = triggered.byFlow()
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`discriminator automation`)

            env.emitWebhook(deployHookId)

            assertTrue(byWebhook)
            assertTrue(byMatchingId)
            assertFalse(byWrongId)
            assertFalse(byState)
            assertFalse(byTime)
            assertFalse(byFlow)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `form data and query repr strings pass through unmodified`() = runTest {
        var capturedDataRepr: String? = null
        var capturedQueryRepr: String? = null
        var capturedJson: Map<String, Any?>? = mapOf("seeded" to true)

        val `capture repr fields` = Jidouka.automation(
            id = "capture_repr",
            mode = AutomationMode.Single
        ) {
            triggers {
                webhook(deployHookId)
            }
            actions {
                val ctx = triggered.webhook()
                capturedDataRepr = ctx?.formDataRepresentation
                capturedQueryRepr = ctx?.queryRepresentation
                capturedJson = ctx?.jsonData
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`capture repr fields`)

            val dataRepr = "<MultiDictProxy('say': 'Hi', 'to': 'Mom')>"
            val queryRepr = "<MultiDictProxy('category': 'shoes', 'color': 'blue')>"
            env.emitWebhook(
                deployHookId,
                json = null,
                dataRepr = dataRepr,
                queryRepr = queryRepr
            )

            assertEquals(dataRepr, capturedDataRepr)
            assertEquals(queryRepr, capturedQueryRepr)
            assertNull(capturedJson)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `multiple webhook triggers use OR logic`() = runTest {
        val githubHook = "github-hook"
        val gitlabHook = "gitlab-hook"

        val `react to either git host` = Jidouka.automation(
            id = "any_git_host",
            mode = AutomationMode.Parallel(10)
        ) {
            triggers {
                webhook(githubHook)
                webhook(gitlabHook)
            }
            actions {
                actions.call("light", "turn_on") { entity(lightId) }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`react to either git host`)

            env.emitWebhook(githubHook)
            assertEquals(1, env.events<RecordedEvent.Action>().size)

            env.clearEvents()

            env.emitWebhook(gitlabHook)
            assertEquals(1, env.events<RecordedEvent.Action>().size)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `webhook trigger blocked by failing condition`() = runTest {
        val `deploy when home` = Jidouka.automation(
            id = "deploy_when_home",
            mode = AutomationMode.Single
        ) {
            triggers {
                webhook(deployHookId)
            }
            conditions {
                condition {
                    entity(personEntityId).state()?.state == "home"
                }
            }
            actions {
                actions.call("notify", "mobile_app") { entity(notifyId) }
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.setEntityState(personEntityId, "not_home")
            env.register(`deploy when home`)

            env.emitWebhook(deployHookId)

            assertTrue(env.events<RecordedEvent.Action>().isEmpty())
            assertTrue(env.events<RecordedEvent.TriggerFired>().any { it.automationId == "deploy_when_home" })
            assertTrue(env.events<RecordedEvent.ConditionFailed>().any { it.automationId == "deploy_when_home" })
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }

    @Test
    fun `webhook trigger fires when all payload fields are null`() = runTest {
        var capturedContext: TriggerContext.Webhook? = null

        val `accept empty webhook` = Jidouka.automation(
            id = "accept_empty_webhook",
            mode = AutomationMode.Single
        ) {
            triggers {
                webhook(deployHookId)
            }
            actions {
                capturedContext = triggered.webhook()
            }
        }

        AutomationTestEnvironment.test(this) { env ->
            env.register(`accept empty webhook`)

            env.emitWebhook(deployHookId, json = null, dataRepr = null, queryRepr = null)

            val context = capturedContext ?: fail("Webhook context was null")
            assertEquals(deployHookId, context.webhookId)
            assertNull(context.jsonData)
            assertNull(context.formDataRepresentation)
            assertNull(context.queryRepresentation)
            assertTrue(env.events<RecordedEvent.AutomationFailed>().isEmpty())
        }
    }
}
