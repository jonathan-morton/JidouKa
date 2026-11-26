package dev.jidouka.common.attributes

import dev.jidouka.common.attributes.models.AttributeKind
import dev.jidouka.common.attributes.models.AttributeValue
import dev.jidouka.common.network.models.hass.PlaceholderState

/**
 * Reads one attribute value into [T]. Implement it for your own types, for example an enum:
 *
 * ```
 * enum class ColorMode(val haValue: String) {
 *     OnOff("onoff"), Brightness("brightness");
 *
 *     companion object : AttributeParser<ColorMode> {
 *         override fun parse(value: Any) = entries.firstOrNull { it.haValue == value }
 *     }
 * }
 *
 * val LightAttributes.colorMode: ColorMode? get() = attribute("color_mode", ColorMode)
 * ```
 */
public fun interface AttributeParser<out T : Any> {
    /**
     * Reads an attribute value as [T].
     *
     * Getters read a missing value or a placeholder as null before calling this.
     *
     * @param value The attribute value, never null or a placeholder
     * @return The value as [T], or null if it doesn't fit
     */
    public fun parse(value: Any): T?

    @Suppress("RemoveRedundantQualifierName")
    public companion object {
        public val Boolean: AttributeParser<Boolean> = BuiltInAttributeParser(AttributeKind.Boolean) {
            it as? kotlin.Boolean
        }

        /** Reads text, and whole numbers as text (`440414` to `"440414"`). */
        public val String: AttributeParser<String> = BuiltInAttributeParser(AttributeKind.String) { value ->
            when (value) {
                is kotlin.String -> value
                is kotlin.Int,
                is kotlin.Long -> value.toString()

                else -> null
            }
        }

        /** Reads whole numbers that fit, including whole doubles (`2.0` to 2, `1.5` to null). */
        public val Int: AttributeParser<Int> = BuiltInAttributeParser(AttributeKind.Int) { value ->
            Long.parse(value)?.takeIf { longNumber ->
                longNumber in kotlin.Int.MIN_VALUE..kotlin.Int.MAX_VALUE
            }?.toInt()
        }

        /** Reads whole numbers that fit, including whole doubles (`2.0` to 2, `1.5` to null). */
        public val Long: AttributeParser<Long> = BuiltInAttributeParser(AttributeKind.Long) { value ->
            when (value) {
                is kotlin.Int -> value.toLong()
                is kotlin.Long -> value
                is kotlin.Double -> value.toLong().takeIf { longNumber ->
                    longNumber != kotlin.Long.MAX_VALUE && longNumber.toDouble() == value
                }

                else -> null
            }
        }

        /** Reads any number (`1` to 1.0). */
        public val Double: AttributeParser<Double> = BuiltInAttributeParser(AttributeKind.Double) { value ->
            (value as? Number)?.toDouble()
        }

        /** Reads any value except an object. */
        public val Any: AttributeParser<AttributeValue> =
            BuiltInAttributeParser(AttributeKind.Any) { attributeValueOf(it) }

        /**
         * Builds a parser for a list whose elements all read with [element].
         *
         * The list reads as null, not a shorter list, when any element is null, a placeholder or doesn't fit, so
         * positions never shift. An empty list stays empty.
         *
         * @param element The parser for each element
         * @return A parser for the whole list
         */
        public fun <T : Any> list(element: AttributeParser<T>): AttributeParser<List<T>> = ListAttributeParser(element)

        /**
         * Checks whether [value] is one of Home Assistant's placeholders, which every getter reads as null.
         *
         * @param value The attribute value
         * @return True for a [PlaceholderState], `""` or blank text like `"   "`
         */
        public fun isPlaceholder(value: Any): Boolean {
            return value is kotlin.String && (value.isBlank() || PlaceholderState.of(value) != null)
        }

        /**
         * Names [parser] so each mismatch is logged once.
         *
         * Built-ins are named by kind. Any other parser is named by its class, because a default `toString()`
         * changes with every instance.
         *
         * @param parser The parser that didn't read a value
         * @return The kind for a built-in, for example `List<Int>`, or the class name for any other parser
         */
        internal fun nameOf(parser: AttributeParser<*>): String = when (parser) {
            is BuiltInAttributeParser -> parser.kind.toString()
            is ListAttributeParser<*> -> "List<${nameOf(parser.element)}>"
            else -> parser.javaClass.name
        }

        /**
         * Finds the built-in parser for [kind].
         *
         * @param kind The kind to read
         * @return The built-in parser, or for [AttributeKind.ListOf] a [list] of its element's parser
         */
        public fun forKind(kind: AttributeKind): AttributeParser<Any> = when (kind) {
            AttributeKind.Boolean -> Boolean
            AttributeKind.Double -> Double
            AttributeKind.Int -> Int
            AttributeKind.Long -> Long
            AttributeKind.String -> String
            AttributeKind.Any -> Any
            is AttributeKind.ListOf -> list(forKind(kind.element))
        }

        private fun attributeValueOf(value: Any?): AttributeValue? = when (value) {
            null -> null
            is kotlin.Boolean -> AttributeValue.Boolean(value)
            is Number -> AttributeValue.Number(value.toDouble())
            is kotlin.String -> AttributeValue.String(value)
            is List<*> -> AttributeValue.ListOf(values = value.map { attributeValueOf(it) })
            else -> null
        }
    }
}

/** A built-in parser for one [AttributeKind]; [AttributeParser.nameOf] names it by [kind]. */
private class BuiltInAttributeParser<out T : Any>(
    val kind: AttributeKind,
    private val read: (Any) -> T?,
) : AttributeParser<T> {

    override fun parse(value: Any): T? {
        return read(value)
    }
}

/** The parser [AttributeParser.list] builds. [element] can be any parser, so a list has no [AttributeKind] of its own. */
private class ListAttributeParser<out T : Any>(val element: AttributeParser<T>) : AttributeParser<List<T>> {
    override fun parse(value: Any): List<T>? {
        val items = value as? List<*> ?: return null
        return items.map { item ->
            if (item == null || AttributeParser.isPlaceholder(item)) return null
            element.parse(item) ?: return null
        }
    }
}
