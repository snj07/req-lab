package com.reqlab.core.storage

import com.reqlab.core.model.CollectionDefinition
import com.reqlab.core.model.EnvironmentDefinition
import com.reqlab.core.model.HistoryEntry
import com.reqlab.core.model.RequestDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryRequestRepository : RequestRepository {
    private val state = MutableStateFlow<Map<String, RequestDefinition>>(emptyMap())

    override fun observeAll(): Flow<List<RequestDefinition>> = state.map { values ->
        values.values.sortedBy { it.updatedAtEpochMillis }
    }

    override suspend fun upsert(request: RequestDefinition) {
        state.value = state.value + (request.id to request)
    }

    override suspend fun delete(requestId: String) {
        state.value = state.value - requestId
    }
}

class InMemoryCollectionRepository : CollectionRepository {
    private val state = MutableStateFlow<Map<String, CollectionDefinition>>(emptyMap())

    override fun observeAll(): Flow<List<CollectionDefinition>> = state.map { values ->
        values.values.sortedBy { it.updatedAtEpochMillis }
    }

    override suspend fun upsert(collection: CollectionDefinition) {
        state.value = state.value + (collection.id to collection)
    }

    override suspend fun delete(collectionId: String) {
        state.value = state.value - collectionId
    }
}

class InMemoryEnvironmentRepository : EnvironmentRepository {
    private val state = MutableStateFlow<Map<String, EnvironmentDefinition>>(emptyMap())

    override fun observeAll(): Flow<List<EnvironmentDefinition>> = state.map { values ->
        values.values.sortedBy { it.updatedAtEpochMillis }
    }

    override suspend fun upsert(environment: EnvironmentDefinition) {
        state.value = state.value + (environment.id to environment)
    }

    override suspend fun delete(environmentId: String) {
        state.value = state.value - environmentId
    }
}

class InMemoryHistoryRepository : HistoryRepository {
    private val state = MutableStateFlow<List<HistoryEntry>>(emptyList())

    override fun observeRecent(limit: Int): Flow<List<HistoryEntry>> = state.map { values ->
        values.sortedByDescending { it.executedAtEpochMillis }.take(limit)
    }

    override suspend fun append(entry: HistoryEntry) {
        state.value = state.value + entry
    }

    override suspend fun clear() {
        state.value = emptyList()
    }
}
