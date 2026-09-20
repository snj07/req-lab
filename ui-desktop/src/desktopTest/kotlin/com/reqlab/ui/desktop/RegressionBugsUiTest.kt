package com.reqlab.ui.desktop

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.reqlab.ui.shared.components.KeyValueEditor
import com.reqlab.ui.shared.state.MutableKeyValue
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.ui.shared.MainScreen
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.CollectionNode
import com.reqlab.ui.shared.state.RequestEditorTab
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Compose UI integration tests for the three regressions documented in
 * regression-bugs-report.md:
 *
 *  Bug #2 – False dirty state immediately after opening a request from the collection sidebar
 *  Bug #3 – Params table is empty when opening a collection request whose URL has a query string
 *
 * These tests exercise the full Compose composition path (MainScreen → RequestTabsBar →
 * RequestEditor → KeyValueEditor) so that any regression in the fix is caught at the UI
 * render level, not just at the state-unit level.
 */
class RegressionBugsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun deleting_first_parameter_keeps_remaining_row_text_attached_to_its_uid() {
        val rows = mutableStateListOf(
            MutableKeyValue("first", "one"),
            MutableKeyValue("second", "two"),
        )
        val secondUid = rows[1].uid
        composeRule.setContent { KeyValueEditor(rows, "param") {} }
        composeRule.onNodeWithTag("param-row-1-value", useUnmergedTree = true).performClick()
        composeRule.runOnIdle { rows.removeAt(0) }
        composeRule.waitForIdle()
        assertEquals(secondUid, rows.single().uid)
        composeRule.onNodeWithTag("param-row-0", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("two", useUnmergedTree = true).assertIsDisplayed().assertIsFocused()
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Builds an [AppState] containing one collection with one POST/JSON request node,
     * then opens that request as a tab — matching the exact user flow from the sidebar.
     */
    private fun stateWithPostRequestOpenedFromSidebar(): AppState {
        val state = AppState(openDefaultTab = false)
        val node = CollectionNode(
            id = "post-dirty-test",
            name = "Create User",
            isFolder = false,
            method = HttpMethodType.POST,
            url = "https://api.com/users",
            bodyType = BodyType.JSON,
        )
        state.collections.add(
            CollectionNode(
                id = "coll-dirty-test",
                name = "Test Collection",
                isFolder = true,
                children = mutableStateListOf(node),
            )
        )
        // openRequest → addTab mirrors exactly what the sidebar "click request" does
        state.openRequest(
            requestId = node.id,
            name = node.name,
            method = node.method!!,
            url = node.url!!,
        )
        return state
    }

    private fun stateWithSearchRequestOpenedFromSidebar(): AppState {
        val state = AppState(openDefaultTab = false)
        val node = CollectionNode(
            id = "get-params-test",
            name = "Search Users",
            isFolder = false,
            method = HttpMethodType.GET,
            url = "https://api.com/search?q=test&page=1",
        )
        state.collections.add(
            CollectionNode(
                id = "coll-params-test",
                name = "Test Collection",
                isFolder = true,
                children = mutableStateListOf(node),
            )
        )
        state.openRequest(
            requestId = node.id,
            name = node.name,
            method = node.method!!,
            url = node.url!!,
        )
        return state
    }

    // ── Bug #2: False dirty state ──────────────────────────────────────────

    /**
     * Immediately after opening a POST/JSON collection request the tab name
     * must NOT carry the dirty marker " *".
     *
     * Root cause was: RequestTabState.init{} captured savedSnapshot before
     * addTab() ran syncSystemHeaders(). The re-anchoring fix (restoreSavedSnapshot(null)
     * at the end of addTab) must ensure the tab chip shows a clean title.
     */
    @Test
    fun `bug2 – tab chip shows clean title immediately after opening from collection sidebar`() {
        val state = stateWithPostRequestOpenedFromSidebar()
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // The tab bar renders: tab.name + if (tab.isDirty) " *" else ""
        val tab = state.activeTab!!

        // Primary assertion: state-level dirty flag must be false
        assertFalse(tab.isDirty, "Tab must not be dirty immediately after opening from sidebar")

        // UI assertion: dirty marker " *" must NOT appear in the tab bar
        composeRule.onNodeWithText("Create User *", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    /**
     * Opening a second tab from the sidebar (GET, no body) also starts clean.
     * Guards against the issue re-appearing for requests with no bodyType.
     */
    @Test
    fun `bug2 – tab chip for GET request opened from sidebar is clean`() {
        val state = stateWithSearchRequestOpenedFromSidebar()
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val tab = state.activeTab!!
        assertFalse(tab.isDirty)
        composeRule.onNodeWithText("Search Users *", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    /**
     * After the user edits the URL and then reverts it to the original value,
     * the dirty indicator must disappear. This validates that the re-anchored
     * savedSnapshot (with system headers) allows a clean revert.
     */
    @Test
    fun `bug2 – dirty indicator disappears after reverting URL to saved value`() {
        val state = stateWithPostRequestOpenedFromSidebar()
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val tab = state.activeTab!!
        val originalUrl = tab.url

        composeRule.runOnUiThread {
            tab.url = "https://api.com/users/modified"
            tab.markDirty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Create User *", useUnmergedTree = true)
            .assertIsDisplayed()

        // Revert
        composeRule.runOnUiThread {
            tab.url = originalUrl
            tab.markDirty()
        }
        composeRule.waitForIdle()

        assertFalse(tab.isDirty, "Dirty flag must clear when URL is reverted to saved value")
        composeRule.onNodeWithText("Create User *", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    // ── Bug #3: Params table empty after opening from sidebar ─────────────

    /**
     * Switching to the Params tab after opening a collection request that has a
     * query string in its URL must show the parsed parameter rows.
     *
     * Root cause was: addTab() never called syncParamsFromUrl(), so the Params
     * table was always empty. The fix calls syncParamsFromUrl(tab, url) in addTab().
     */
    @Test
    fun `bug3 – params tab shows rows for URL query string when opened from collection sidebar`() {
        val state = stateWithSearchRequestOpenedFromSidebar()

        // Switch to Params tab so KeyValueEditor renders param rows
        state.activeTab!!.selectedEditorTab = RequestEditorTab.PARAMS

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // After the fix, tab.params = [q=test, page=1] → rows "param-row-0", "param-row-1"
        composeRule.onNodeWithTag("param-row-0", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("param-row-1", useUnmergedTree = true).assertExists()
    }

    /**
     * The Params tab badge count must reflect the number of query params parsed
     * from the URL, not zero. The count renders as a separate Text node "(2)" next
     * to the "Params" label (see EditorTabBar in RequestEditor.kt).
     */
    @Test
    fun `bug3 – params tab badge shows correct count when URL has query params`() {
        val state = stateWithSearchRequestOpenedFromSidebar()
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // State-level: tab must have 2 params
        assertEquals(2, state.activeTab!!.params.size,
            "Expected 2 params after opening URL with query string")

        // UI-level: the badge text "(2)" is rendered as a separate Text node
        composeRule.onNodeWithText("(2)", useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * A URL with no query string must leave the Params table empty (sanity check
     * that the fix does not introduce spurious rows).
     */
    @Test
    fun `bug3 – params table is empty when URL has no query string`() {
        val state = stateWithPostRequestOpenedFromSidebar()  // URL = "https://api.com/users" (no ?)
        state.activeTab!!.selectedEditorTab = RequestEditorTab.PARAMS

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("param-row-0", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * After opening with URL params, opening the same request again (already open tab)
     * must not duplicate rows — addTab() returns early for existing tabs.
     */
    @Test
    fun `bug3 – reopening an already-open tab does not duplicate param rows`() {
        val state = stateWithSearchRequestOpenedFromSidebar()
        state.activeTab!!.selectedEditorTab = RequestEditorTab.PARAMS

        // Open the same request again — addTab early-returns, params must stay at 2
        state.openRequest(
            requestId = "get-params-test",
            name = "Search Users",
            method = HttpMethodType.GET,
            url = "https://api.com/search?q=test&page=1",
        )

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val tab = state.activeTab!!
        kotlin.test.assertEquals(2, tab.params.size, "Params must not be duplicated on re-open")
        composeRule.onNodeWithTag("param-row-0", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("param-row-1", useUnmergedTree = true).assertExists()
        // There must be no third row
        composeRule.onNodeWithTag("param-row-2", useUnmergedTree = true).assertDoesNotExist()
    }
}
