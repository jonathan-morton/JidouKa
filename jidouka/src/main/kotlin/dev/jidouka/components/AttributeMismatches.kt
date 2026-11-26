package dev.jidouka.components

import dev.jidouka.aliases.EntityId
import java.util.concurrent.ConcurrentHashMap

/** Remembers which attribute mismatches were logged, so each entity, key and kind logs once per process. */
internal class AttributeMismatches {
    private val reportedMismatches = ConcurrentHashMap.newKeySet<Mismatch>()

    /**
     * Records a mismatch so it's logged once per process.
     *
     * @param entityId The entity whose attribute didn't read
     * @param key The attribute key
     * @param kind The parser's name, from `AttributeParser.nameOf`
     * @return True the first time this entity, key and kind are reported, false after that
     */
    fun isFirstReport(
        entityId: EntityId,
        key: String,
        kind: String
    ): Boolean =
        reportedMismatches.add(Mismatch(entityId = entityId, key = key, kind = kind))

    private data class Mismatch(
        val entityId: String,
        val key: String,
        val kind: String
    )
}