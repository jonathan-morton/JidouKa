package dev.jidouka.common.usecases

import dev.jidouka.common.network.JsonManager
import dev.jidouka.common.network.models.hass.websocket.AuthorizationMessage
import dev.jidouka.common.network.repositories.MessageRepositoryDefault
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthenticationUseCaseTest {
    private val messageRepository = MessageRepositoryDefault(JsonManager())
    private val authenticationUseCase = AuthenticationUseCase(messageRepository)

    private suspend fun serialized(message: AuthorizationMessage): String =
        messageRepository.serializeAuthorizationMessage(message)

    @Test
    fun `authenticate succeeds on auth_ok`() = runTest {
        val sent = mutableListOf<String>()
        val incoming = ArrayDeque(
            listOf(
                serialized(AuthorizationMessage.Required(haVersion = haVersion)),
                serialized(AuthorizationMessage.Ok(haVersion = haVersion))
            )
        )

        val result = authenticationUseCase.authenticate(
            accessToken = accessToken,
            receiveMessage = { incoming.removeFirst() },
            sendMessage = { sent.add(it) }
        )

        assertEquals(haVersion, result.getOrThrow())
        assertEquals(1, sent.size)

        val sentMessage = messageRepository.parseAuthorizationMessage(sent.first())
        assertIs<AuthorizationMessage.Authorize>(sentMessage)
        assertEquals(accessToken, sentMessage.accessToken)
    }

    @Test
    fun `authenticate fails on auth_invalid`() = runTest {
        val incoming = ArrayDeque(
            listOf(
                serialized(AuthorizationMessage.Required(haVersion = haVersion)),
                serialized(AuthorizationMessage.Invalid(message = "Invalid password"))
            )
        )

        val result = authenticationUseCase.authenticate(accessToken, { incoming.removeFirst() }, { })

        assertTrue(result.isFailure)
        assertEquals(true, result.exceptionOrNull()?.message?.contains("Invalid password"))
    }

    @Test
    fun `authenticate fails when first message is not auth_required`() = runTest {
        val incoming = ArrayDeque(listOf(serialized(AuthorizationMessage.Ok(haVersion = haVersion))))

        val result = authenticationUseCase.authenticate(accessToken, { incoming.removeFirst() }, { })

        assertTrue(result.isFailure)
    }

    @Test
    fun `authenticate fails on malformed response`() = runTest {
        val incoming = ArrayDeque(listOf("not valid json"))

        val result = authenticationUseCase.authenticate(accessToken, { incoming.removeFirst() }, { })

        assertTrue(result.isFailure)
    }

    companion object {
        private const val accessToken = "token"
        private const val haVersion = "2026.1.0"
    }
}