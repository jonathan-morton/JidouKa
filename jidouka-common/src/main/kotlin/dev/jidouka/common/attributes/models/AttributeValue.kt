package dev.jidouka.common.attributes.models

/**
 * A value of any kind, read by `AttributeParser.Any` for attributes that legitimately send different kinds. Every
 * branch always exists, so a kind appearing later never breaks a `when`.
 */
public sealed interface AttributeValue {
    public data class Boolean(public val value: kotlin.Boolean) : AttributeValue
    public data class Number(public val value: Double) : AttributeValue
    public data class String(public val value: kotlin.String) : AttributeValue
    public data class ListOf(public val values: List<AttributeValue?>) : AttributeValue
}