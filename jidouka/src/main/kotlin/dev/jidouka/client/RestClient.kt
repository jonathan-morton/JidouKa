package dev.jidouka.client

import dev.jidouka.aliases.EntityId
import dev.jidouka.configuration.HomeAssistantConfiguration
import dev.jidouka.network.JsonManager
import dev.jidouka.network.NetworkResponse
import dev.jidouka.network.models.hass.rest.StateDTO
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.koin.core.annotation.Single

internal interface RestClient {
    suspend fun getState(entityId: EntityId): NetworkResponse<StateDTO>
}

@Single
internal class HomeAssistantRestClient(
    private val client: HttpClient,
    private val configuration: HomeAssistantConfiguration,
    private val jsonManager: JsonManager
) : RestClient {

    private val logger = KotlinLogging.logger {}

    override suspend fun getState(entityId: EntityId): NetworkResponse<StateDTO> {
        logger.debug { "Fetching state for entity '$entityId' via REST" }

        val path = "/api/states/$entityId"
        return request<StateDTO>(
            method = HttpMethod.Get,
            path = path,
        )
    }

    private suspend inline fun <reified T> request(
        method: HttpMethod,
        path: String,
        block: HttpRequestBuilder.() -> Unit = {}
    ): NetworkResponse<T> {
        return try {
            val response = client.request("${configuration.baseUrl}$path") {
                this.method = method
                bearerAuth(configuration.accessToken)
                block()
            }

            if (response.status.isSuccess()) {
                logger.debug { "REST ${method.value} $path succeeded (${response.status})" }

                NetworkResponse.Success<T>(
                    data = jsonManager.json.decodeFromString<T>(response.bodyAsText()),
                    statusCode = response.status
                )
            } else {
                logger.warn { "REST ${method.value} $path failed with HTTP ${response.status}" }

                NetworkResponse.Failure(
                    statusCode = response.status,
                    error = Exception("HTTP error: ${response.status}"),
                )
            }
        } catch (exception: Exception) {
            logger.error(exception) { "REST ${method.value} $path failed with exception" }

            NetworkResponse.Failure(
                statusCode = null,
                error = exception,
            )
        }
    }

    private inline fun <reified T> parse(jsonString: String): T {
        return jsonManager.json.decodeFromString<T>(jsonString)
    }
}