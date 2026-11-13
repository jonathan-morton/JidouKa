package dev.jidouka.codegen.di

import dev.jidouka.codegen.usecases.FetchEntityStatesUseCase
import dev.jidouka.common.client.WebSocketRequestClient
import dev.jidouka.common.network.JsonManager
import dev.jidouka.common.network.repositories.MessageRepository
import dev.jidouka.common.network.repositories.MessageRepositoryDefault
import dev.jidouka.common.usecases.AuthenticationUseCase
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

internal val CodegenModule = module {
    singleOf(::JsonManager)
    singleOf(::MessageRepositoryDefault) bind MessageRepository::class
    singleOf(::AuthenticationUseCase)
    singleOf(::WebSocketRequestClient)
    singleOf(::FetchEntityStatesUseCase)
}
