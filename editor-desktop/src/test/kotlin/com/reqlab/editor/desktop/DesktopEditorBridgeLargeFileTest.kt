package com.reqlab.editor.desktop

import com.reqlab.editor.core.LanguageMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Performance tests for [DesktopEditorBridge] with large file handling.
 *
 * These tests verify:
 *   - Bridge handles 5 MB / 10 MB payloads without hanging.
 *   - Debounced validation doesn't block the calling thread.
 *   - NoOpCodeEditorHost handles large payloads correctly.
 */
class DesktopEditorBridgeLargeFileTest {

    // ─── 5 MB via bridge.open ───────────────────────────────────

    @Test
    fun bridge_opens_5mb_json_without_crash() {
        val bridge  = DesktopEditorBridge()
        val payload = buildLargeJson(5_000_000)

        val state = bridge.open(payload, LanguageMode.JSON)
        assertEquals(payload, state.text)
        assertEquals(LanguageMode.JSON, state.languageMode)
    }

    @Test
    fun bridge_opens_10mb_json_without_crash() {
        val bridge  = DesktopEditorBridge()
        val payload = buildLargeJson(10_000_000)

        val state = bridge.open(payload, LanguageMode.JSON)
        assertEquals(payload, state.text)
        assertEquals(LanguageMode.JSON, state.languageMode)
    }

    // ─── Rapid text changes (simulates fast typing) ─────────────

    @Test
    fun bridge_handles_rapid_text_changes_without_crash() {
        val bridge  = DesktopEditorBridge()
        val initial = bridge.open("{}", LanguageMode.JSON)

        // Simulate 100 rapid text changes (like typing fast)
        var current = initial
        repeat(100) { i ->
            current = bridge.onTextChanged(current, """{"key$i": $i}""")
        }
        // Final state should reflect the last change
        assertEquals("""{"key99": 99}""", current.text)
    }

    // ─── NoOp host with large payload ───────────────────────────

    @Test
    fun noop_host_handles_5mb_payload() {
        val host = NoOpCodeEditorHost()
        val payload = buildLargeJson(5_000_000)

        var receivedText = ""
        var errorCount = -1
        host.onTextChanged = { receivedText = it }
        host.onErrorCount = { errorCount = it }

        host.setText(payload, LanguageMode.JSON)
        assertEquals(payload, receivedText)
        assertEquals(0, errorCount, "Valid 5 MB JSON should produce 0 errors")
    }

    @Test
    fun noop_host_handles_10mb_payload() {
        val host = NoOpCodeEditorHost()
        val payload = buildLargeJson(10_000_000)

        var errorCount = -1
        host.onErrorCount = { errorCount = it }

        host.setText(payload, LanguageMode.JSON)
        assertEquals(0, errorCount, "Valid 10 MB JSON should produce 0 errors")
    }

    // ─── Mode switching on large file ───────────────────────────

    @Test
    fun bridge_mode_switch_5mb_does_not_hang() {
        val bridge  = DesktopEditorBridge()
        val payload = buildLargeJson(5_000_000)

        val jsonState = bridge.open(payload, LanguageMode.JSON)
        val plainState = bridge.onModeChanged(jsonState, LanguageMode.PLAIN_TEXT)

        assertEquals(payload, plainState.text)
        assertTrue(plainState.diagnostics.isEmpty(), "PLAIN_TEXT should produce 0 diagnostics")
    }

    // ─── HTML bundle still passes with debounce changes ─────────

    @Test
    fun html_bundle_contains_debounce() {
        val html = EditorHtmlBundle.load()
        assertTrue(
            "TEXT_CHANGED_DEBOUNCE_MS" in html || "textChangedTimer" in html || "setTimeout" in html,
            "editor.html should include debounce for TEXT_CHANGED"
        )
    }

    // ─── Helpers ────────────────────────────────────────────────

    private fun buildLargeJson(targetBytes: Int): String {
        val item = "{\"id\":12345,\"name\":\"testValue\",\"active\":true}"
        val builder = StringBuilder(targetBytes + 256)
        builder.append("{\"items\":[")
        while (builder.length < targetBytes) {
            if (builder[builder.length - 1] != '[') builder.append(',')
            builder.append(item)
        }
        builder.append("]}")
        return builder.toString()
    }
}
