package dev.jidouka.actions

import dev.jidouka.aliases.EntityId
import dev.jidouka.components.Entity
import dev.jidouka.network.models.hass.websocket.ActionTarget

/**
 * Builder for creating the targets of a Home Assistant service action call
 */
public class ActionTargetBuilder {
    private val entityIds = mutableSetOf<String>()
    private val areaIds = mutableSetOf<String>()
    private val deviceIds = mutableSetOf<String>()
    private val floorIds = mutableSetOf<String>()
    private val labelIds = mutableSetOf<String>()

    // region Entities
    public fun entity(id: EntityId) {
        entityIds.add(id)
    }

    @JvmName("entitiesFromIds")
    public fun entities(ids: Collection<EntityId>) {
        entityIds.addAll(ids)
    }

    public fun entity(entity: Entity<*>) {
        entity(entity.entityId)
    }


    @JvmName("entitiesFromObjects")
    public fun entities(entities: Collection<Entity<*>>) {
        entities(entities.map { it.entityId })
    }
    //endregion

    //region Areas
    public fun area(id: String) {
        areaIds.add(id)
    }

    public fun areas(ids: Collection<String>) {
        areaIds.addAll(ids)
    }
    //endregion

    //region Devices
    public fun device(id: String) {
        deviceIds.add(id)
    }

    public fun devices(ids: Collection<String>) {
        deviceIds.addAll(ids)
    }
    //endregion

    //region Floors
    public fun floor(id: String) {
        floorIds.add(id)
    }

    public fun floors(ids: Collection<String>) {
        floorIds.addAll(ids)
    }
    //endregion

    //region Labels
    public fun label(id: String) {
        labelIds.add(id)
    }

    public fun labels(ids: Collection<String>) {
        labelIds.addAll(ids)
    }
    //endregion

    internal fun build(): ActionTarget {
        require(
            entityIds.isNotEmpty()
                    || areaIds.isNotEmpty()
                    || deviceIds.isNotEmpty()
                    || floorIds.isNotEmpty()
                    || labelIds.isNotEmpty()
        ) {
            "Action target must specify at least one entity, area, device, floor, or label"
        }

        return ActionTarget(
            entityIds = entityIds.toList().takeIf { it.isNotEmpty() },
            areaIds = areaIds.toList().takeIf { it.isNotEmpty() },
            deviceIds = deviceIds.toList().takeIf { it.isNotEmpty() },
            floorIds = floorIds.toList().takeIf { it.isNotEmpty() },
            labelIds = labelIds.toList().takeIf { it.isNotEmpty() }
        )
    }
}