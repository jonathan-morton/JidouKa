package dev.jidouka.network.repositories

import dev.jidouka.aliases.EntityId
import dev.jidouka.client.RestClient
import dev.jidouka.components.StateObject
import dev.jidouka.network.NetworkResponse
import org.koin.core.annotation.Single

internal interface StateRepository {
    suspend fun fetchState(entityId: EntityId): NetworkResponse<StateObject>
}

@Single
internal class StateRepositoryRest(
    private val restClient: RestClient
) : StateRepository {

    override suspend fun fetchState(entityId: EntityId): NetworkResponse<StateObject> {
        return when (val response = restClient.getState(entityId)) {
            is NetworkResponse.Success -> NetworkResponse.Success(
                data = response.data.toStateObject(),
                statusCode = response.statusCode

            )

            is NetworkResponse.Failure -> response
        }
    }
}