package dev.jidouka.components

import dev.jidouka.aliases.EntityId
import dev.jidouka.common.attributes.AttributeParser
import dev.jidouka.common.network.models.hass.EntityStateAttribute
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * The attributes Home Assistant can put on an entity of any domain. Domain and per-entity attribute classes
 * extend it.
 *
 * Read your own attributes with [attribute] and [list]. They return null for a missing key, a placeholder
 * (`unknown`, `unavailable`, empty or blank text) or a value that doesn't fit; the original value stays in [raw].
 *
 * @param entityId the entity these attributes belong to. Private: it only names the entity in mismatch warnings,
 * and keeps the name `entityId` free for an attribute.
 */
public open class EntityAttributes(
    private val entityId: EntityId,
    public val raw: Map<String, Any?>
) {
    /**
     * Home Assistant plans to deprecate `friendly_name` (2026.11). It keeps the old name until the entity is renamed,
     * so it can differ from the name the Home Assistant UI shows.
     */
    public val friendlyName: String?
        get() = attribute(EntityStateAttribute.FriendlyName.haValue, AttributeParser.String)

    public val icon: String?
        get() = attribute(EntityStateAttribute.Icon.haValue, AttributeParser.String)

    public val deviceClass: String?
        get() = attribute(EntityStateAttribute.DeviceClass.haValue, AttributeParser.String)

    public val unitOfMeasurement: String?
        get() = attribute(EntityStateAttribute.UnitOfMeasurement.haValue, AttributeParser.String)

    public val entityPicture: String?
        get() = attribute(EntityStateAttribute.EntityPicture.haValue, AttributeParser.String)

    /** The raw feature bitmask. Its bits mean different things in each domain. */
    public val supportedFeatures: Int?
        get() = attribute(EntityStateAttribute.SupportedFeatures.haValue, AttributeParser.Int)

    public val attribution: String?
        get() = attribute(EntityStateAttribute.Attribution.haValue, AttributeParser.String)

    /** Home Assistant writes `assumed_state` only when true. */
    public val assumedState: Boolean
        get() = raw[EntityStateAttribute.AssumedState.haValue] == true

    /** True when the entity registry restored this entity because its integration hasn't loaded. */
    public val restored: Boolean
        get() = raw[EntityStateAttribute.Restored.haValue] == true

    /**
     * Reads [key] with [parser].
     *
     * A value that doesn't fit is logged once per entity, key and parser, and stays in [raw].
     *
     * @param key The attribute key as Home Assistant sends it, for example `brightness`
     * @param parser The parser for the value, for example [AttributeParser.Int]
     * @return The value, or null if [key] is missing, a placeholder or doesn't fit [parser]
     */
    public fun <T : Any> attribute(key: String, parser: AttributeParser<T>): T? = read(key, parser)

    /**
     * Reads [key] as a list whose elements all read with [element].
     *
     * One element that doesn't fit makes the whole list null; see [AttributeParser.list]. The mismatch is logged
     * the same way as in [attribute].
     *
     * @param key The attribute key as Home Assistant sends it, for example `effect_list`
     * @param element The parser for each element, for example [AttributeParser.String]
     * @return The list, or null if [key] is missing, a placeholder, not a list, or has an element that doesn't fit
     */
    public fun <T : Any> list(key: String, element: AttributeParser<T>): List<T>? =
        read(key, AttributeParser.list(element))

    private fun <T : Any> read(key: String, parser: AttributeParser<T>): T? {
        val value = raw[key] ?: return null
        if (AttributeParser.isPlaceholder(value)) {
            return null
        }

        val parsedValue = parser.parse(value)
        if (parsedValue == null) {
            val parserName = AttributeParser.nameOf(parser)
            if (reportedMismatches.isFirstReport(entityId, key, parserName)) {
                logger.warn {
                    "$entityId: attribute '$key' is $value, which doesn't read as $parserName. " +
                            "Reading it as null; the value is still in raw[\"$key\"]."
                }
            }
        }

        return parsedValue
    }

    private companion object {
        private val logger = KotlinLogging.logger {}
        private val reportedMismatches = AttributeMismatches()
    }
}
