package dev.jidouka.common.configuration

import io.ktor.http.*

/**
 * The configuration for your Home Assistant server
 * @param host The IP address or host name of your Home Assistant server, without `http://`, a port or a path.
 * Put IPv6 addresses in brackets, such as `[fe80::1]`
 * @param port The port Home Assistant listens on, shown in Home Assistant under Settings > System > Network >
 * HTTP server. If you connect through a reverse proxy, use the proxy's port
 * @param accessToken The [long-lived access token](https://developers.home-assistant.io/docs/auth_api/#long-lived-access-token) required by third-party APIs like Jidouka
 * @param isSecure `true` if Home Assistant has SSL/TLS set up under Settings > System > Network > HTTP server, or you
 * connect through an `https` reverse proxy or remote-access URL. Jidouka then connects with `https` and `wss` instead
 * of `http` and `ws`
 * @throws IllegalArgumentException if [host] isn't a bare host name or IP address, for example if it includes
 * `https://`, a port or a path
 */
public data class HomeAssistantConfiguration(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val accessToken: String,
    val isSecure: Boolean = DEFAULT_IS_SECURE
) {

    private val httpProtocol: URLProtocol = if (isSecure) {
        URLProtocol.HTTPS
    } else {
        URLProtocol.HTTP
    }
    private val websocketProtocol: URLProtocol = if (isSecure) {
        URLProtocol.WSS
    } else {
        URLProtocol.WS
    }

    internal val baseUrl = URLBuilder(
        protocol = httpProtocol,
        host = host,
        port = port
    ).buildString()

    internal val websocketUrl = URLBuilder(
        protocol = websocketProtocol,
        host = host,
        port = port
    ).apply { path("api", "websocket") }
        .buildString()

    init {
        parseUrl(host)?.let { url ->
            throw IllegalArgumentException(
                "host must not be a URL, got '$host'. Use host = \"${url.host}\", port = ${url.port}, isSecure = ${url.protocol.isSecure()}"
            )
        }
        require(parseUrl(websocketUrl)?.host == host) {
            "host must be a host name or IP address without a port or path, got '$host'"
        }
    }

    /** Shown when Jidouka can't reach Home Assistant at startup, by the runtime and by codegen */
    public val connectionFailedHint: String = """
        Could not connect to Home Assistant at $websocketUrl. Check these settings:
          host: $host (${HomeAssistantEnvironmentVariables.HOST})
          port: $port (${HomeAssistantEnvironmentVariables.PORT})
          isSecure: $isSecure (${HomeAssistantEnvironmentVariables.SECURE})
        Find the port and SSL/TLS settings in Home Assistant under Settings > System > Network > HTTP server.
    """.trimIndent()

    public companion object {
        public const val DEFAULT_HOST: String = "homeassistant.local"
        public const val DEFAULT_PORT: Int = 8123
        public const val DEFAULT_IS_SECURE: Boolean = false
    }
}
