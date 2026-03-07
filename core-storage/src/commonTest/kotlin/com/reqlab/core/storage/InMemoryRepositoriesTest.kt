package com.reqlab.core.storage

import com.reqlab.core.model.CollectionDefinition
import com.reqlab.core.model.EnvironmentDefinition
import com.reqlab.core.model.HistoryEntry
import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.model.RequestDefinition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryRepositoriesTest {

    @Test
    fun request_repository_supports_upsert_and_delete() = runTest {
        val repository = InMemoryRequestRepository()
        val request = sampleRequest("req-1")
        repository.upsert(request)
        assertEquals(1, repository.observeAll().first().size)

        repository.delete("req-1")
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun collection_repository_supports_basic_crud() = runTest {
        val repository = InMemoryCollectionRepository()
        repository.upsert(
            CollectionDefinition(
                id = "col-1",
                workspaceId = "ws-1",
                name = "Users",
                createdAtEpochMillis = 1,
                updatedAtEpochMillis = 1
            )
        )
        repository.upsert(
            CollectionDefinition(
                id = "col-1",
                workspaceId = "ws-1",
                name = "Users Updated",
                createdAtEpochMillis = 1,
                updatedAtEpochMillis = 2
            )
        )

        val list = repository.observeAll().first()
        assertEquals(1, list.size)
        assertEquals("Users Updated", list.first().name)
    }

    @Test
    fun environment_repository_supports_persistence_semantics() = runTest {
        val repository = InMemoryEnvironmentRepository()
        repository.upsert(
            EnvironmentDefinition(
                id = "env-dev",
                workspaceId = "ws-1",
                name = "Development",
                createdAtEpochMillis = 10,
                updatedAtEpochMillis = 10
            )
        )
        assertEquals("Development", repository.observeAll().first().first().name)
    }

    @Test
    fun history_repository_stores_and_orders_recent_requests() = runTest {
        val repository = InMemoryHistoryRepository()
        repository.append(
            HistoryEntry(
                id = "h-1",
                requestId = "r-1",
                requestSnapshot = sampleRequest("r-1"),
                responseSnapshot = null,
                executedAtEpochMillis = 100
            )
        )
        repository.append(
            HistoryEntry(
                id = "h-2",
                requestId = "r-2",
                requestSnapshot = sampleRequest("r-2"),
                responseSnapshot = null,
                executedAtEpochMillis = 200
            )
        )

        val history = repository.observeRecent(limit = 10).first()
        assertEquals(listOf("h-2", "h-1"), history.map { it.id })

        repository.clear()
        assertTrue(repository.observeRecent().first().isEmpty())
    }

    private fun sampleRequest(id: String) = RequestDefinition(
        id = id,
        name = id,
        method = HttpMethodType.GET,
        url = "https://example.com",
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1
    )
}
