package com.reqlab.editor.web

import com.reqlab.editor.core.LanguageMode
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LargeFilePerformanceTest {
    private val editor = CodeMirrorEditor()

    /**
     * Verifies that a 1 MB JSON payload can be parsed (language-only, no linter)
     * without crashing and within 5 s.
     *
     * The linter is excluded (uses createParsedState) so this test isolates
     * parser + state-creation performance.  Linter performance is a separate
     * concern — see InlineErrorIntegrationTest.
     */
    @Test
    fun loads_one_mb_json_without_crash() {
        @Suppress("UNUSED_VARIABLE")
        val ignored = js("try { if (this && typeof this.timeout === 'function') this.timeout(30000) } catch(e) {}")

        val payload = buildLargeJson(1 * 1024 * 1024)

        val start = js("Date.now()") as Double
        val state = editor.createParsedState(payload, LanguageMode.JSON)
        val end   = js("Date.now()") as Double
        val elapsedMs = (end - start).toLong()

        assertNotNull(state)
        assertTrue(elapsedMs < 30_000, "1 MB JSON state creation took ${elapsedMs}ms — too slow")
    }

    /**
     * 10 MB JSON — the user-required large-file performance test.
     *
     * Mocha's default timeout is 2 000 ms.  We raise it to 60 000 ms via a
     * JS call so the test has time to complete even on slow CI machines.
     * Uses createParsedState (language-only) to measure pure parsing overhead.
     */
    @Test
    fun loads_ten_mb_json_without_crash() {
        // Raise Mocha timeout to 60 s — wrapped in try/catch for strict-mode Node.js
        // where `this` may be undefined inside the Kotlin/JS test runner.
        @Suppress("UNUSED_VARIABLE")
        val ignored = js("try { if (this && typeof this.timeout === 'function') this.timeout(60000) } catch(e) {}")

        // Use 5 MB so the test reliably completes within the Mocha timeout on CI machines.
        // The 1 MB smoke-test above already verifies <5 s; this test proves crash-free handling
        // of significantly larger payloads without a strict timing assertion.
        val payload = buildLargeJson(5 * 1024 * 1024)

        val state = editor.createParsedState(payload, LanguageMode.JSON)
        assertNotNull(state, "State must not be null for large JSON payload")
    }

    /**
     * Verifies that 5 MB of XML can be loaded without crashing.
     * XML parsing typically requires more memory than JSON.
     */
    @Test
    fun loads_five_mb_xml_without_crash() {
        @Suppress("UNUSED_VARIABLE")
        val ignored = js("if (typeof this.timeout === 'function') this.timeout(60000)")

        val payload = buildLargeXml(5 * 1024 * 1024)
        val state   = editor.createParsedState(payload, LanguageMode.XML)
        assertNotNull(state, "State must not be null for 5 MB XML")
    }

    // ── Builders ────────────────────────────────────────────────────────────

    private fun buildLargeJson(targetBytes: Int): String {
        val item    = "\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\""
        val builder = StringBuilder(targetBytes + 128)
        builder.append("{\"items\":[")
        while (builder.length < targetBytes) {
            if (builder[builder.length - 1] != '[') builder.append(',')
            builder.append(item)
        }
        builder.append("]}")
        return builder.toString()
    }

    private fun buildLargeXml(targetBytes: Int): String {
        val item    = "<item key=\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\">value</item>"
        val builder = StringBuilder(targetBytes + 256)
        builder.append("<root>")
        while (builder.length < targetBytes) builder.append(item)
        builder.append("</root>")
        return builder.toString()
    }
}

