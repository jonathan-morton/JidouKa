package dev.jidouka.common.network.models.hass.websocket

import dev.jidouka.common.network.JsonManager
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthorizationMessageTest {
    private val json = JsonManager().json
    private val serializer = AuthorizationMessageSerializer(json)

    @Test
    fun `decodes auth_required`() {
        val payload = buildJsonObject {
            put("type", "auth_required")
            put("ha_version", HA_VERSION)
        }

        val message = json.decodeFromJsonElement(serializer, payload)

        assertEquals(AuthorizationMessage.Required(haVersion = HA_VERSION), message)
    }

    @Test
    fun `decodes auth_ok`() {
        val payload = buildJsonObject {
            put("type", "auth_ok")
            put("ha_version", HA_VERSION)
        }

        val message = json.decodeFromJsonElement(serializer, payload)

        assertEquals(AuthorizationMessage.Ok(haVersion = HA_VERSION), message)
    }

    @Test
    fun `decodes auth_invalid`() {
        val payload = buildJsonObject {
            put("type", "auth_invalid")
            put("message", INVALID_REASON)
        }

        val message = json.decodeFromJsonElement(serializer, payload)

        assertEquals(AuthorizationMessage.Invalid(message = INVALID_REASON), message)
    }

    @Test
    fun `encodes auth with the access token`() {
        val payload = json.encodeToJsonElement(serializer, AuthorizationMessage.Authorize(accessToken = ACCESS_TOKEN))

        val expected = buildJsonObject {
            put("type", "auth")
            put("access_token", ACCESS_TOKEN)
        }
        assertEquals(expected, payload)
    }

    @Test
    fun `fails on a message without a type`() {
        val payload = buildJsonObject { put("ha_version", HA_VERSION) }

        assertFailsWith<SerializationException> { json.decodeFromJsonElement(serializer, payload) }
    }

    @Test
    fun `fails on a message type that is not an auth message`() {
        val payload = buildJsonObject { put("type", "result") }

        assertFailsWith<SerializationException> { json.decodeFromJsonElement(serializer, payload) }
    }

    private companion object {
        private const val ACCESS_TOKEN = "token"
        private const val HA_VERSION = "2026.1.0"
        private const val INVALID_REASON = "Invalid password"
    }
}
