package dev.jidouka.api.utilities

internal object EnvironmentUtilities {
    fun env(key: String): String? = System.getenv(key)

    fun envOrThrow(key: String) = env(key)
        ?: error("Environment variable '$key' is required but not set")
}