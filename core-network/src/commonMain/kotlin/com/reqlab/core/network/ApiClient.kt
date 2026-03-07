package com.reqlab.core.network

import com.reqlab.core.model.RequestDefinition
import kotlinx.coroutines.flow.Flow

interface ApiClient {
    fun execute(
        request: RequestDefinition,
        variableLayers: List<Map<String, String>> = emptyList()
    ): Flow<NetworkEvent>
}
