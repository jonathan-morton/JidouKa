package dev.jidouka.common.usecases.exceptions

/** Home Assistant answered and rejected the access token */
public class AuthenticationFailedException(message: String) : Exception(message)
