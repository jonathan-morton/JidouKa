package dev.jidouka.codegen.usecases

import dev.jidouka.common.client.WebSocketRequestClient
import dev.jidouka.common.configuration.HomeAssistantConfiguration
import dev.jidouka.common.network.models.hass.websocket.HaRequest
import dev.jidouka.common.network.models.hass.websocket.StateData
import dev.jidouka.common.network.repositories.MessageRepository
import kotlinx.coroutines.CancellationException

public class FetchEntityStatesUseCase(
    private val webSocketRequestClient: WebSocketRequestClient,
    private val messageRepository: MessageRepository
) {
    public suspend fun fetchStates(configuration: HomeAssistantConfiguration): Result<List<StateData>> {
        return try {
            val initialId = 1
            val result = webSocketRequestClient.request(
                configuration,
                request = HaRequest.GetStatesRequest(id = initialId)
            ) ?: return Result.failure(IllegalStateException("get_states returned no result"))

            Result.success(messageRepository.parseStates(result))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }
}
