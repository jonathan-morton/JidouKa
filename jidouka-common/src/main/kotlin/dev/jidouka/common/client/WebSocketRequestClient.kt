package dev.jidouka.common.client

import dev.jidouka.common.configuration.HomeAssistantConfiguration
import dev.jidouka.common.network.models.hass.websocket.EventResponse
import dev.jidouka.common.network.models.hass.websocket.HaRequest
import dev.jidouka.common.network.models.hass.websocket.HaResponse
import dev.jidouka.common.network.models.hass.websocket.ResultResponse
import dev.jidouka.common.network.repositories.MessageRepository
import dev.jidouka.common.usecases.AuthenticationUseCase
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.JsonElement

public class WebSocketRequestClient(
    private val messageRepository: MessageRepository,
    private val authenticationUseCase: AuthenticationUseCase
) {
    public suspend fun request(
        configuration: HomeAssistantConfiguration,
        request: HaRequest
    ): JsonElement? {
        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        client.use { client ->
            var result: JsonElement? = null

            client.webSocket(
                host = configuration.host,
                port = configuration.port,
                path = configuration.websocketPath
            ) {
                suspend fun receiveTextFrame(): String {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            return frame.readText()
                        }
                    }
                    throw IllegalStateException("Connection closed before a text frame was received")
                }

                authenticationUseCase.authenticate(
                    accessToken = configuration.accessToken,
                    receiveMessage = { receiveTextFrame() },
                    sendMessage = { json ->
                        send(Frame.Text(json))
                    }
                ).getOrThrow()

                send(
                    Frame.Text(
                        messageRepository.serializeRequest(request = request)
                    )
                )

                result = when (val response = messageRepository.parseMessage(receiveTextFrame())) {
                    is ResultResponse.Success -> response.result
                    is ResultResponse.Error -> throw IllegalStateException(
                        "Request '${request.messageType}' failed: ${response.code}: ${response.message}"
                    )

                    is HaRequest,
                    is EventResponse,
                    is HaResponse -> throw IllegalStateException(
                        "Unexpected response to '${request.messageType}' : ${response.messageType}"
                    )
                }
            }
            return result
        }
    }
}