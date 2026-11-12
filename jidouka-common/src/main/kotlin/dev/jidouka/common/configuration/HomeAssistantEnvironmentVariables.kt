package dev.jidouka.common.configuration

/**
 * Names of the environment variables Jidouka reads to connect to Home Assistant.
 */
public object HomeAssistantEnvironmentVariables {
    /** The IP address or URL of your Home Assistant server. Falls back to [HomeAssistantConfiguration.DEFAULT_HOST] */
    public const val HOST: String = "HA_HOST"

    /** The port of your Home Assistant instance. Falls back to [HomeAssistantConfiguration.DEFAULT_PORT] */
    public const val PORT: String = "HA_PORT"

    /** The long-lived access token. Required, no default */
    public const val ACCESS_TOKEN: String = "HA_ACCESS_TOKEN"
}
