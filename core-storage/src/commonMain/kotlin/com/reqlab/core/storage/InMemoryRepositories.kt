package com.reqlab.core.storage

import com.reqlab.core.model.CollectionDefinition
import com.reqlab.core.model.EnvironmentDefinition
import com.reqlab.core.model.HistoryEntry
import com.reqlab.core.model.RequestDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

// ── Shared base ──────────────────────────────────────────────────

/**
 * Generic in-memory store backed by a [MutableStateFlow] of a keyed map.
 *
 * @param T        Entity type.
 * @param getKey   Extracts the unique string key from an entity.
 * @param sortKey  Returns the sort value used by [observeAllSorted]; lower values come first.
 */
abstract class InMemoryMapRepository<T>(
    private val getKey: (T) -> String,
    private val sortKey: (T) -> Long,
) {
    protected val store = MutableStateFlow<Map<String, T>>(emptyMap())

    fun observeAllSorted(): Flow<List<T>> = store.map { m ->
        m.values.sortedBy { sortKey(it) }
    }

    suspend fun upsertEntity(entity: T) {
        store.value = store.value + (getKey(entity) to entity)
    }

    suspend fun deleteEntity(id: String) {
        store.value = store.value - id
    }
}

// ── Concrete repositories ────────────────────────────────────────

class InMemoryRequestRepository : InMemoryMapRepository<RequestDefinition>(
    getKey = { it.id },
    sortKey = { it.updatedAtEpochMillis },
), RequestRepository {
    override fun observeAll() = observeAllSorted()
    override suspend fun upsert(request: RequestDefinition) = upsertEntity(request)
    override suspend fun delete(requestId: String) = deleteEntity(requestId)
}

class InMemoryCollectionRepository : InMemoryMapRepository<CollectionDefinition>(
    getKey = { it.id },
    sortKey = { it.updatedAtEpochMillis },
), CollectionRepository {
    override fun observeAll() = observeAllSorted()
    override suspend fun upsert(collection: CollectionDefinition) = upsertEntity(collection)
    override suspend fun delete(collectionId: String) = deleteEntity(collectionId)
}

class InMemoryEnvironmentRepository : InMemoryMapRepository<EnvironmentDefinition>(
    getKey = { it.id },
    sortKey = { it.updatedAtEpochMillis },
), EnvironmentRepository {
    override fun observeAll() = observeAllSorted()
    override suspend fun upsert(environment: EnvironmentDefinition) = upsertEntity(environment)
    override suspend fun delete(environmentId: String) = deleteEntity(environmentId)
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
