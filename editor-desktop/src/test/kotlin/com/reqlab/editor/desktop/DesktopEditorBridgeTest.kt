package com.reqlab.editor.desktop

import com.reqlab.editor.core.InlineErrorSeverity
import com.reqlab.editor.core.LanguageMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// ─── DesktopEditorBridge tests ────────────────────────────────────────────────

class DesktopEditorBridgeTest {

    @Test
    fun bridge_revalidates_when_mode_changes_to_json() {
        val bridge = DesktopEditorBridge()
        val plain  = bridge.open("{ \"a\": 1, }", LanguageMode.PLAIN_TEXT)
        assertEquals(0, plain.diagnostics.size, "PLAIN_TEXT should produce no diagnostics")

        val json = bridge.onModeChanged(plain, LanguageMode.JSON)
        assertEquals(1, json.diagnostics.size,
            "Trailing comma in JSON must produce exactly 1 diagnostic")
    }

    @Test
    fun bridge_opens_with_correct_language_mode() {
        val bridge = DesktopEditorBridge()
        val state  = bridge.open("{\"key\": 1}", LanguageMode.JSON)
        assertEquals(LanguageMode.JSON, state.languageMode)
    }

    @Test
    fun bridge_clears_diagnostics_on_valid_json() {
        val bridge = DesktopEditorBridge()
        val state  = bridge.open("""{"a": 1, "b": [1,2,3]}""", LanguageMode.JSON)
        assertTrue(state.diagnostics.isEmpty(), "Valid JSON should produce no diagnostics")
    }

    @Test
    fun bridge_reflects_text_changes() {
        val bridge  = DesktopEditorBridge()
        val initial = bridge.open("{}", LanguageMode.JSON)
        val updated = bridge.onTextChanged(initial, """{"name": "sanjay"}""")
        assertEquals("""{"name": "sanjay"}""", updated.text)
        assertTrue(updated.diagnostics.isEmpty())
    }

    @Test
    fun bridge_validates_xml_on_mode_switch() {
        val bridge  = DesktopEditorBridge()
        val initial = bridge.open("<root><unclosed></root>", LanguageMode.PLAIN_TEXT)
        val xml     = bridge.onModeChanged(initial, LanguageMode.XML)
        assertTrue(xml.diagnostics.isNotEmpty(), "Mismatched XML should produce diagnostics")
        assertTrue(xml.diagnostics.any { it.severity == InlineErrorSeverity.ERROR })
    }

    @Test
    fun bridge_validates_javascript_on_mode_switch() {
        val bridge  = DesktopEditorBridge()
        val initial = bridge.open("function broken() { const x = 1;", LanguageMode.PLAIN_TEXT)
        val js      = bridge.onModeChanged(initial, LanguageMode.JAVASCRIPT)
        assertTrue(js.diagnostics.isNotEmpty(), "Unclosed JS brace should produce diagnostics")
    }

    @Test
    fun bridge_html_unclosed_tag_is_warning_not_error() {
        val bridge = DesktopEditorBridge()
        val state  = bridge.open("<div><span>text</div>", LanguageMode.HTML)
        assertTrue(state.diagnostics.isNotEmpty(), "Unclosed <span> should produce a diagnostic")
        assertTrue(
            state.diagnostics.any { it.severity == InlineErrorSeverity.WARNING },
            "HTML unclosed tag must be WARNING. Got: ${state.diagnostics.map { it.severity }}"
        )
    }

    @Test
    fun bridge_noop_host_text_callback_fires() {
        val host    = NoOpCodeEditorHost()
        val bridge  = DesktopEditorBridge(host)
        var received = ""
        host.onTextChanged = { received = it }
        bridge.open("hello", LanguageMode.PLAIN_TEXT)
        assertEquals("hello", received)
    }
}

// ─── EditorHtmlBundle tests ───────────────────────────────────────────────────

class EditorHtmlBundleTest {

    @Test
    fun html_bundle_is_available() {
        assertTrue(EditorHtmlBundle.isAvailable(), "editor.html must be present in resources")
    }

    @Test
    fun html_bundle_loads_without_crash() {
        val html = EditorHtmlBundle.load()
        assertTrue(html.isNotBlank(), "editor.html must not be empty")
    }

    @Test
    fun html_bundle_contains_codemirror_imports() {
        val html = EditorHtmlBundle.load()
        assertTrue("@codemirror/state"           in html, "must import @codemirror/state")
        assertTrue("@codemirror/view"            in html, "must import @codemirror/view")
        assertTrue("@codemirror/lang-json"       in html, "must import lang-json")
        assertTrue("@codemirror/lang-html"       in html, "must import lang-html")
        assertTrue("@codemirror/lang-javascript" in html, "must import lang-javascript")
    }

