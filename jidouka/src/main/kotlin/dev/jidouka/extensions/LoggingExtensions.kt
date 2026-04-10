@file:Suppress("unused")

package dev.jidouka.extensions

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.Level

private val walker = StackWalker.getInstance()

/**
 * Resolve the calling method name by walking the stack.
 * skip(1) = skip callerMethod itself
 * skip(2) = skip the extension function (debugF/infoF/etc)
 */
private fun callerMethod(skipCount: Long = 2): String {
    return walker.walk { frames ->
        frames.skip(skipCount)
            .findFirst()
            .orElse(null)
    }?.methodName ?: "?"
}

public fun KLogger.debugF(message: () -> String) {
    if (isLoggingEnabledFor(Level.DEBUG)) {
        val method = callerMethod()
        debug { "[$method] $message" }
    }
}

public fun KLogger.infoF(message: () -> String) {
    if (isLoggingEnabledFor(Level.INFO)) {
        val method = callerMethod()
        info { "[$method] $message" }
    }
}

public fun KLogger.warnF(message: () -> String) {
    if (isLoggingEnabledFor(Level.WARN)) {
        val method = callerMethod()
        warn { "[$method] $message" }
    }
}

public fun KLogger.warnF(exception: Throwable, message: () -> String) {
    if (isLoggingEnabledFor(Level.WARN)) {
        val method = callerMethod()
        warn(exception) { "[$method] $message" }
    }
}

public fun KLogger.errorF(message: () -> String) {
    if (isLoggingEnabledFor(Level.ERROR)) {
        val method = callerMethod()
        error { "[$method] $message" }
    }
}

public fun KLogger.errorF(exception: Throwable, message: () -> String) {
    if (isLoggingEnabledFor(Level.ERROR)) {
        val method = callerMethod()
        error(exception) { "[$method] $message" }
    }
}

public fun KLogger.traceF(message: () -> String) {
    if (isLoggingEnabledFor(Level.TRACE)) {
        val method = callerMethod()
        trace { "$method: ${message()}" }
    }
}