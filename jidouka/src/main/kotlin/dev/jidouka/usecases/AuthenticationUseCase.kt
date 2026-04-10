package dev.jidouka.usecases

import dev.jidouka.network.models.hass.websocket.AuthorizationMessage
import dev.jidouka.network.repositories.MessageRepository
import org.koin.core.annotation.Single

@Single
internal class AuthenticationUseCase(
    private val messageRepository: MessageRepository
) {
    suspend fun authenticate(
        accessToken: String,
        receiveMessage: suspend () -> String,
        sendMessage: suspend (String) -> Unit
    ): Result<String> {
        return try {
            val authorizationRequiredJson = receiveMessage()
            val message = messageRepository.parseAuthorizationMessage(authorizationRequiredJson)
            if (message !is AuthorizationMessage.Required) {
                return Result.failure(Exception("Expected auth_required, got: ${message.type}"))
            }

            val requestJson = messageRepository.serializeAuthorizationMessage(
                AuthorizationMessage.Authorize(accessToken)
            )
            sendMessage(requestJson)

            val responseJson = receiveMessage()
            val response = messageRepository.parseAuthorizationMessage(responseJson)

            when (response) {
                is AuthorizationMessage.Ok -> Result.success(response.haVersion)
                is AuthorizationMessage.Invalid -> Result.failure(
                    Exception("Authentication failed: ${response.message}")
                )

                is AuthorizationMessage.Authorize,
                is AuthorizationMessage.Required -> Result.failure(
                    IllegalStateException("Unexpected response during authentication: ${response.type}")
                )
            }
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }
}