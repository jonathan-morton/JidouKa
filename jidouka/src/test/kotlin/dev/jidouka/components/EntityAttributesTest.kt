package dev.jidouka.components

import dev.jidouka.common.attributes.AttributeParser
import dev.jidouka.common.network.models.hass.EntityStateAttribute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class EntityAttributesTest {
    private fun attributes(vararg pairs: Pair<String, Any?>) = EntityAttributes(ENTITY_ID, mapOf(*pairs))

    @Test
    fun `missing keys and placeholders read as null`() {
        val attributes = attributes(
            EntityStateAttribute.Icon.haValue to UNKNOWN,
            EntityStateAttribute.FriendlyName.haValue to EMPTY_TEXT,
        )

        assertNull(attributes.icon)
        assertNull(attributes.friendlyName)
        assertNull(attributes.deviceClass)
    }

    @Test
    fun `base members read through the built-in parsers`() {
        val attributes = attributes(
            EntityStateAttribute.FriendlyName.haValue to FRIENDLY_NAME,
            EntityStateAttribute.EntityPicture.haValue to ENTITY_PICTURE,
            EntityStateAttribute.SupportedFeatures.haValue to SUPPORTED_FEATURES,
            EntityStateAttribute.Attribution.haValue to ATTRIBUTION,
        )

        assertEquals(FRIENDLY_NAME, attributes.friendlyName)
        assertEquals(ENTITY_PICTURE, attributes.entityPicture)
        assertEquals(SUPPORTED_FEATURES, attributes.supportedFeatures)
        assertEquals(ATTRIBUTION, attributes.attribution)
    }

    @Test
    fun `assumedState and restored are false unless sent as true`() {
        val unsent = attributes()
        val sent = attributes(
            EntityStateAttribute.AssumedState.haValue to true,
            EntityStateAttribute.Restored.haValue to true,
        )

        assertFalse(unsent.assumedState)
        assertFalse(unsent.restored)
        assertTrue(sent.assumedState)
        assertTrue(sent.restored)
    }

    @Test
    fun `a value that doesn't fit reads as null and stays in raw`() {
        val attributes = attributes(INT_KEY to NOT_A_NUMBER)

        assertNull(attributes.attribute(INT_KEY, AttributeParser.Int))
        assertEquals(NOT_A_NUMBER, attributes.raw[INT_KEY])
    }

    @Test
    fun `list reads a list attribute`() {
        val attributes = attributes(LIST_KEY to LIGHTS)

        assertEquals(LIGHTS, attributes.list(LIST_KEY, AttributeParser.String))
    }

    @Test
    fun `a user parser reads a user type`() {
        val attributes = attributes(USER_TYPE_KEY to TestColorMode.ColorTemp.haValue)

        assertEquals(TestColorMode.ColorTemp, attributes.attribute(USER_TYPE_KEY, TestColorMode))
    }

    @Test
    fun `a state's attributes belong to its entity`() {
        val state = GenericState.parser.parse(
            stateObject = StateObject(
                entityId = ENTITY_ID,
                state = STATE,
                attributesRaw = mapOf(EntityStateAttribute.FriendlyName.haValue to FRIENDLY_NAME),
                lastChanged = TIMESTAMP,
                lastUpdated = TIMESTAMP,
                lastReported = null,
                context = null,
            ),
            previous = null,
        )

        assertEquals(ENTITY_ID, state?.entityId)
        assertEquals(FRIENDLY_NAME, state?.attributes?.friendlyName)
    }

    private enum class TestColorMode(val haValue: String) {
        OnOff("onoff"), ColorTemp("color_temp");

        companion object : AttributeParser<TestColorMode> {
            override fun parse(value: Any) = entries.firstOrNull { it.haValue == value }
        }
    }

    private companion object {
        private const val ENTITY_ID = "light.desk_zone"
        private const val STATE = "on"
        private val TIMESTAMP = Instant.fromEpochMilliseconds(0)

        private const val INT_KEY = "brightness"
        private const val LIST_KEY = "lights"
        private const val USER_TYPE_KEY = "color_mode"

        private const val ATTRIBUTION = "Data from a weather service"
        private const val ENTITY_PICTURE = "/api/image.png"
        private const val FRIENDLY_NAME = "Desk Zone"
        private const val SUPPORTED_FEATURES = 44
        private val LIGHTS = listOf("Desk Left", "Desk Right")
        private const val NOT_A_NUMBER = "bright"

        private const val UNKNOWN = "unknown"
        private const val EMPTY_TEXT = ""
    }
}
