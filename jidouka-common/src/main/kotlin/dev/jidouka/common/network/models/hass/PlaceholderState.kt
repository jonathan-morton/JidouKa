package dev.jidouka.common.network.models.hass

/**
 * The states Home Assistant reports when an entity has no real value: `STATE_UNAVAILABLE` and `STATE_UNKNOWN` in
 * `homeassistant/const.py`. Attributes use the same words.
 */
public enum class PlaceholderState(public val haValue: String) {
    Unavailable("unavailable"),
    Unknown("unknown");

    public companion object {
        /**
         * Finds the placeholder that [value] spells, ignoring case.
         *
         * @param value A state or attribute value, for example `unavailable` or `Unknown`
         * @return The placeholder, or null for a real value
         */
        public fun of(value: String): PlaceholderState? {
            return entries.firstOrNull { state ->
                state.haValue.equals(value, ignoreCase = true)
            }
        }
    }
}