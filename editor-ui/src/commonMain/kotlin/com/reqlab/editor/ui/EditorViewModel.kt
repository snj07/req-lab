package com.reqlab.editor.ui

import com.reqlab.editor.core.DisplayLineMap
import com.reqlab.editor.core.DocumentModel
import com.reqlab.editor.core.FoldRegion
import com.reqlab.editor.core.InlineEditorError
import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.core.LanguageRegistry
import com.reqlab.editor.core.StyleBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// ── Display state ────────────────────────────────────────────────

data class EditorDisplayState(
    val version: Int       = 0,
    val styleClock: Long   = 0L,
    val foldVersion: Int   = 0,
    val cursorOffset: Int      = 0,
    val selectionStart: Int    = -1,
    val selectionEnd: Int      = -1,
    val diagnostics: List<InlineEditorError> = emptyList(),
    val totalDisplayLines: Int = 1,
    /** True when at least one document line exceeds [EditorViewModel.DISPLAY_LINE_LENGTH_LIMIT] chars. */
    val hasLineTruncation: Boolean = false,
)

private data class EditCommand(
    val start: Int,
    val beforeText: String,
    val afterText: String,
    val cursorBefore: Int,
    val cursorAfter: Int,
)

// ── EditorViewModel ────────────────────────────────────────────

