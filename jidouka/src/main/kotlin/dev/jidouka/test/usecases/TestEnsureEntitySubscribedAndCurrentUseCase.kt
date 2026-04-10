package dev.jidouka.test.usecases

import dev.jidouka.aliases.EntityId
import dev.jidouka.usecases.EnsureEntitySubscribedAndCurrentUseCase
import io.github.oshai.kotlinlogging.KotlinLogging

internal class TestEnsureEntitySubscribedAndCurrentUseCase : EnsureEntitySubscribedAndCurrentUseCase {
    private val logger = KotlinLogging.logger {}

    override suspend fun ensure(entityId: EntityId, automationId: String) {
        logger.debug { "Test ensure for entity '$entityId' in automation '$automationId'" }
        //States are set manually with InMemoryStateRegistry
    }
}