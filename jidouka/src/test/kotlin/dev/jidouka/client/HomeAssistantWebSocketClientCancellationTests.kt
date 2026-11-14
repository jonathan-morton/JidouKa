package dev.jidouka.client

import dev.jidouka.BaseUnitTest
import dev.jidouka.actions.ActionResponse
import dev.jidouka.common.network.models.hass.websocket.HaRequest
import dev.jidouka.common.network.models.hass.websocket.StateData
import dev.jidouka.common.network.repositories.MessageRepository
import io.ktor.websocket.*
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeAssistantWebSocketClientCancellationTests : BaseUnitTest() {
    @MockK
    private lateinit var messageRepository: MessageRepository

    @MockK
    private lateinit var session: WebSocketSession

    private lateinit var sentRequests: MutableList<HaRequest>

    private lateinit var client: HomeAssistantWebSocketClient

    override fun setUp() {
        super.setUp()
        sentRequests = mutableListOf()
        coEvery { messageRepository.serializeRequest(capture(sentRequests)) } returns "{}"
        coEvery { session.send(any()) } just Runs

        client = HomeAssistantWebSocketClient(
            authenticationUseCase = mockk(),
            stateRegistry = mockk(),
            messageRepository = messageRepository,
            eventRegistry = mockk(),
            webhookRegistry = mockk(),
        )
        client.session = session
    }

    private suspend fun callLight(action: String) = client.callServiceAction(
        domain = "light",
        service = action,
        target = null,
        serviceData = null,
        returnResponse = false
    )

    @Test
    fun `cancelling while waiting for a service call reply stops the next call from being sent`() = runTestScope {
        val job = launch {
            callLight("turn_on")
            callLight("turn_off")
        }
        runCurrent()
        assertEquals(1, sentRequests.size)

        job.cancelAndJoin()

        assertEquals(1, sentRequests.size, "The cancelled job sent another request")
    }

    @Test
    fun `cancelling while waiting for a service call reply throws instead of returning a failure`() = runTestScope {
        var result: Result<ActionResponse?>? = null
        val job = launch { result = callLight("turn_on") }
        runCurrent()

        job.cancelAndJoin()

        assertNull(result, "Cancellation came back as a Result instead of being thrown")
    }

    @Test
    fun `cancelling while waiting for get_states throws instead of returning a failure`() = runTestScope {
        var result: Result<List<StateData>>? = null
        val job = launch { result = client.getStates() }
        runCurrent()

        job.cancelAndJoin()

        assertNull(result)
    }

    @Test
    fun `an already cancelled job sends nothing`() = runTestScope {
        val job = launch {
            cancel()
            callLight("turn_on")
        }

        job.join()

        assertTrue(sentRequests.isEmpty(), "A cancelled job sent a request")
    }

    @Test
    fun `a disconnect while waiting still returns a failure result`() = runTestScope {
        var result: Result<ActionResponse?>? = null
        launch { result = callLight("turn_on") }
        runCurrent()

        client.disconnect()
        runCurrent()

        assertIs<IllegalStateException>(result?.exceptionOrNull())
    }
}
