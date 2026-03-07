package com.reqlab.ui.desktop.components

import com.reqlab.core.model.HttpMethodType
import com.reqlab.ui.desktop.state.CollectionNode
import androidx.compose.runtime.mutableStateListOf
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for sidebar collection-manipulation helper functions.
 * These functions are package-private in Sidebar.kt so we test them via a test
 * file in the same package.
 */
class SidebarCollectionHelpersTest {

    private fun sampleCollections(): MutableList<CollectionNode> = mutableStateListOf(
        CollectionNode("c1", "Users API", isFolder = true, children = mutableStateListOf(
            CollectionNode("r1", "Get all users", method = HttpMethodType.GET, url = "/users"),
            CollectionNode("r2", "Create user", method = HttpMethodType.POST, url = "/users"),
            CollectionNode("r3", "Update user", method = HttpMethodType.PUT, url = "/users/1"),
        )),
        CollectionNode("c2", "Auth", isFolder = true, children = mutableStateListOf(
            CollectionNode("r4", "Login", method = HttpMethodType.POST, url = "/auth/login"),
        )),
    )

    // ── deleteRequestFromCollections ──

    @Test
    fun deleteRequest_removes_request_from_parent() {
        val collections = sampleCollections()
        val result = deleteRequestFromCollections(collections, "r2")
        assertTrue(result)
        assertEquals(2, collections[0].children.size)
        assertNull(collections[0].children.firstOrNull { it.id == "r2" })
    }

    @Test
    fun deleteRequest_returns_false_for_nonexistent_id() {
        val collections = sampleCollections()
        assertFalse(deleteRequestFromCollections(collections, "nonexistent"))
    }

    // ── renameRequestInCollections ──

    @Test
    fun renameRequest_changes_name() {
        val collections = sampleCollections()
        renameRequestInCollections(collections, "r1", "List Users v2")
        assertEquals("List Users v2", collections[0].children.first().name)
    }

    @Test
    fun renameRequest_no_op_for_nonexistent_id() {
        val collections = sampleCollections()
        renameRequestInCollections(collections, "nonexistent", "New Name")
        // Original names unchanged
        assertEquals("Get all users", collections[0].children.first().name)
    }

    // ── moveRequestInCollections ──

    @Test
    fun moveRequest_down_swaps_with_next_sibling() {
        val collections = sampleCollections()
        val result = moveRequestInCollections(collections, "r1", 1)
        assertTrue(result)
        assertEquals("r2", collections[0].children[0].id)
        assertEquals("r1", collections[0].children[1].id)
    }

    @Test
    fun moveRequest_up_swaps_with_previous_sibling() {
        val collections = sampleCollections()
        val result = moveRequestInCollections(collections, "r2", -1)
        assertTrue(result)
        assertEquals("r2", collections[0].children[0].id)
        assertEquals("r1", collections[0].children[1].id)
    }

    @Test
    fun moveRequest_at_top_cannot_move_up() {
        val collections = sampleCollections()
        val result = moveRequestInCollections(collections, "r1", -1)
        assertFalse(result)
        assertEquals("r1", collections[0].children[0].id)
    }

    @Test
    fun moveRequest_at_bottom_cannot_move_down() {
        val collections = sampleCollections()
        val result = moveRequestInCollections(collections, "r3", 1)
        assertFalse(result)
        assertEquals("r3", collections[0].children[2].id)
    }

    @Test
    fun moveRequest_beforeRequest_reorders_correctly() {
        val collections = sampleCollections()
        val result = moveRequestBeforeRequest(collections, "r3", "r1")
        assertTrue(result)
        assertEquals("r3", collections[0].children[0].id)
        assertEquals("r1", collections[0].children[1].id)
    }

    @Test
    fun moveRequest_afterRequest_places_source_after_target() {
        val collections = sampleCollections()
        // Move r1 to after r3 (last in collection)
        val result = moveRequestAfterRequest(collections, "r1", "r3")
        assertTrue(result)
        assertEquals("r2", collections[0].children[0].id)
        assertEquals("r3", collections[0].children[1].id)
        assertEquals("r1", collections[0].children[2].id)
    }

    @Test
    fun moveRequest_afterRequest_moves_down_by_one() {
        val collections = sampleCollections()
        // Move r1 after r2 — should end up in position 1
        val result = moveRequestAfterRequest(collections, "r1", "r2")
        assertTrue(result)
        assertEquals(3, collections[0].children.size)
        assertEquals("r2", collections[0].children[0].id)
        assertEquals("r1", collections[0].children[1].id)
        assertEquals("r3", collections[0].children[2].id)
    }

    @Test
    fun moveRequest_afterRequest_returns_false_for_nonexistent_source() {
        val collections = sampleCollections()
        assertFalse(moveRequestAfterRequest(collections, "nonexistent", "r1"))
    }

    @Test
    fun moveRequest_toDifferentCollection_moves_node() {
        val collections = sampleCollections()
        val result = moveRequestToCollection(collections, "r1", "c2")
        assertTrue(result)
        assertEquals(2, collections[0].children.size)
        assertEquals(2, collections[1].children.size)
        assertEquals("r1", collections[1].children.last().id)
    }

    // ── duplicateRequestInCollections ──

    @Test
    fun duplicateRequest_creates_copy_next_to_original() {
        val collections = sampleCollections()
        val result = duplicateRequestInCollections(collections, "r1")
        assertNotNull(result)
        assertTrue(result.contains("Copy"))
        assertEquals(4, collections[0].children.size)
        assertEquals(result, collections[0].children[1].name)
    }

    @Test
    fun duplicateRequest_returns_null_for_nonexistent_id() {
        val collections = sampleCollections()
        assertNull(duplicateRequestInCollections(collections, "nonexistent"))
    }

    // ── filterCollectionNode ──

    @Test
    fun filterCollectionNode_returns_matching_children() {
        val collection = sampleCollections()[0]
        val filtered = filterCollectionNode(collection, "update")
        assertNotNull(filtered)
        assertEquals(1, filtered.children.size)
        assertEquals("Update user", filtered.children[0].name)
    }

    @Test
    fun filterCollectionNode_returns_null_when_no_match() {
        val collection = sampleCollections()[0]
        val filtered = filterCollectionNode(collection, "zzz-no-match")
        assertNull(filtered)
    }

    @Test
    fun filterCollectionNode_returns_all_for_empty_query() {
        val collection = sampleCollections()[0]
        val filtered = filterCollectionNode(collection, "")
        assertNotNull(filtered)
        assertEquals(3, filtered.children.size)
    }
}
