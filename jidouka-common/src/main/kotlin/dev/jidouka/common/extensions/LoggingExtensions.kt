@file:Suppress("unused")

package dev.jidouka.common.extensions

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.Level
import java.lang.invoke.MethodHandles

private val walker = StackWalker.getInstance()
private val loggingExtensionsClassName = MethodHandles.lookup().lookupClass().name
/**
 * Resolve the calling method name by walking the stack
 */
private fun callerMethod(): String {
    return walker.walk { frames ->
        frames.dropWhile { it.className == loggingExtensionsClassName }
            .findFirst()
            .orElse(null)
    }?.methodName ?: "?"
}

/**
 * Build the line as `[callerMethod] message`.
 * Call it before the logger call, not inside the logger's lambda: the logger runs that lambda
 * inside its own frames, and [callerMethod] would report a logging-library method.
 */
private fun callerMessage(buildMessage: () -> String): String {
    return "[${callerMethod()}] ${buildMessage()}"
}

internal fun KLogger.debugF(buildMessage: () -> String) {
    if (isLoggingEnabledFor(Level.DEBUG)) {
        val messageLine = callerMessage(buildMessage)
        debug { messageLine }
    }
}

internal fun KLogger.infoF(buildMessage: () -> String) {
    if (isLoggingEnabledFor(Level.INFO)) {
        val messageLine = callerMessage(buildMessage)
        info { messageLine }
    }
}

internal fun KLogger.warnF(buildMessage: () -> String) {
    if (isLoggingEnabledFor(Level.WARN)) {
        val messageLine = callerMessage(buildMessage)
        warn { messageLine }
    }
}

internal fun KLogger.warnF(exception: Throwable, buildMessage: () -> String) {
    if (isLoggingEnabledFor(Level.WARN)) {
        val messageLine = callerMessage(buildMessage)
        warn(exception) { messageLine }
    }
}

internal fun KLogger.errorF(buildMessage: () -> String) {
    if (isLoggingEnabledFor(Level.ERROR)) {
        val messageLine = callerMessage(buildMessage)
        error { messageLine }
    }
}

internal fun KLogger.errorF(exception: Throwable, buildMessage: () -> String) {
    if (isLoggingEnabledFor(Level.ERROR)) {
        val messageLine = callerMessage(buildMessage)
        error(exception) { messageLine }
    }
}

internal fun KLogger.traceF(buildMessage: () -> String) {
    if (isLoggingEnabledFor(Level.TRACE)) {
        val messageLine = callerMessage(buildMessage)
        trace { messageLine }
    }
}
