package dev.jidouka.network

import io.ktor.http.*

internal sealed class NetworkResponse<out T> {
    abstract val statusCode: HttpStatusCode?

    internal data class Success<T>(
        val data: T,
        override val statusCode: HttpStatusCode
    ) : NetworkResponse<T>()

    internal data class Failure(
        override val statusCode: HttpStatusCode?,
        val error: Throwable
    ) : NetworkResponse<Nothing>()
}