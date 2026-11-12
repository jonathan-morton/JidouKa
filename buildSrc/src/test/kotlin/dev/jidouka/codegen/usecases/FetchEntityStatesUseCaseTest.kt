@file:OptIn(ExperimentalTime::class)

package dev.jidouka.codegen.usecases

import dev.jidouka.codegen.BaseUnitTest
import dev.jidouka.common.client.WebSocketRequestClient
import dev.jidouka.common.configuration.HomeAssistantConfiguration
import dev.jidouka.common.network.models.hass.websocket.HaRequest
import dev.jidouka.common.network.models.hass.websocket.StateData
import dev.jidouka.common.network.repositories.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class FetchEntityStatesUseCaseTest : BaseUnitTest() {
    @MockK
    private lateinit var webSocketRequestClient: WebSocketRequestClient

    @MockK
    private lateinit var messageRepository: MessageRepository

    private lateinit var useCase: FetchEntityStatesUseCase

    override fun setUp() {
        super.setUp()
        useCase = FetchEntityStatesUseCase(webSocketRequestClient, messageRepository)
    }

    @Test
    fun `returns the parsed states when get_states succeeds`() = runTest {
        coEvery { webSocketRequestClient.request(configuration, any()) } returns statesPayload
        coEvery { messageRepository.parseStates(statesPayload) } returns listOf(kitchenLight)

        val states = useCase.fetchStates(configuration).getOrThrow()

        assertEquals(listOf(kitchenLight), states)
    }

    @Test
    fun `sends a get_states request`() = runTest {
        coEvery { webSocketRequestClient.request(configuration, any()) } returns statesPayload
        coEvery { messageRepository.parseStates(statesPayload) } returns listOf(kitchenLight)

        useCase.fetchStates(configuration)

        coVerify { webSocketRequestClient.request(configuration, ofType<HaRequest.GetStatesRequest>()) }
    }

    @Test
    fun `fails when get_states returns no result`() = runTest {
        coEvery { webSocketRequestClient.request(configuration, any()) } returns null

        val exception = useCase.fetchStates(configuration).exceptionOrNull()

        assertIs<IllegalStateException>(exception)
        coVerify(exactly = 0) { messageRepository.parseStates(any()) }
    }

    @Test
    fun `fails with the client's exception when the request throws`() = runTest {
        val failure = IllegalStateException()
        coEvery { webSocketRequestClient.request(configuration, any()) } throws failure

        val exception = useCase.fetchStates(configuration).exceptionOrNull()

        assertSame(failure, exception)
    }

    @Test
    fun `fails with the repository's exception when parsing throws`() = runTest {
        val failure = SerializationException()
        coEvery { webSocketRequestClient.request(configuration, any()) } returns statesPayload
        coEvery { messageRepository.parseStates(statesPayload) } throws failure

        val exception = useCase.fetchStates(configuration).exceptionOrNull()

        assertSame(failure, exception)
    }

    private companion object {
        private const val ENTITY_ID = "light.kitchen"
        private const val STATE_ON = "on"
        private const val ACCESS_TOKEN = "token"

        private val configuration = HomeAssistantConfiguration(accessToken = ACCESS_TOKEN)
        private val statesPayload = JsonArray(emptyList())

        private val timestamp = Instant.fromEpochSeconds(0)
        private val kitchenLight = StateData(
            entityId = ENTITY_ID,
            state = STATE_ON,
            attributes = JsonObject(emptyMap()),
            lastChanged = timestamp,
            lastUpdated = timestamp
        )
    }
}
