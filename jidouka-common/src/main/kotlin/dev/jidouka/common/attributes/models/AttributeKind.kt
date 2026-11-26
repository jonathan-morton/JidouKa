package dev.jidouka.common.attributes.models

import java.io.Serializable

/** The kind of value an attribute getter reads. */
@Suppress("unused", "RemoveRedundantQualifierName")
public sealed interface AttributeKind : Serializable {
    public sealed interface Primitive : AttributeKind

    public data object Boolean : Primitive {
        private fun readResolve(): kotlin.Any = AttributeKind.Boolean
    }

    public data object String : Primitive {
        private fun readResolve(): kotlin.Any = AttributeKind.String
    }

    public data object Int : Primitive {
        private fun readResolve(): kotlin.Any = AttributeKind.Int
    }

    public data object Long : Primitive {
        private fun readResolve(): kotlin.Any = AttributeKind.Long
    }

    public data object Double : Primitive {
        private fun readResolve(): kotlin.Any = AttributeKind.Double
    }

    /** A list of one [Primitive] kind, so lists don't nest. A list with mixed kinds uses [Any]. */
    public data class ListOf(public val element: Primitive) : AttributeKind

    public data object Any : AttributeKind {
        private fun readResolve(): kotlin.Any = AttributeKind.Any
    }
}