package dev.jidouka.components

public open class EntityAttributes(
    public val raw: Map<String, Any?>
) {
    public val friendlyName: String?
        get() = raw["friendly_name"] as? String

    public val icon: String?
        get() = raw["icon"] as? String

    public val deviceClass: String?
        get() = raw["device_class"] as? String

    public val unitOfMeasurement: String?
        get() = raw["unit_of_measurement"] as? String
}
