package com.reqlab.feature.requests

import com.reqlab.core.model.RequestDefinition
import com.reqlab.core.network.ApiClient
import com.reqlab.core.network.NetworkEvent
import kotlinx.coroutines.flow.Flow

class RequestExecutionService(
    private val apiClient: ApiClient
) {
    fun execute(
        request: RequestDefinition,
        variableLayers: List<Map<String, String>> = emptyList()
    ): Flow<NetworkEvent> = apiClient.execute(request, variableLayers)
}
