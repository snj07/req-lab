package com.reqlab.editor.core

import kotlin.test.*
import kotlin.time.TimeSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Performance microbenchmarks for the editor core data structures.
 *
 * These are NOT pass/fail unit tests — they always pass.  They print timing tables
 * to stderr/stdout so that the CI runner / developer can see measured numbers.
 *
 * Run with:
 *   ./gradlew :editor-core:desktopTest --tests "*.PerformanceBenchmarkTest" --no-daemon
 *
 * Budget thresholds below are guidelines, not hard assertions, since JVM JIT warm-up
 * can make cold numbers unrealistically high.  Each benchmark runs a warm-up pass
 * first and measures the warm pass.
 */
class PerformanceBenchmarkTest {

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun measure(label: String, warmupRuns: Int = 1, block: () -> Unit): Duration {
        // Warm-up
        repeat(warmupRuns) { block() }
        val t0 = TimeSource.Monotonic.markNow()
        block()
        val elapsed = t0.elapsedNow()
        println("[PERF] $label  ${elapsed.inWholeMilliseconds} ms")
        return elapsed
    }

    private fun buildJsonArray(entries: Int): String {
        val sb = StringBuilder("[")
        repeat(entries) { i ->
            sb.append("""{"id":$i,"name":"User$i","value":${i * 1.5},"active":true}""")
            if (i < entries - 1) sb.append(",\n")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun build10MbJson(): String = buildJsonArray(65_000)
    private fun build5MbJson(): String = buildJsonArray(32_000)
    private fun buildMinifiedJson(): String {
        // Single-line minified JSON array — like 5MB-min.json fixture
        val sb = StringBuilder("[")
        repeat(30_000) { i ->
            sb.append("""{"id":$i,"name":"User$i","value":${i * 1.5},"active":true}""")
            if (i < 29_999) sb.append(",")
        }
        sb.append("]")
        return sb.toString()
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  1. DocumentModel — loading large files
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun bench_DocumentModel_loadSmall_100KB() {
        val text = buildJsonArray(600)   // ~100 KB
        val elapsed = measure("DocumentModel.replaceAll  100 KB") {
            val doc = DocumentModel()
            doc.replaceAll(text)
        }
        // 100 KB should finish in < 500 ms even cold
        assertTrue(elapsed.inWholeMilliseconds < 2_000, "100KB load: ${elapsed.inWholeMilliseconds} ms")
    }

    @Test
    fun bench_DocumentModel_load1MB() {
        val text = buildJsonArray(6_000)  // ~1 MB
        val elapsed = measure("DocumentModel.replaceAll  1 MB") {
            val doc = DocumentModel()
            doc.replaceAll(text)
        }
        assertTrue(elapsed.inWholeMilliseconds < 5_000, "1MB load: ${elapsed.inWholeMilliseconds} ms")
    }

    @Test
    fun bench_DocumentModel_load5MB() {
        val text = build5MbJson()  // ~5 MB
        val elapsed = measure("DocumentModel.replaceAll  5 MB") {
            val doc = DocumentModel()
            doc.replaceAll(text)
        }
        assertTrue(elapsed.inWholeMilliseconds < 15_000, "5MB load: ${elapsed.inWholeMilliseconds} ms")
    }

    @Test
    fun bench_DocumentModel_load10MB() {
        val text = build10MbJson()  // ~10 MB
        val elapsed = measure("DocumentModel.replaceAll  10 MB / 5K lines") {
            val doc = DocumentModel()
            doc.replaceAll(text)
        }
        assertTrue(elapsed.inWholeMilliseconds < 30_000, "10MB load: ${elapsed.inWholeMilliseconds} ms")
    }

    @Test
    fun bench_DocumentModel_load_minifiedSingleLine() {
        val text = buildMinifiedJson()  // single line, ~3.5 MB
        val elapsed = measure("DocumentModel.replaceAll  minified single-line ~3.5 MB") {
            val doc = DocumentModel()
            doc.replaceAll(text)
        }
        assertTrue(elapsed.inWholeMilliseconds < 15_000, "minified single-line load: ${elapsed.inWholeMilliseconds} ms")
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  2. DocumentModel — sequential typing at end (the common case)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun bench_DocumentModel_typingAtEnd_100keystrokes_in_1MBdoc() {
        val base = buildJsonArray(6_000)  // ~1 MB seed
        val doc = DocumentModel(base)
        val elapsed = measure("100 sequential inserts at END of 1 MB doc") {
            repeat(100) { i ->
                doc.insert(doc.length, "x")
            }
        }
        assertTrue(elapsed.inWholeMilliseconds < 1_000, "100 keys @ end of 1MB: ${elapsed.inWholeMilliseconds} ms")
    }

    @Test
    fun bench_DocumentModel_typingAtEnd_100keystrokes_in_10MBdoc() {
        val base = build10MbJson()   // ~10 MB seed
        val doc = DocumentModel(base)
        val elapsed = measure("100 sequential inserts at END of 10 MB doc") {
            repeat(100) { i ->
                doc.insert(doc.length, "x")
            }
        }
        assertTrue(elapsed.inWholeMilliseconds < 1_000, "100 keys @ end of 10MB: ${elapsed.inWholeMilliseconds} ms")
    }

    @Test
    fun bench_DocumentModel_typingAtStart_100keystrokes_in_10MBdoc() {
        val base = build10MbJson()
        val doc = DocumentModel(base)
        val elapsed = measure("100 sequential inserts at START of 10 MB doc (gap move worst case)") {
            repeat(100) { i ->
                doc.insert(0, "x")
            }
        }
        // Each insert at pos 0 moves the entire gap from wherever it was — O(n)
        println("[PERF]   ^ expected to be SLOWEST keystroke path for 10MB file")
        assertTrue(elapsed.inWholeMilliseconds < 10_000, "100 keys @ start of 10MB: ${elapsed.inWholeMilliseconds} ms")
    }

    @Test
    fun bench_DocumentModel_jumpingEdits_10MBdoc() {
        val base = build10MbJson()
        val doc = DocumentModel(base)
        val mid = doc.length / 2
        // Alternate between start and end — worst case gap movement
        val elapsed = measure("10 alternating-position inserts in 10 MB doc (gap thrash)") {
            repeat(10) { i ->
                val pos = if (i % 2 == 0) 0 else doc.length
                doc.insert(pos, "x")
            }
        }
        println("[PERF]   ^ alternating gap moves across 10MB document")
        assertTrue(elapsed.inWholeMilliseconds < 5_000, "10 alternating inserts on 10MB: ${elapsed.inWholeMilliseconds} ms")
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  3. GapBuffer.toFullString() — O(n) snapshot allocation
    //     Called in: scheduleDiagnostics, computeAndApplyFolds
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun bench_toFullString_5MB() {
        val text = build5MbJson()
        val doc = DocumentModel(text)
        val elapsed = measure("document.toFullString()  5 MB") {
            doc.toFullString()
        }
        assertTrue(elapsed.inWholeMilliseconds < 2_000, "toFullString 5MB: ${elapsed.inWholeMilliseconds} ms")
    }

    @Test
    fun bench_toFullString_10MB() {
        val text = build10MbJson()
        val doc = DocumentModel(text)
        val elapsed = measure("document.toFullString()  10 MB") {
            doc.toFullString()
        }
        assertTrue(elapsed.inWholeMilliseconds < 5_000, "toFullString 10MB: ${elapsed.inWholeMilliseconds} ms")
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  4. String concatenation in applyReplace (lastExternalText update)
    //     lastExternalText = old.substring(0, f) + insertText + old.substring(t)
    //     This runs ON THE MAIN THREAD on every keystroke.
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun bench_stringConcatUpdate_editAtEnd_10MB() {
        val text = build10MbJson()
        val elapsed = measure("String concat (applyReplace) — edit at END of 10 MB string") {
            val f = text.length
            val t = text.length
            @Suppress("UNUSED_VARIABLE")
            val result = text.substring(0, f) + "x" + text.substring(t)
        }
        println("[PERF]   ^ This runs on MAIN THREAD per keystroke. Blocks UI at these times.")
        assertTrue(elapsed.inWholeMilliseconds < 3_000, "String concat edit at end 10MB: ${elapsed.inWholeMilliseconds} ms")
    }

    @Test
    fun bench_stringConcatUpdate_editAtStart_10MB() {
        val text = build10MbJson()
        val elapsed = measure("String concat (applyReplace) — edit at START of 10 MB string") {
            val f = 0
            val t = 0
            @Suppress("UNUSED_VARIABLE")
            val result = text.substring(0, f) + "x" + text.substring(t)
        }
        println("[PERF]   ^ Allocates three String objects totaling ~30 MB per keystroke.")
        assertTrue(elapsed.inWholeMilliseconds < 3_000, "String concat edit at start 10MB: ${elapsed.inWholeMilliseconds} ms")
    }

    @Test
    fun bench_stringConcatUpdate_editAtMiddle_10MB() {
        val text = build10MbJson()
        val mid = text.length / 2
        val elapsed = measure("String concat (applyReplace) — edit at MIDDLE of 10 MB string") {
            @Suppress("UNUSED_VARIABLE")
            val result = text.substring(0, mid) + "x" + text.substring(mid)
        }
        assertTrue(elapsed.inWholeMilliseconds < 3_000, "String concat edit at middle 10MB: ${elapsed.inWholeMilliseconds} ms")
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  5. DisplayLineMap.reset() + buildPrefixSums() per-keystroke cost
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun bench_DisplayLineMap_reset_5Klines() {
        val map = DisplayLineMap(5013)
        val elapsed = measure("DisplayLineMap.reset(5013 lines) — called per keystroke") {
            map.reset(5013)
        }
        assertTrue(elapsed.inWholeMilliseconds < 500, "DisplayLineMap.reset 5013 lines: ${elapsed.inWholeMilliseconds} ms")
    }

    @Test
    fun bench_DisplayLineMap_reset_then_applyFolds_5Klines() {
        val map = DisplayLineMap(5013)
        // Create some representative folds (e.g. 200 folds)
        val folds = (0 until 200).map { i -> Pair(i * 20, i * 20 + 10) }
        val elapsed = measure("DisplayLineMap.reset(5013) + applyFolds(200 regions)") {
            map.reset(5013)
            map.applyFolds(folds)
        }
        assertTrue(elapsed.inWholeMilliseconds < 1_000, "DisplayLineMap reset+applyFolds: ${elapsed.inWholeMilliseconds} ms")
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  6. JsonMode.foldingRegions() — called every onExternalTextChanged
    //     Scans the entire document text character by character
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun bench_JsonFoldingRegions_5MB() {
        val text = build5MbJson()
        val editorDoc = EditorDocument.create(text)
        val elapsed = measure("JsonMode.foldingRegions()  5 MB JSON") {
            JsonMode.foldingRegions(editorDoc)
        }
        assertTrue(elapsed.inWholeMilliseconds < 10_000, "foldingRegions 5MB: ${elapsed.inWholeMilliseconds} ms")
    }

    @Test
    fun bench_JsonFoldingRegions_10MB() {
        val text = build10MbJson()
        val editorDoc = EditorDocument.create(text)
        val elapsed = measure("JsonMode.foldingRegions()  10 MB JSON") {
            JsonMode.foldingRegions(editorDoc)
        }
        assertTrue(elapsed.inWholeMilliseconds < 20_000, "foldingRegions 10MB: ${elapsed.inWholeMilliseconds} ms")
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  7. JsonMode.validate() — lightweight path (>1MB), 500ms debounce
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun bench_JsonValidate_lightweightPath_4MB() {
        val text = buildMinifiedJson()  // ~3.5MB, uses lightweight validate
        val elapsed = measure("JsonMode.validate() lightweight  ~3.5 MB JSON") {
            JsonMode.validate(text)
        }
        assertTrue(elapsed.inWholeMilliseconds < 5_000, "validate lightweight 3.5MB: ${elapsed.inWholeMilliseconds} ms")
    }

    @Test
    fun bench_JsonValidate_lightweightPath_10MB() {
        val text = build10MbJson()
        val elapsed = measure("JsonMode.validate() lightweight  10 MB JSON") {
            JsonMode.validate(text)
        }
        assertTrue(elapsed.inWholeMilliseconds < 15_000, "validate lightweight 10MB: ${elapsed.inWholeMilliseconds} ms")
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  8. LineIndex.rebuild() — called on full document replace
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun bench_LineIndex_rebuild_5013lines() {
        val text = build10MbJson()
        val buf = GapBuffer(text.length + 256)
        buf.insert(0, text)
        val idx = LineIndex()
        val elapsed = measure("LineIndex.rebuild()  5013 lines / 10 MB") {
            idx.rebuild(buf)
        }
        assertTrue(elapsed.inWholeMilliseconds < 5_000, "LineIndex.rebuild 5013 lines: ${elapsed.inWholeMilliseconds} ms")
    }

    @Test
    fun bench_LineIndex_rebuild_singleLine_3_5MB() {
        val text = buildMinifiedJson()   // single line
        val buf = GapBuffer(text.length + 256)
        buf.insert(0, text)
        val idx = LineIndex()
        val elapsed = measure("LineIndex.rebuild()  1 line / 3.5 MB (minified)") {
            idx.rebuild(buf)
        }
        assertTrue(elapsed.inWholeMilliseconds < 3_000, "LineIndex.rebuild single line 3.5MB: ${elapsed.inWholeMilliseconds} ms")
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  9. computeAndApplyFolds() full simulation — called on onExternalTextChanged
    //     Includes: toFullString() → EditorDocument.create() → foldingRegions()
    //     → displayLineMap.reset()
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun bench_computeAndApplyFolds_simulation_5MB() {
        val text = build5MbJson()
        val doc = DocumentModel(text)
        val displayLineMap = DisplayLineMap(doc.lineCount)
        LanguageRegistry.registerBuiltins()

        val elapsed = measure("computeAndApplyFolds() simulation  5 MB JSON") {
            // This replicates EditorViewModel.computeAndApplyFolds()
            val editorDoc = EditorDocument.create(doc.toFullString())
            val regions = JsonMode.foldingRegions(editorDoc)
            displayLineMap.reset(doc.lineCount)
        }
        assertTrue(elapsed.inWholeMilliseconds < 15_000, "computeAndApplyFolds 5MB: ${elapsed.inWholeMilliseconds} ms")
    }

    @Test
    fun bench_computeAndApplyFolds_simulation_10MB() {
        val text = build10MbJson()
        val doc = DocumentModel(text)
        val displayLineMap = DisplayLineMap(doc.lineCount)
        LanguageRegistry.registerBuiltins()

        val elapsed = measure("computeAndApplyFolds() simulation  10 MB JSON") {
            val editorDoc = EditorDocument.create(doc.toFullString())
            val regions = JsonMode.foldingRegions(editorDoc)
            displayLineMap.reset(doc.lineCount)
        }
        assertTrue(elapsed.inWholeMilliseconds < 30_000, "computeAndApplyFolds 10MB: ${elapsed.inWholeMilliseconds} ms")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 10. StyleBuffer.grow() — called after every insert
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun bench_StyleBuffer_grow_5MB_to_10MB() {
        val buf = StyleBuffer(5 * 1024 * 1024)
        val elapsed = measure("StyleBuffer.grow(5MB → 10MB) — called by IdleLexer after external load") {
            buf.grow(10 * 1024 * 1024)
        }
        assertTrue(elapsed.inWholeMilliseconds < 2_000, "StyleBuffer.grow 5M→10M: ${elapsed.inWholeMilliseconds} ms")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 11. XmlMode.foldingRegions() — for 5mb.xml fixture
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun bench_XmlFoldingRegions_5MB() {
        // Build a representative XML document
        val sb = StringBuilder("<root>\n")
        repeat(10_000) { i ->
            sb.append("  <item id=\"$i\"><name>User$i</name><value>${i * 1.5}</value></item>\n")
        }
        sb.append("</root>")
        val text = sb.toString()
        val editorDoc = EditorDocument.create(text)
        val elapsed = measure("XmlMode.foldingRegions()  ~5 MB XML") {
            XmlMode.foldingRegions(editorDoc)
        }
        assertTrue(elapsed.inWholeMilliseconds < 10_000, "XmlMode.foldingRegions 5MB: ${elapsed.inWholeMilliseconds} ms")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 12. Single keystroke full path simulation — applyReplace equivalent
    //     Measures total synchronous cost on the MAIN THREAD per keypress
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun bench_perKeystroke_mainThreadCost_10MB_typingAtEnd() {
        val text = build10MbJson()
        val doc = DocumentModel(text)
        val displayLineMap = DisplayLineMap(doc.lineCount)
        var lastExternalText = text

        val elapsed = measure("Per-keystroke main-thread cost: typing at END of 10 MB JSON") {
            val f = lastExternalText.length
            val t = lastExternalText.length

            // 1. String concat for lastExternalText (main thread)
            lastExternalText = lastExternalText.substring(0, f) + "x" + lastExternalText.substring(t)

            // 2. GapBuffer insert (main thread — doc mutation)
            doc.insert(doc.length - 1, "x")

            // 3. styleBuffer.invalidateFrom (main thread — O(1))
            // omitted: just an integer write

            // 4. displayLineMap.reset (main thread)
            displayLineMap.reset(doc.lineCount)
        }

        println("[PERF]   ^ Frame budget: 16ms @60fps. Breakdown: string concat + reset.")
        println("[PERF]   ^ If > 16ms → UI thread is blocked; frame drop / jank.")
    }

    @Test
    fun bench_perKeystroke_mainThreadCost_10MB_typingAtStart() {
        val text = build10MbJson()
        val doc = DocumentModel(text)
        val displayLineMap = DisplayLineMap(doc.lineCount)
        var lastExternalText = text

        val elapsed = measure("Per-keystroke main-thread cost: typing at START of 10 MB JSON (WORST CASE)") {
            // 1. String concat for lastExternalText
            lastExternalText = "x" + lastExternalText.substring(0)

            // 2. GapBuffer insert at position 0 — moves entire gap across 10MB
            doc.insert(0, "x")

            // 3. displayLineMap.reset
            displayLineMap.reset(doc.lineCount)
        }

        println("[PERF]   ^ Gap move O(10MB) + String alloc O(10MB) = catastrophic for 10MB file.")
    }
}
