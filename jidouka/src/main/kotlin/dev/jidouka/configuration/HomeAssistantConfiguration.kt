package dev.jidouka.configuration

/**
 * The configuration for your Home Assistant server
 * @param host The IP address or URL of your Home Assistant server
 * @param port The port of your Home Assistant instance
 * @param accessToken The [long-lived access token](https://developers.home-assistant.io/docs/auth_api/#long-lived-access-token) required by third-party APIs like Jidouka
 */
public data class HomeAssistantConfiguration(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val accessToken: String
) {
    internal val baseUrl: String = "http://$host:$port"
    internal val websocketPath: String = "/api/websocket"

    public companion object {
        public const val DEFAULT_HOST: String = "homeassistant.local"
        public const val DEFAULT_PORT: Int = 8123
    }
}