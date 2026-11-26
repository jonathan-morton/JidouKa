package dev.jidouka.common.network.models.hass

/**
 * The state attributes Home Assistant puts on entities of every domain: `EntityStateAttribute` in
 * `homeassistant/const.py`, without `latitude` and `longitude`, which only location domains send.
 * `EntityAttributes` has a getter for each.
 */
public enum class EntityStateAttribute(public val haValue: String) {
    AssumedState("assumed_state"),
    Attribution("attribution"),
    DeviceClass("device_class"),
    EntityPicture("entity_picture"),
    FriendlyName("friendly_name"),
    Icon("icon"),
    Restored("restored"),
    SupportedFeatures("supported_features"),
    UnitOfMeasurement("unit_of_measurement"),
}
