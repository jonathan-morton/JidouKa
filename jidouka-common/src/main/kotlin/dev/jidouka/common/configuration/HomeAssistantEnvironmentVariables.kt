package dev.jidouka.common.configuration

/**
 * Names of the environment variables Jidouka reads to connect to Home Assistant.
 */
public object HomeAssistantEnvironmentVariables {
    /** The IP address or host name of your Home Assistant server. Falls back to [HomeAssistantConfiguration.DEFAULT_HOST] */
    public const val HOST: String = "HA_HOST"

    /** The port Home Assistant listens on (Settings > System > Network > HTTP server). Falls back to [HomeAssistantConfiguration.DEFAULT_PORT] */
    public const val PORT: String = "HA_PORT"

    /** `true` to connect with `https`/`wss`, see [HomeAssistantConfiguration.isSecure]. Falls back to [HomeAssistantConfiguration.DEFAULT_IS_SECURE] */
    public const val SECURE: String = "HA_SECURE"

    /** The long-lived access token. Required, no default */
    public const val ACCESS_TOKEN: String = "HA_ACCESS_TOKEN"
}
