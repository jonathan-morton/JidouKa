package dev.jidouka

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

internal fun HttpClientConfig<CIOEngineConfig>.configureSerialization() {
    install(ContentNegotiation) {
        json()
    }
}