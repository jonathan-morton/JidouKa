package dev.jidouka.common.usecases

import dev.jidouka.common.BaseUnitTest
import dev.jidouka.common.network.models.hass.websocket.AuthorizationMessage
import dev.jidouka.common.network.repositories.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AuthenticationUseCaseTest : BaseUnitTest() {
    @MockK
    private lateinit var messageRepository: MessageRepository

    private lateinit var authenticationUseCase: AuthenticationUseCase

    override fun setUp() {
        super.setUp()
        authenticationUseCase = AuthenticationUseCase(messageRepository)
    }

    @Test
    fun `authenticate succeeds on auth_ok`() = runTest {
        val sent = mutableListOf<String>()
        val incoming = ArrayDeque(listOf(AUTH_REQUIRED_JSON, AUTH_OK_JSON))
        coEvery { messageRepository.parseAuthorizationMessage(AUTH_REQUIRED_JSON) } returns required
        coEvery { messageRepository.serializeAuthorizationMessage(authorize) } returns AUTHORIZE_JSON
        coEvery { messageRepository.parseAuthorizationMessage(AUTH_OK_JSON) } returns ok

        val result = authenticationUseCase.authenticate(
            accessToken = ACCESS_TOKEN,
            receiveMessage = { incoming.removeFirst() },
            sendMessage = { sent.add(it) }
        )

        assertEquals(HA_VERSION, result.getOrThrow())
        assertEquals(listOf(AUTHORIZE_JSON), sent)
    }

    @Test
    fun `authenticate fails on auth_invalid`() = runTest {
        val incoming = ArrayDeque(listOf(AUTH_REQUIRED_JSON, AUTH_INVALID_JSON))
        coEvery { messageRepository.parseAuthorizationMessage(AUTH_REQUIRED_JSON) } returns required
        coEvery { messageRepository.serializeAuthorizationMessage(authorize) } returns AUTHORIZE_JSON
        coEvery { messageRepository.parseAuthorizationMessage(AUTH_INVALID_JSON) } returns invalid

        val exception = authenticationUseCase.authenticate(ACCESS_TOKEN, { incoming.removeFirst() }, { })
            .exceptionOrNull()

        assertContains(exception?.message.orEmpty(), INVALID_REASON)
    }

    @Test
    fun `authenticate fails when first message is not auth_required`() = runTest {
        coEvery { messageRepository.parseAuthorizationMessage(AUTH_OK_JSON) } returns ok

        val result = authenticationUseCase.authenticate(ACCESS_TOKEN, { AUTH_OK_JSON }, { })

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { messageRepository.serializeAuthorizationMessage(any()) }
    }

    @Test
    fun `authenticate fails on malformed response`() = runTest {
        val failure = SerializationException()
        coEvery { messageRepository.parseAuthorizationMessage(AUTH_REQUIRED_JSON) } throws failure

        val exception = authenticationUseCase.authenticate(ACCESS_TOKEN, { AUTH_REQUIRED_JSON }, { })
            .exceptionOrNull()

        assertSame(failure, exception)
    }

    private companion object {
        private const val ACCESS_TOKEN = "token"
        private const val HA_VERSION = "2026.1.0"
        private const val INVALID_REASON = "Invalid password"

        private const val AUTH_REQUIRED_JSON = "auth_required"
        private const val AUTHORIZE_JSON = "auth"
        private const val AUTH_OK_JSON = "auth_ok"
        private const val AUTH_INVALID_JSON = "auth_invalid"

        private val required = AuthorizationMessage.Required(haVersion = HA_VERSION)
        private val authorize = AuthorizationMessage.Authorize(accessToken = ACCESS_TOKEN)
        private val ok = AuthorizationMessage.Ok(haVersion = HA_VERSION)
        private val invalid = AuthorizationMessage.Invalid(message = INVALID_REASON)
    }
}
