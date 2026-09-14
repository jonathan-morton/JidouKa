package dev.jidouka.common.network.utils

import dev.jidouka.common.aliases.EntityId

public data class EntityIdParts(
    public val domainId: String,
    public val objectId: String
) {
    public companion object {
        public fun parse(entityId: EntityId): EntityIdParts {
            val idParts = entityId.split(".")

            require(idParts.size == 2) {
                "Entity ID must be in format 'domain.object_id', got: '$entityId'"
            }

            return EntityIdParts(
                domainId = idParts.first(),
                objectId = idParts.last()
            )
        }
    }
}
