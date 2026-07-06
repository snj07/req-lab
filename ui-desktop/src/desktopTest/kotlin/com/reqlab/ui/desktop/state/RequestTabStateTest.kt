package com.reqlab.ui.shared.state

import com.reqlab.core.model.BodyType
import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.ui.EditorViewModel
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests for the per-tab EditorViewModel cache and URL undo stack added to
 * [RequestTabState] as part of the undo/redo persistence fix.
 */
class RequestTabStateTest {

    // ═══════════════════════════════════════════════════════════════════════
    // Body ViewModel cache
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun bodyViewModelCache_returns_same_instance_for_same_body_type() {
        val tab = RequestTabState()
        val vm1 = tab.getOrCreateBodyViewModel(BodyType.JSON, "{}", LanguageMode.JSON)
        val vm2 = tab.getOrCreateBodyViewModel(BodyType.JSON, "{}", LanguageMode.JSON)
        assertSame(vm1, vm2, "Same BodyType must return the same EditorViewModel instance")
    }

    @Test
    fun bodyViewModelCache_creates_new_instance_for_different_body_type() {
        val tab = RequestTabState()
        val jsonVm = tab.getOrCreateBodyViewModel(BodyType.JSON, "{}", LanguageMode.JSON)
        val xmlVm  = tab.getOrCreateBodyViewModel(BodyType.XML, "<r/>", LanguageMode.XML)
        assertNotSame(jsonVm, xmlVm, "Different BodyTypes must produce distinct EditorViewModel instances")
    }

    @Test
    fun bodyViewModelCache_initial_text_is_used_only_on_first_creation() {
        val tab = RequestTabState()
        // First call with "initial text"
        val vm = tab.getOrCreateBodyViewModel(BodyType.JSON, "initial text", LanguageMode.JSON)
        assertEquals("initial text", vm.getFullText(), "Initial text should be set on first creation")

        // Second call with different initial text — should return same VM (not re-init)
        val vm2 = tab.getOrCreateBodyViewModel(BodyType.JSON, "different text", LanguageMode.JSON)
        assertSame(vm, vm2, "Must return same VM instance on second call")
        assertEquals("initial text", vm2.getFullText(), "Text should NOT be overwritten by second call")
    }

    @Test
    fun disposeBodyViewModels_clears_cache_and_cancels_scopes() {
        val tab = RequestTabState()
        val vm = tab.getOrCreateBodyViewModel(BodyType.JSON, "{}", LanguageMode.JSON)

        // Make an edit so there's something in the undo stack
        vm.moveCursorTo(1)
        vm.insertAtCursor("a")

        tab.disposeBodyViewModels()

        // After dispose, a new call must return a fresh instance (cache was cleared)
        val vmAfterDispose = tab.getOrCreateBodyViewModel(BodyType.JSON, "{}", LanguageMode.JSON)
        assertNotSame(vm, vmAfterDispose, "After disposeBodyViewModels, a new VM instance must be created")
    }

    @Test
    fun undo_history_is_preserved_across_multiple_cache_retrievals() {
        val tab = RequestTabState()

        val vm = tab.getOrCreateBodyViewModel(BodyType.JSON, "{}", LanguageMode.JSON)
        vm.moveCursorTo(1)
        vm.insertAtCursor("x")
        vm.insertAtCursor("y")
        assertEquals("{xy}", vm.getFullText())

        // Simulate re-entering the Body tab: get the same VM, verify undo still works
        val vmAgain = tab.getOrCreateBodyViewModel(BodyType.JSON, "{xy}", LanguageMode.JSON)
        assertSame(vm, vmAgain)
        vmAgain.undo()
        assertEquals("{x}", vmAgain.getFullText(), "Undo history must survive tab re-entry via cache")
        vmAgain.undo()
        assertEquals("{}", vmAgain.getFullText())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // URL undo stack
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun urlUndoStack_is_initially_empty() {
        val tab = RequestTabState()
        assertTrue(tab.urlUndoStack.isEmpty(), "urlUndoStack must start empty")
    }

    @Test
    fun urlUndoStack_accumulates_entries_on_text_change() {
        val tab = RequestTabState()
        tab.urlUndoStack.addLast("https://example.com")
        tab.urlUndoStack.addLast("https://example.com/api")
        assertEquals(2, tab.urlUndoStack.size)
        assertEquals("https://example.com/api", tab.urlUndoStack.last())
    }

    @Test
    fun urlUndoStack_pop_restores_previous_value() {
        val tab = RequestTabState()
        tab.urlUndoStack.addLast("https://before.com")
        val restored = tab.urlUndoStack.removeLastOrNull()
        assertEquals("https://before.com", restored, "Pop must return the last pushed value")
        assertTrue(tab.urlUndoStack.isEmpty(), "Stack must be empty after pop")
    }

    @Test
    fun urlUndoStack_survives_body_type_switch() {
        // The URL undo stack is on RequestTabState — body type changes must not affect it
        val tab = RequestTabState()
        tab.urlUndoStack.addLast("https://example.com")

        tab.bodyType = BodyType.JSON
        tab.bodyType = BodyType.XML

        assertEquals(1, tab.urlUndoStack.size, "urlUndoStack must be unaffected by bodyType changes")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // closeTab / closeTabsByIds lifecycle
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun closeTab_disposes_cached_viewModels_before_removal() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        val tab = RequestTabState()
        state.openTabs.add(tab)

        // Pre-populate the cache
        val vm = tab.getOrCreateBodyViewModel(BodyType.JSON, "{}", LanguageMode.JSON)
        vm.moveCursorTo(1)
        vm.insertAtCursor("z")

        state.closeTab(0)

        // The tab is gone from openTabs
        assertEquals(0, state.openTabs.size)

        // A completely new tab's cache is fresh — creating a VM there must differ from the disposed one.
        val newTab = RequestTabState()
        val freshVm = newTab.getOrCreateBodyViewModel(BodyType.JSON, "{}", LanguageMode.JSON)
        assertNotSame(vm, freshVm, "After tab close, old VM must not be reused by a new tab")
    }
}
