package dev.jidouka.automations.dsl.scopes

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

public class LoggingScope(automationId: String) {
    private val logger: KLogger = KotlinLogging.logger("automation.$automationId")

    public fun trace(message: () -> String) {
        logger.trace(message)
    }

    public fun debug(message: () -> String) {
        logger.debug(message)
    }

    public fun info(message: () -> String) {
        logger.info(message)
    }

    public fun warn(message: () -> String) {
        logger.warn(message)
    }

    public fun warn(exception: Throwable, message: () -> String) {
        logger.warn(exception, message)
    }

    public fun error(message: () -> String) {
        logger.error(message)
    }

    public fun error(exception: Throwable, message: () -> String) {
        logger.error(exception, message)
    }
}
