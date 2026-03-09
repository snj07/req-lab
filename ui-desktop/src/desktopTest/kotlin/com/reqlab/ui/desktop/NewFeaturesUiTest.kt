package com.reqlab.ui.desktop

import com.reqlab.ui.shared.MainScreen

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.reqlab.core.model.KeyValueEntry
import com.reqlab.core.model.ResponseDefinition
import com.reqlab.core.model.ResponseMetrics
import com.reqlab.ui.shared.state.AppState
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class NewFeaturesUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── Timing Tab ──

    @Test
    fun timing_tab_appears_in_response_tabs() {
        val state = AppState().apply {
            activeTab?.response = sampleResponse()
        }
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithText("Timing").assertIsDisplayed()
    }

    @Test
    fun timing_view_shows_breakdown_when_phases_available() {
        val state = AppState().apply {
            activeTab?.response = sampleResponse(serverMs = 80, downloadMs = 20)
        }
        composeRule.setContent { MainScreen(state) }

        // Navigate to Timing tab
        composeRule.onNodeWithText("Timing").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("response-timing-view").assertIsDisplayed()
        composeRule.onNodeWithText("Server Processing").assertIsDisplayed()
        composeRule.onNodeWithText("Content Download").assertIsDisplayed()
    }

    @Test
    fun timing_view_shows_not_available_when_no_phases() {
        val state = AppState().apply {
            activeTab?.response = sampleResponse()
        }
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithText("Timing").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("response-timing-view").assertIsDisplayed()
        composeRule.onNodeWithText("Detailed timing phases are not available for this response.")
            .assertIsDisplayed()
    }

    // ── Collection "+" button ──

    @Test
    fun add_button_exists_for_each_collection() {
        val state = AppState(withDemoData = true)
        composeRule.setContent { MainScreen(state) }

        // Each collection should have an add button with testTag
        val firstCollectionId = state.collections.first().id
        composeRule.onNodeWithTag("collection-add-$firstCollectionId").assertIsDisplayed()
    }

    // ── Context menu icons ──

    @Test
    fun collection_context_menu_shows_actions() {
        val state = AppState(withDemoData = true)
        composeRule.setContent { MainScreen(state) }

        // Click the "..." button for the first collection
        val firstCollectionId = state.collections.first().id
        composeRule.onNodeWithTag("collection-actions-$firstCollectionId").performClick()
        composeRule.waitForIdle()

        // Verify menu items appear with icons
        composeRule.onNodeWithText("Add Folder").assertIsDisplayed()
        composeRule.onNodeWithText("Add Request").assertIsDisplayed()
        composeRule.onNodeWithText("Export Collection").assertIsDisplayed()
        composeRule.onNodeWithText("Duplicate Collection").assertIsDisplayed()
        composeRule.onNodeWithText("Rename").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun add_subfolder_from_context_menu_adds_node_to_tree() {
        val state = AppState(withDemoData = true).apply { settings.confirmBeforeDelete = false }
        val rootId = state.collections.first().id
        val before = state.collections.first().children.count { it.isFolder }
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("collection-actions-$rootId", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Add Folder", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("rename-dialog-save", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        val created = state.collections.first().children.last { it.isFolder }
        assertTrue(state.collections.first().children.count { it.isFolder } == before + 1)
        composeRule.onNodeWithTag("collection-node-${created.id}", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun delete_subfolder_from_context_menu_removes_node_from_tree() {
        val state = AppState(withDemoData = true).apply { settings.confirmBeforeDelete = false }
        val root = state.collections.first()
        val folder = com.reqlab.ui.shared.components.addSubfolderInCollections(state.collections, root.id, "Temp Folder")
            ?: error("Folder creation failed")
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("collection-actions-${folder.id}", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertTrue(state.collections.first().children.none { it.id == folder.id })
        composeRule.onAllNodesWithTag("collection-node-${folder.id}", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun subfolder_icon_is_distinct_from_root_collection_icon() {
        val state = AppState(withDemoData = true)
        val root = state.collections.first()
        val sub = com.reqlab.ui.shared.components.addSubfolderInCollections(state.collections, root.id, "Nested")
            ?: error("Folder creation failed")

        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("collection-root-icon-${root.id}", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("collection-subfolder-icon-${sub.id}", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun collection_menu_shows_expand_and_collapse_actions() {
        val state = AppState(withDemoData = true)
        val rootId = state.collections.first().id
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("collection-actions-$rootId", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Expand", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Collapse", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Expand All", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Collapse All", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun collapse_and_expand_from_collection_menu_hides_and_restores_children() {
        val state = AppState(withDemoData = true)
        val rootId = state.collections.first().id
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("collection-node-r1", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("collection-actions-$rootId", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Collapse", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("collection-node-r1", useUnmergedTree = true).assertCountEquals(0)

        composeRule.onNodeWithTag("collection-actions-$rootId", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Expand", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("collection-node-r1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun environment_row_does_not_resize_when_actions_become_visible() {
        val state = AppState(withDemoData = true)
        val envName = state.environments.first().name
        composeRule.setContent { MainScreen(state) }

        val row = composeRule.onNodeWithTag("env-row-$envName", useUnmergedTree = true)
        val before = row.getUnclippedBoundsInRoot()

        // Clicking marks as active, which reveals the actions icon.
        row.performClick()
        composeRule.waitForIdle()

        val after = row.getUnclippedBoundsInRoot()
        assertTrue((before.right - before.left) == (after.right - after.left))
        assertTrue((before.bottom - before.top) == (after.bottom - after.top))
    }

    // ── Request tabs bar shows tab count ──

    @Test
    fun adding_tab_in_collection_increases_tab_count() {
        val state = AppState(withDemoData = true)
        val tabsBefore = state.openTabs.size

        composeRule.setContent { MainScreen(state) }

        // Click the "+" button for the first collection
        val firstCollectionId = state.collections.first().id
        composeRule.onNodeWithTag("collection-add-$firstCollectionId").performClick()
        composeRule.waitForIdle()

        // Tab count should increase
        assert(state.openTabs.size == tabsBefore + 1)
    }

    @Test
    fun duplicate_request_updates_ui_immediately() {
        val state = AppState(withDemoData = true).apply { settings.confirmBeforeDelete = false }
        val before = state.collections.first().children.size
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("collection-actions-r1", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Duplicate Request", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // State updated
        assertTrue(state.collections.first().children.size == before + 1)
        // UI shows the duplicated node — look for its tree row in the Compose node tree
        val duplicatedId = state.collections.first().children[1].id
        composeRule.onNodeWithTag("collection-node-$duplicatedId", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun delete_request_updates_ui_immediately() {
        val state = AppState(withDemoData = true).apply { settings.confirmBeforeDelete = false }
        val before = state.collections.first().children.size
        composeRule.setContent { MainScreen(state) }

        // r1 should be visible before delete
        composeRule.onNodeWithTag("collection-node-r1", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("collection-actions-r1", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Delete Request", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // State updated
        assertTrue(state.collections.first().children.size == before - 1)
        // UI: the deleted node must not exist in the tree
        composeRule.onNodeWithTag("collection-node-r1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun add_request_updates_ui_immediately() {
        val state = AppState(withDemoData = true).apply { settings.confirmBeforeDelete = false }
        val before = state.collections.first().children.size
        val firstCollectionId = state.collections.first().id
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("collection-add-$firstCollectionId", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // State updated
        assertTrue(state.collections.first().children.size == before + 1)
        // UI shows the new node
        val newId = state.collections.first().children.last().id
        composeRule.onNodeWithTag("collection-node-$newId", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun open_request_shows_selected_indicator() {
        val state = AppState(withDemoData = true)
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithText("Get all users").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("selected-request-indicator-r1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun close_dirty_request_shows_save_discard_cancel_dialog() {
        val state = AppState().apply {
            settings.autoSaveRequests = false
            addTab(name = "Temp")
        }
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("url-input").performTextInput("https://example.com")
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Close tab", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dirty-close-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("dirty-close-save").assertIsDisplayed()
        composeRule.onNodeWithTag("dirty-close-discard").assertIsDisplayed()
        composeRule.onNodeWithTag("dirty-close-cancel").assertIsDisplayed()
    }

    // ── Helpers ──

    private fun sampleResponse(
        serverMs: Long = -1,
        downloadMs: Long = -1,
    ) = ResponseDefinition(
        requestId = "req-1",
        statusCode = 200,
        statusText = "OK",
        headers = listOf(KeyValueEntry("Content-Type", "application/json")),
        cookies = emptyList(),
        bodyText = "{\"ok\":true}",
        contentType = "application/json",
        executedAtEpochMillis = System.currentTimeMillis(),
        metrics = ResponseMetrics(
            statusCode = 200,
            responseTimeMs = 120,
            responseSizeBytes = 12,
            serverMs = serverMs,
            downloadMs = downloadMs,
        ),
    )
}