    @Test
    fun html_bundle_contains_all_required_features() {
        val html = EditorHtmlBundle.load()
        assertTrue("indentationMarkers" in html || "indentation-markers" in html,
            "editor.html must include indentation-markers support")
        assertTrue("foldGutter"   in html, "must include foldGutter")
        assertTrue("lineNumbers"  in html, "must include lineNumbers()")
        assertTrue("SET_TEXT"     in html, "must handle SET_TEXT messages")
        assertTrue("TEXT_CHANGED" in html, "must emit TEXT_CHANGED events")
        assertTrue("SET_READONLY" in html, "must handle SET_READONLY messages")
        assertTrue("window.editor" in html, "must expose window.editor API")
    }

    @Test
    fun html_bundle_contains_all_language_modes() {
        val html = EditorHtmlBundle.load()
        assertTrue("json()"        in html, "must use json() language pack")
        assertTrue("xml()"         in html, "must use xml() language pack")
        assertTrue("html()"        in html, "must use html() language pack")
        assertTrue("javascript()"  in html, "must use javascript() language pack")
    }
}

// ─── WebEditorMessage tests ───────────────────────────────────────────────────

class WebEditorMessageTest {

    @Test
    fun set_text_serialises_json_language() {
        val json = WebEditorMessage.SetText("""{"a":1}""", "json").toJson()
        assertTrue("SET_TEXT"              in json)
        assertTrue("\"language\":\"json\"" in json)
    }

    @Test
    fun set_text_escapes_quotes_in_content() {
        val json = WebEditorMessage.SetText("""{"key": "val\"ue"}""", "json").toJson()
        assertTrue("\\\"" in json, "Double quotes inside content must be escaped")
    }

    @Test
    fun set_text_escapes_newlines() {
        val json = WebEditorMessage.SetText("line1\nline2", "text").toJson()
        assertTrue("\\n" in json, "Newlines must be escaped")
    }

    @Test
    fun language_id_maps_all_five_modes() {
        assertEquals("json",       WebEditorMessage.languageId(LanguageMode.JSON))
        assertEquals("xml",        WebEditorMessage.languageId(LanguageMode.XML))
        assertEquals("html",       WebEditorMessage.languageId(LanguageMode.HTML))
        assertEquals("javascript", WebEditorMessage.languageId(LanguageMode.JAVASCRIPT))
        assertEquals("text",       WebEditorMessage.languageId(LanguageMode.PLAIN_TEXT))
    }

    @Test
    fun from_json_parses_ready_message() {
        assertEquals(WebEditorMessage.Ready, WebEditorMessage.fromJson("""{"type":"READY"}"""))
    }

    @Test
    fun from_json_parses_text_changed_message() {
        val msg = WebEditorMessage.fromJson("""{"type":"TEXT_CHANGED","text":"hello"}""")
        assertTrue(msg is WebEditorMessage.TextChanged)
        assertEquals("hello", (msg as WebEditorMessage.TextChanged).text)
    }

    @Test
    fun from_json_parses_error_count_message() {
        val msg = WebEditorMessage.fromJson("""{"type":"ERROR_COUNT","count":3}""")
        assertTrue(msg is WebEditorMessage.ErrorCount)
        assertEquals(3, (msg as WebEditorMessage.ErrorCount).count)
    }

    @Test
    fun from_json_returns_null_for_unknown_type() {
        assertEquals(null, WebEditorMessage.fromJson("""{"type":"UNKNOWN"}"""))
    }
}

// ─── NoOpCodeEditorHost tests ─────────────────────────────────────────────────

class NoOpCodeEditorHostTest {

    @Test
    fun noop_host_fires_text_changed_callback() {
        val host     = NoOpCodeEditorHost()
        var received = ""
        host.onTextChanged = { received = it }
        host.setText("hello world", LanguageMode.PLAIN_TEXT)
        assertEquals("hello world", received)
    }

    @Test
    fun noop_host_fires_error_count_for_invalid_json() {
        val host  = NoOpCodeEditorHost()
        var count = -1
        host.onErrorCount = { count = it }
        host.setText("""{"a": 1, }""", LanguageMode.JSON)
        assertEquals(1, count, "Trailing comma in JSON should produce exactly 1 diagnostic")
    }

    @Test
    fun noop_host_fires_zero_error_count_for_valid_json() {
        val host  = NoOpCodeEditorHost()
        var count = -1
        host.onErrorCount = { count = it }
        host.setText("""{"a": 1}""", LanguageMode.JSON)
        assertEquals(0, count, "Valid JSON should produce 0 diagnostics")
    }

    @Test
    fun noop_host_get_text_returns_current_content() {
        val host = NoOpCodeEditorHost()
        host.setText("the content", LanguageMode.PLAIN_TEXT)
        var result = ""
        host.getText { result = it }
        assertEquals("the content", result)
    }

    @Test
    fun noop_host_state_has_correct_language_mode() {
        val host = NoOpCodeEditorHost()
        host.setText("{}", LanguageMode.JSON)
        assertEquals(LanguageMode.JSON, host.state.languageMode)
    }

    @Test
    fun noop_host_fires_error_count_for_xml_mismatch() {
        val host  = NoOpCodeEditorHost()
        var count = -1
        host.onErrorCount = { count = it }
        host.setText("<root><wrong></right></root>", LanguageMode.XML)
        assertTrue(count > 0, "Mismatched XML should produce errors, got $count")
    }
}

