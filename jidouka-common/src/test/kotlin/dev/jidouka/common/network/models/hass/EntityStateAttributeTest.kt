package dev.jidouka.common.network.models.hass

import kotlin.test.Test
import kotlin.test.assertEquals

class EntityStateAttributeTest {
    @Test
    fun `each entry spells the key Home Assistant sends`() {
        assertEquals(HOME_ASSISTANT_KEYS, EntityStateAttribute.entries.associateWith { it.haValue })
    }

    private companion object {
        /** `EntityStateAttribute` in Home Assistant's `homeassistant/const.py`, one key per entry. */
        private val HOME_ASSISTANT_KEYS = mapOf(
            EntityStateAttribute.AssumedState to "assumed_state",
            EntityStateAttribute.Attribution to "attribution",
            EntityStateAttribute.DeviceClass to "device_class",
            EntityStateAttribute.EntityPicture to "entity_picture",
            EntityStateAttribute.FriendlyName to "friendly_name",
            EntityStateAttribute.Icon to "icon",
            EntityStateAttribute.Restored to "restored",
            EntityStateAttribute.SupportedFeatures to "supported_features",
            EntityStateAttribute.UnitOfMeasurement to "unit_of_measurement",
        )
    }
}
