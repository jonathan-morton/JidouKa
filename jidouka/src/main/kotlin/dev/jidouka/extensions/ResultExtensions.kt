package dev.jidouka.extensions

import kotlinx.coroutines.CancellationException

/**
 * Rethrows a [CancellationException] caught by `runCatching`, so cancelling a coroutine
 * stops it instead of coming back as a failed [Result]. Other failures are returned unchanged.
 */
internal fun <T> Result<T>.rethrowCancellation(): Result<T> {
    return onFailure { exception ->
        if (exception is CancellationException) {
            throw exception
        }
    }
}
