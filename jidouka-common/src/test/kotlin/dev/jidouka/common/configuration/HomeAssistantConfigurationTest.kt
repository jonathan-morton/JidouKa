package dev.jidouka.common.configuration

import dev.jidouka.common.configuration.HomeAssistantConfiguration.Companion.DEFAULT_HOST
import dev.jidouka.common.configuration.HomeAssistantConfiguration.Companion.DEFAULT_PORT
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HomeAssistantConfigurationTest {
    @Test
    fun `defaults connect with http and ws on the default port`() {
        val configuration = HomeAssistantConfiguration(accessToken = ACCESS_TOKEN)

        assertEquals("http://$DEFAULT_HOST:$DEFAULT_PORT", configuration.baseUrl)
        assertEquals("ws://$DEFAULT_HOST:$DEFAULT_PORT$WEBSOCKET_PATH", configuration.websocketUrl)
    }

    @Test
    fun `secure connects with https and wss on the same port`() {
        val configuration = HomeAssistantConfiguration(host = HOST, accessToken = ACCESS_TOKEN, isSecure = true)

        assertEquals("https://$HOST:$DEFAULT_PORT", configuration.baseUrl)
        assertEquals("wss://$HOST:$DEFAULT_PORT$WEBSOCKET_PATH", configuration.websocketUrl)
    }

    @Test
    fun `default ports are left out of the URL`() {
        val plain = HomeAssistantConfiguration(host = HOST, port = HTTP_DEFAULT_PORT, accessToken = ACCESS_TOKEN)
        val secure = HomeAssistantConfiguration(
            host = HOST,
            port = HTTPS_DEFAULT_PORT,
            accessToken = ACCESS_TOKEN,
            isSecure = true
        )

        assertEquals("http://$HOST", plain.baseUrl)
        assertEquals("ws://$HOST$WEBSOCKET_PATH", plain.websocketUrl)
        assertEquals("https://$HOST", secure.baseUrl)
        assertEquals("wss://$HOST$WEBSOCKET_PATH", secure.websocketUrl)
    }

    @Test
    fun `host with an underscore is accepted`() {
        val configuration = HomeAssistantConfiguration(host = UNDERSCORE_HOST, accessToken = ACCESS_TOKEN)

        assertEquals("ws://$UNDERSCORE_HOST:$DEFAULT_PORT$WEBSOCKET_PATH", configuration.websocketUrl)
    }

    @Test
    fun `bracketed IPv6 host is accepted`() {
        val configuration = HomeAssistantConfiguration(host = IPV6_HOST, accessToken = ACCESS_TOKEN)

        assertEquals("ws://$IPV6_HOST:$DEFAULT_PORT$WEBSOCKET_PATH", configuration.websocketUrl)
    }

    @Test
    fun `host that is a URL is rejected with the settings to use`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            HomeAssistantConfiguration(host = "https://$HOST", accessToken = ACCESS_TOKEN)
        }

        assertContains(exception.message.orEmpty(), "Use host = \"$HOST\", port = $HTTPS_DEFAULT_PORT, isSecure = true")
    }

    @Test
    fun `host that is not a bare host name or IP address is rejected`() {
        invalidHosts.forEach { host ->
            assertFailsWith<IllegalArgumentException>(message = "host '$host'") {
                HomeAssistantConfiguration(host = host, accessToken = ACCESS_TOKEN)
            }
        }
    }

    @Test
    fun `connection failed hint lists the settings in use`() {
        val hint = HomeAssistantConfiguration(
            host = HOST,
            port = HTTP_DEFAULT_PORT,
            accessToken = ACCESS_TOKEN,
            isSecure = true
        ).connectionFailedHint

        assertContains(hint, "wss://$HOST:$HTTP_DEFAULT_PORT$WEBSOCKET_PATH")
        assertContains(hint, "host: $HOST (${HomeAssistantEnvironmentVariables.HOST})")
        assertContains(hint, "port: $HTTP_DEFAULT_PORT (${HomeAssistantEnvironmentVariables.PORT})")
        assertContains(hint, "isSecure: true (${HomeAssistantEnvironmentVariables.SECURE})")
    }

    private companion object {
        private const val ACCESS_TOKEN = "token"
        private const val WEBSOCKET_PATH = "/api/websocket"

        private const val HOST = "ha.example.com"
        private const val UNDERSCORE_HOST = "home_assistant"
        private const val IPV6_HOST = "[fe80::1]"

        private val HTTP_DEFAULT_PORT = URLProtocol.HTTP.defaultPort
        private val HTTPS_DEFAULT_PORT = URLProtocol.HTTPS.defaultPort

        private val invalidHosts = listOf(
            "$HOST:$DEFAULT_PORT",
            "$HOST/path",
            "user@$HOST",
            "",
            "fe80::1",
        )
    }
}