class EditorViewModel(
    initialText: String,
    val languageMode: LanguageMode,
) {
    val document      = DocumentModel(initialText)
    val styleBuffer   = StyleBuffer(maxOf(initialText.length, 64))
    val displayLineMap = DisplayLineMap(document.lineCount)

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mutex = Mutex()

    private val provider = LanguageRegistry.getProvider(languageMode)

    private val idleLexer = IdleLexer(
        document    = document,
        styleBuffer = styleBuffer,
        provider    = provider,
        onStyled    = {
            _state.update { it.copy(styleClock = styleBuffer.styleClock) }
        },
    )

    private val _state = MutableStateFlow(
        EditorDisplayState(
            version            = document.version,
            totalDisplayLines  = displayLineMap.totalDisplayLines,
            hasLineTruncation  = computeHasLineTruncation(),
        )
    )
    val state: StateFlow<EditorDisplayState> = _state.asStateFlow()

    // textChangedFlow — emitted immediately on every local edit.
    // Debouncing (150 ms) is applied in the composable LaunchedEffect so that
    // Compose tests can advance the clock past the debounce with waitForIdle().
    private val _textChangedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    val textChangedFlow: SharedFlow<Unit> = _textChangedFlow.asSharedFlow()

    private var lastExternalText: String = initialText
    private var diagnosticsJob: Job? = null
    private var foldRegionsJob: Job? = null
    private var editSequence: Long = 0L
    private val undoStack = ArrayDeque<EditCommand>()
    private val redoStack = ArrayDeque<EditCommand>()
    private var undoBytes: Long = 0L

    init {
        idleLexer.scheduleFrom(0, scope)
        scheduleInitialFolds()
    }

    fun onExternalTextChanged(text: String) {
        if (text == lastExternalText) return
        lastExternalText = text
        clearHistory()
        // Notify immediately: lastExternalText is already correct, so onTextChange fires
        // before the background coroutine completes. The guard above prevents feedback loops
        // when onTextChange → bodyContent update → LaunchedEffect → onExternalTextChanged.
        notifyTextChanged()
        val capturedSeq = editSequence
        scope.launch(Dispatchers.Default) {
            mutex.withLock {
                if (editSequence != capturedSeq) return@withLock
                document.replaceAll(text)
                styleBuffer.invalidateFrom(0)
                styleBuffer.grow(document.length)
                displayLineMap.reset(document.lineCount)
                scheduleInitialFoldsInternal()
            }
            if (editSequence != capturedSeq) return@launch
            val newVersion = document.version
            val docLen = document.length
            val hasTruncation = computeHasLineTruncation()
            // StateFlow.update is @ThreadSafe — update directly on Default dispatcher
            _state.update {
                it.copy(
                    version = newVersion,
                    styleClock = styleBuffer.styleClock,
                    cursorOffset = it.cursorOffset.coerceIn(0, docLen),
                    selectionStart = -1,
                    selectionEnd = -1,
                    diagnostics = emptyList(),
                    totalDisplayLines = displayLineMap.totalDisplayLines,
                    hasLineTruncation = hasTruncation,
                )
            }
            idleLexer.scheduleFrom(0, scope)
            scheduleDiagnostics()
        }
    }

    fun insertAtCursor(text: String) {
        if (text.isEmpty()) return
        editSequence++
        val st = _state.value
        val oldLen = lastExternalText.length
        val hasSelection = st.selectionStart >= 0 && st.selectionEnd > st.selectionStart
        val from = if (hasSelection) st.selectionStart else st.cursorOffset
        val to   = if (hasSelection) st.selectionEnd else st.cursorOffset
        val f = from.coerceIn(0, oldLen)
        val t = to.coerceIn(f, oldLen)

        applyReplace(
            from = f,
            to = t,
            insertText = text,
            cursorBefore = st.cursorOffset.coerceIn(0, oldLen),
            cursorAfter = f + text.length,
            recordHistory = true,
            clearRedo = true,
        )
    }

    fun deleteBeforeCursor() {
        editSequence++
        val st = _state.value
        val oldLen = lastExternalText.length
        if (st.selectionStart >= 0 && st.selectionEnd > st.selectionStart) {
            val f = st.selectionStart.coerceIn(0, oldLen)
            val t = st.selectionEnd.coerceIn(f, oldLen)
            applyReplace(
                from = f,
                to = t,
                insertText = "",
                // Use the selection end as cursorBefore so undo restores the cursor
                // to the end of the deleted range (e.g. after select-all + delete + undo).
                cursorBefore = t,
                cursorAfter = f,
                recordHistory = true,
                clearRedo = true,
            )
            return
        }

        // Clamp cursor to current document length so optimistic cursor updates cannot
        // cause out-of-range backspace behavior.
        val cursor = st.cursorOffset.coerceIn(0, oldLen)
        if (cursor <= 0) return
        applyReplace(
            from = cursor - 1,
            to = cursor,
            insertText = "",
            cursorBefore = cursor,
            cursorAfter = cursor - 1,
            recordHistory = true,
            clearRedo = true,
        )
    }

    fun deleteForwardAtCursor() {
        editSequence++
        val st = _state.value
        val oldLen = lastExternalText.length
        if (st.selectionStart >= 0 && st.selectionEnd > st.selectionStart) {
            val f = st.selectionStart.coerceIn(0, oldLen)
            val t = st.selectionEnd.coerceIn(f, oldLen)
            applyReplace(
                from = f,
                to = t,
                insertText = "",
                cursorBefore = st.cursorOffset.coerceIn(0, oldLen),
                cursorAfter = f,
                recordHistory = true,
                clearRedo = true,
            )
            return
        }

        val cursor = st.cursorOffset.coerceIn(0, oldLen)
        if (cursor >= oldLen) return
        applyReplace(
            from = cursor,
            to = cursor + 1,
            insertText = "",
            cursorBefore = cursor,
            cursorAfter = cursor,
            recordHistory = true,
            clearRedo = true,
        )
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        editSequence++
        val cmd = undoStack.removeLast()
        undoBytes -= commandBytes(cmd)
        applyReplace(
            from = cmd.start,
            to = cmd.start + cmd.afterText.length,
            insertText = cmd.beforeText,
            cursorBefore = cmd.cursorAfter,
            cursorAfter = cmd.cursorBefore,
            recordHistory = false,
            clearRedo = false,
        )
        redoStack.addLast(cmd)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        editSequence++
        val cmd = redoStack.removeLast()
        applyReplace(
            from = cmd.start,
            to = cmd.start + cmd.beforeText.length,
            insertText = cmd.afterText,
            cursorBefore = cmd.cursorBefore,
            cursorAfter = cmd.cursorAfter,
            recordHistory = false,
            clearRedo = false,
        )
        pushUndo(cmd)
    }

    private fun applyReplace(
        from: Int,
        to: Int,
        insertText: String,
        cursorBefore: Int,
        cursorAfter: Int,
        recordHistory: Boolean,
        clearRedo: Boolean,
    ) {
        val old = lastExternalText
        val f = from.coerceIn(0, old.length)
        val t = to.coerceIn(f, old.length)
        val beforeText = old.substring(f, t)

        if (beforeText.isEmpty() && insertText.isEmpty()) {
            _state.update {
                it.copy(
                    cursorOffset = cursorAfter.coerceIn(0, old.length),
                    selectionStart = -1,
                    selectionEnd = -1,
                )
            }
            return
        }

        if (recordHistory) {
            pushUndo(
                EditCommand(
                    start = f,
                    beforeText = beforeText,
                    afterText = insertText,
                    cursorBefore = cursorBefore.coerceIn(0, old.length),
                    cursorAfter = cursorAfter.coerceIn(0, old.length - (t - f) + insertText.length),
                ),
            )
        }
        if (clearRedo) {
            redoStack.clear()
        }

        lastExternalText = old.substring(0, f) + insertText + old.substring(t)

        // ── Fold-state preservation (C-2 fix) ────────────────────────────────
        // Capture the current fold shape BEFORE touching the document so we can
        // restore it – adjusted for any line-count delta – after the reset.
        val savedFolds  = displayLineMap.getFoldedRegions()
        val editDocLine = old.substring(0, f).count { it == '\n' }
        val deletedNL   = beforeText.count { it == '\n' }
        val insertedNL  = insertText.count { it == '\n' }
        val lineDelta   = insertedNL - deletedNL
        // ─────────────────────────────────────────────────────────────────────

        document.delete(f, t)
        if (insertText.isNotEmpty()) {
            document.insert(f, insertText)
        }

        styleBuffer.invalidateFrom(f)
        // grow() is background-only (IdleLexer calls it before any write); calling it here
        // from the main thread races with IdleLexer's own grow() on the styles array reference.
        displayLineMap.reset(document.lineCount)

        // ── Restore folds after reset ─────────────────────────────────────────
        if (savedFolds.isNotEmpty()) {
            val newLineCount = document.lineCount
            if (lineDelta == 0) {
                displayLineMap.applyFolds(savedFolds)
            } else {
                val adjusted = savedFolds.mapNotNull { (start, end) ->
                    val newStart = if (start >= editDocLine) (start + lineDelta).coerceAtLeast(0) else start
                    val newEnd   = if (end   >= editDocLine) (end   + lineDelta).coerceAtLeast(0) else end
                    if (newStart >= 0 && newEnd > newStart && newEnd < newLineCount) Pair(newStart, newEnd)
                    else null
                }
                if (adjusted.isNotEmpty()) displayLineMap.applyFolds(adjusted)
            }
        }
        // ─────────────────────────────────────────────────────────────────────

        // ── Immediately adjust foldRegions line numbers for the edit ──────────
        // This keeps fold-arrow positions correct after any insert/delete that
        // changes the line count.  A debounced background recompute
        // (scheduleFoldRegionsUpdate) will re-detect structural changes later.
        val foldRegionsChanged = if (lineDelta != 0 && foldRegions.isNotEmpty()) {
            val isAtLineStart = f == 0 || (f > 0 && old[f - 1] == '\n')
            val newLineCount = document.lineCount
            val updated = foldRegions.mapNotNull { region ->
                val sl0 = region.startLine - 1   // 0-based line of the region header
                val el0 = region.endLine - 1     // 0-based line of the region footer
                // Shift a boundary if the insert/delete is on an earlier line, OR at
                // the very start of the same line (the content moves down by lineDelta).
                val shiftStart = editDocLine < sl0 || (editDocLine == sl0 && isAtLineStart)
                val shiftEnd   = editDocLine < el0 || (editDocLine == el0 && isAtLineStart)
                val newStart = if (shiftStart) region.startLine + lineDelta else region.startLine
                val newEnd   = if (shiftEnd)   region.endLine   + lineDelta else region.endLine
                if (newStart >= 1 && newEnd > newStart && newEnd <= newLineCount)
                    region.copy(startLine = newStart, endLine = newEnd)
                else null
            }
            foldRegions = updated
            true
        } else false
        // ─────────────────────────────────────────────────────────────────────

        _state.update {
            it.copy(
                version = document.version,
                cursorOffset = cursorAfter.coerceIn(0, lastExternalText.length),
                selectionStart = -1,
                selectionEnd = -1,
                totalDisplayLines = displayLineMap.totalDisplayLines,
                // Clear stale diagnostics immediately so undo/redo/edits don't
                // leave red underlines on text that is now syntactically valid.
                // They are re-populated after the 500 ms background validation pass.
                diagnostics = emptyList(),
                // Bump foldVersion only when arrow positions actually changed so the
                // renderer re-reads foldRegions.
                foldVersion = if (foldRegionsChanged) it.foldVersion + 1 else it.foldVersion,
            )
        }

        notifyTextChanged()
        idleLexer.scheduleFrom(f, scope)
        scheduleDiagnostics()
        scheduleFoldRegionsUpdate()
    }

    private fun pushUndo(cmd: EditCommand) {
        undoStack.addLast(cmd)
        undoBytes += commandBytes(cmd)
        while (undoStack.size > MAX_UNDO_COMMANDS || undoBytes > MAX_UNDO_BYTES) {
            val dropped = undoStack.removeFirst()
            undoBytes -= commandBytes(dropped)
        }
    }

    private fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
        undoBytes = 0L
    }

    private fun commandBytes(cmd: EditCommand): Long =
        (cmd.beforeText.length + cmd.afterText.length).toLong()

    fun moveCursorTo(offset: Int, extendSelection: Boolean = false) {
        val clamped = offset.coerceIn(0, document.length)
        _state.update { st ->
            if (extendSelection) {
                // The anchor is the FIXED end of the selection — the opposite end from
                // where the cursor currently sits. This ensures the anchor stays pinned
                // while the user drags left or right, and flips correctly when direction
                // reverses (e.g. drag left then back right past the start point).
                val anchor = when {
                    st.selectionStart < 0 ->
                        st.cursorOffset               // no selection yet: anchor at current cursor
                    st.cursorOffset >= st.selectionEnd ->
                        st.selectionStart             // cursor is at the END → anchor is the START
                    else ->
                        st.selectionEnd               // cursor is at the START → anchor is the END
                }
                val selStart = minOf(anchor, clamped)
                val selEnd   = maxOf(anchor, clamped)
                st.copy(cursorOffset = clamped, selectionStart = selStart, selectionEnd = selEnd)
            } else {
                st.copy(cursorOffset = clamped, selectionStart = -1, selectionEnd = -1)
            }
        }
    }

    fun moveCursorLeft(extendSelection: Boolean = false) {
        moveCursorTo((_state.value.cursorOffset - 1).coerceAtLeast(0), extendSelection)
    }

    fun moveCursorRight(extendSelection: Boolean = false) {
        moveCursorTo((_state.value.cursorOffset + 1).coerceAtMost(document.length), extendSelection)
    }

    fun moveCursorUp(extendSelection: Boolean = false) {
        val offset = _state.value.cursorOffset
        val line   = document.lineAt(offset)
        if (line <= 0) { moveCursorTo(0, extendSelection); return }
        val col    = offset - document.lineStart(line)
        val prevStart = document.lineStart(line - 1)
        val prevLen   = document.lineText(line - 1).length
        moveCursorTo(prevStart + minOf(col, prevLen), extendSelection)
    }

    fun moveCursorDown(extendSelection: Boolean = false) {
        val offset = _state.value.cursorOffset
        val line   = document.lineAt(offset)
        if (line >= document.lineCount - 1) { moveCursorTo(document.length, extendSelection); return }
        val col    = offset - document.lineStart(line)
        val nextStart = document.lineStart(line + 1)
        val nextLen   = document.lineText(line + 1).length
        moveCursorTo(nextStart + minOf(col, nextLen), extendSelection)
    }

    fun moveCursorToLineStart(extendSelection: Boolean = false) {
        val offset = _state.value.cursorOffset
        val line   = document.lineAt(offset)
        moveCursorTo(document.lineStart(line), extendSelection)
    }

    fun moveCursorToLineEnd(extendSelection: Boolean = false) {
        val offset = _state.value.cursorOffset
        val line   = document.lineAt(offset)
        moveCursorTo(document.lineStart(line) + document.lineText(line).length, extendSelection)
    }

    fun selectWordAt(offset: Int) {
        val text = lastExternalText
        val len = text.length
        if (len == 0) return
        val pos = offset.coerceIn(0, len - 1)
        var start = pos
        var end = pos
        while (start > 0 && isWordChar(text[start - 1])) start--
        while (end < len && isWordChar(text[end])) end++
        if (start == end) end = (end + 1).coerceAtMost(len) // single non-word char
        _state.update {
            it.copy(
                selectionStart = start,
                selectionEnd = end,
                cursorOffset = end,
            )
        }
    }

    fun deleteSelection() {
        val st = _state.value
        if (st.selectionStart < 0 || st.selectionEnd <= st.selectionStart) return
        editSequence++
        val oldLen = lastExternalText.length
        val f = st.selectionStart.coerceIn(0, oldLen)
        val t = st.selectionEnd.coerceIn(f, oldLen)
        applyReplace(
            from = f,
            to = t,
            insertText = "",
            cursorBefore = st.cursorOffset.coerceIn(0, oldLen),
            cursorAfter = f,
            recordHistory = true,
            clearRedo = true,
        )
    }

    fun moveCursorWordLeft(extendSelection: Boolean = false) {
        val text = lastExternalText
        var pos = _state.value.cursorOffset.coerceIn(0, text.length)
        if (pos > 0) pos--
        while (pos > 0 && !isWordChar(text[pos - 1])) pos--
        while (pos > 0 && isWordChar(text[pos - 1])) pos--
        moveCursorTo(pos, extendSelection)
    }

    fun moveCursorWordRight(extendSelection: Boolean = false) {
        val text = lastExternalText
        var pos = _state.value.cursorOffset.coerceIn(0, text.length)
        while (pos < text.length && !isWordChar(text[pos])) pos++
        while (pos < text.length && isWordChar(text[pos])) pos++
        moveCursorTo(pos, extendSelection)
    }

    fun moveCursorToDocStart(extendSelection: Boolean = false) =
        moveCursorTo(0, extendSelection)

    fun moveCursorToDocEnd(extendSelection: Boolean = false) =
        moveCursorTo(document.length, extendSelection)

    // ── M-1: Page movement ────────────────────────────────────────────────

    fun moveCursorPageDown(pageSize: Int = 20, extendSelection: Boolean = false) {
        val offset = _state.value.cursorOffset
        val line   = document.lineAt(offset)
        // Clamp: if paging down would go past the last line, go to end of document.
        if (line + pageSize >= document.lineCount) {
            moveCursorTo(document.length, extendSelection)
            return
        }
        val col         = offset - document.lineStart(line)
        val targetLine  = line + pageSize
        val targetStart = document.lineStart(targetLine)
        val targetLen   = document.lineText(targetLine).length
        moveCursorTo(targetStart + minOf(col, targetLen), extendSelection)
    }

    fun moveCursorPageUp(pageSize: Int = 20, extendSelection: Boolean = false) {
        val offset = _state.value.cursorOffset
        val line   = document.lineAt(offset)
        // Clamp: if paging up would go past line 0, go to start of document.
        if (line - pageSize < 0) {
            moveCursorTo(0, extendSelection)
            return
        }
        val col         = offset - document.lineStart(line)
        val targetLine  = line - pageSize
        val targetStart = document.lineStart(targetLine)
        val targetLen   = document.lineText(targetLine).length
        moveCursorTo(targetStart + minOf(col, targetLen), extendSelection)
    }

    // ── M-2: Dedent (Shift+Tab) ───────────────────────────────────────────

    fun dedentAtCursor() {
        editSequence++
        val st     = _state.value
        val oldLen = lastExternalText.length
        val cursor = st.cursorOffset.coerceIn(0, oldLen)
        val line   = document.lineAt(cursor)
        val lineStart = document.lineStart(line)
        val lineText  = document.lineText(line)
        val (removeCount, removeStart) = when {
            lineText.startsWith("  ") -> Pair(2, lineStart)
            lineText.startsWith("\t") -> Pair(1, lineStart)
            else -> return   // nothing to dedent
        }
        applyReplace(
            from = removeStart,
            to   = removeStart + removeCount,
            insertText = "",
            cursorBefore = cursor,
            cursorAfter  = (cursor - removeCount).coerceAtLeast(lineStart),
            recordHistory = true,
            clearRedo = true,
        )
    }

    // ── M-3: Delete word before cursor (Alt/Cmd+Backspace) ───────────────

    fun deleteWordBeforeCursor() {
        val st = _state.value
        if (st.selectionStart >= 0 && st.selectionEnd > st.selectionStart) {
            deleteSelection()
            return
        }
        editSequence++
        val text   = lastExternalText
        var cursor = st.cursorOffset.coerceIn(0, text.length)
        if (cursor <= 0) return
        val end = cursor
        cursor--
        // Skip trailing non-word chars, then skip the word
        while (cursor > 0 && !isWordChar(text[cursor - 1])) cursor--
        while (cursor > 0 && isWordChar(text[cursor - 1])) cursor--
        applyReplace(
            from = cursor,
            to   = end,
            insertText = "",
            cursorBefore = end,
            cursorAfter  = cursor,
            recordHistory = true,
            clearRedo = true,
        )
    }

    // ── M-4: Newline with auto-indent (Enter) ────────────────────────────

    fun insertNewlineWithAutoIndent() {
        val cursorOffset = _state.value.cursorOffset.coerceIn(0, lastExternalText.length)
        val line   = document.lineAt(cursorOffset)
        val lineText = document.lineText(line)
        val indent = lineText.takeWhile { it == ' ' || it == '\t' }
        insertAtCursor("\n$indent")
    }

    // ─────────────────────────────────────────────────────────────────────

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_'

    fun selectAll() {
        _state.update {
            it.copy(
                selectionStart = 0,
                selectionEnd = document.length,
                // Keep viewport stable: select all should not force-jump to doc end.
                cursorOffset = it.cursorOffset.coerceIn(0, document.length),
            )
        }
    }

    fun onVisibleRangeChanged(firstDisplayLine: Int, lastDisplayLine: Int) {
        val firstDocLine = displayLineMap.docFromDisplay(firstDisplayLine)
        val firstCharInViewport = document.lineStart(firstDocLine).coerceAtLeast(0)
        if (firstCharInViewport < styleBuffer.endStyled) return
        idleLexer.scheduleFrom(firstCharInViewport, scope)
    }

    @kotlin.concurrent.Volatile
    var foldRegions: List<FoldRegion> = emptyList()
        private set

    private fun scheduleInitialFolds() {
        scope.launch(Dispatchers.Default) {
            computeAndApplyFolds()
            val hasTruncation = computeHasLineTruncation()
            // StateFlow.update is @ThreadSafe — no Main dispatcher needed
            emitFoldUpdate(hasTruncation)
        }
    }

    private suspend fun scheduleInitialFoldsInternal() {
        computeAndApplyFolds()
    }

    private fun computeAndApplyFolds() {
        val editorDoc = com.reqlab.editor.core.EditorDocument.create(document.toFullString())
        val regions = provider.foldingRegions(editorDoc)
        foldRegions = regions
        displayLineMap.reset(document.lineCount)
    }

    private fun emitFoldUpdate(hasTruncation: Boolean = false) {
        _state.update {
            it.copy(
                foldVersion = it.foldVersion + 1,
                totalDisplayLines = displayLineMap.totalDisplayLines,
                hasLineTruncation = hasTruncation,
            )
        }
    }

    fun toggleFold(docLine: Int) {
        val region = foldRegions.firstOrNull { it.startLine - 1 == docLine } ?: return
        val startDoc = region.startLine - 1
        val endDoc   = region.endLine - 1
        if (displayLineMap.isVisible(startDoc + 1)) {
            displayLineMap.setFolded(startDoc, endDoc)
        } else {
            displayLineMap.setVisible(startDoc, endDoc)
        }
        _state.update { it.copy(totalDisplayLines = displayLineMap.totalDisplayLines, foldVersion = it.foldVersion + 1) }
    }

    fun foldAll() {
        displayLineMap.applyFolds(foldRegions.map { Pair(it.startLine - 1, it.endLine - 1) })
        _state.update { it.copy(totalDisplayLines = displayLineMap.totalDisplayLines, foldVersion = it.foldVersion + 1) }
    }

    fun unfoldAll() {
        displayLineMap.reset(document.lineCount)
        _state.update { it.copy(totalDisplayLines = displayLineMap.totalDisplayLines, foldVersion = it.foldVersion + 1) }
    }

    fun getFullText(): String = lastExternalText

    fun getSelectedText(): String {
        val st = _state.value
        if (st.selectionStart < 0 || st.selectionEnd <= st.selectionStart) return ""
        val s = st.selectionStart.coerceIn(0, document.length)
        val e = st.selectionEnd.coerceIn(s, document.length)
        return document.buffer.subSequence(s, e)
    }

    private fun notifyTextChanged() {
        _textChangedFlow.tryEmit(Unit)
    }

    private fun scheduleDiagnostics() {
        diagnosticsJob?.cancel()
        val seq = editSequence   // capture before the delay
        diagnosticsJob = scope.launch(Dispatchers.Default) {
            delay(500L)
            // Discard if a newer edit arrived during the delay
            if (editSequence != seq) return@launch
            val text = lastExternalText
            if (editSequence != seq) return@launch
            val errors = provider.validate(text)
            // Json.parseToJsonElement is not coroutine-cancellable; guard against the
            // stale-result race by checking the sequence AFTER the potentially-long parse.
            if (editSequence == seq) {
                _state.update { it.copy(diagnostics = errors) }
            }
        }
    }

    /** Re-detect fold regions in the background and update [foldRegions] without
     *  touching [displayLineMap] (collapsed state is preserved).  Debounced at 300 ms. */
    private fun scheduleFoldRegionsUpdate() {
        foldRegionsJob?.cancel()
        val seq = editSequence
        foldRegionsJob = scope.launch(Dispatchers.Default) {
            delay(300L)
            if (editSequence != seq) return@launch
            val text = lastExternalText
            if (editSequence != seq) return@launch
            val editorDoc = com.reqlab.editor.core.EditorDocument.create(text)
            val newRegions = provider.foldingRegions(editorDoc)
            if (editSequence == seq) {
                foldRegions = newRegions
                _state.update { it.copy(foldVersion = it.foldVersion + 1) }
            }
        }
    }

    /** O(lineCount) check: scans line start offsets to find any line > [DISPLAY_LINE_LENGTH_LIMIT] chars. */
    private fun computeHasLineTruncation(): Boolean {
        val count = document.lineCount
        for (line in 0 until count) {
            val start = document.lineStart(line)
            val end = if (line + 1 < count) document.lineStart(line + 1) - 1 else document.length
            if ((end - start) > DISPLAY_LINE_LENGTH_LIMIT) return true
        }
        return false
    }

    companion object {
        /** Matches LineView.MAX_RENDER_CHARS_PER_LINE — lines longer than this are truncated in the renderer. */
        const val DISPLAY_LINE_LENGTH_LIMIT = 50_000
        // Keep undo memory bounded for multi-MB documents while still allowing
        // long Cmd+Z/Cmd+Shift+Z chains.
        private const val MAX_UNDO_COMMANDS = 2_000
        private const val MAX_UNDO_BYTES = 32L * 1024L * 1024L
    }

    fun dispose() {
        idleLexer.cancel()
        scope.cancel()
    }
}
