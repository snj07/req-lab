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
)

// ── EditorViewModelV2 ────────────────────────────────────────────

class EditorViewModelV2(
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
    private var editSequence: Long = 0L

    init {
        idleLexer.scheduleFrom(0, scope)
        scheduleInitialFolds()
    }

    fun onExternalTextChanged(text: String) {
        if (text == lastExternalText) return
        lastExternalText = text
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
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        version = newVersion,
                        styleClock = styleBuffer.styleClock,
                        cursorOffset = it.cursorOffset.coerceIn(0, docLen),
                        selectionStart = -1,
                        selectionEnd = -1,
                        diagnostics = emptyList(),
                        totalDisplayLines = displayLineMap.totalDisplayLines,
                    )
                }
            }
            idleLexer.scheduleFrom(0, scope)
            scheduleDiagnostics()
        }
    }

    fun insertAtCursor(text: String) {
        editSequence++
        val st = _state.value
        var cursorPos = st.cursorOffset
        val oldText = lastExternalText
        if (st.selectionStart >= 0 && st.selectionEnd > st.selectionStart) {
            val from = st.selectionStart
            val to   = st.selectionEnd
            document.delete(from, to)
            styleBuffer.invalidateFrom(from)
            cursorPos = from
            val sf = from.coerceIn(0, oldText.length)
            val st2 = to.coerceIn(sf, oldText.length)
            lastExternalText = oldText.substring(0, sf) + text + oldText.substring(st2)
        } else {
            val sp = cursorPos.coerceIn(0, oldText.length)
            lastExternalText = oldText.substring(0, sp) + text + oldText.substring(sp)
        }

        if (text.length <= 1_000) {
            document.insert(cursorPos, text)
            styleBuffer.invalidateFrom(cursorPos)
            val newCursor = cursorPos + text.length
            displayLineMap.reset(document.lineCount)
            _state.update {
                it.copy(
                    version      = document.version,
                    cursorOffset = newCursor,
                    selectionStart = -1,
                    selectionEnd   = -1,
                    totalDisplayLines = displayLineMap.totalDisplayLines,
                )
            }
            notifyTextChanged()
            idleLexer.scheduleFrom(cursorPos, scope)
            scheduleDiagnostics()
        } else {
            val newCursorEager = (cursorPos + text.length).coerceAtMost(
                oldText.length - (if (st.selectionStart >= 0) (st.selectionEnd - st.selectionStart).coerceAtLeast(0) else 0) + text.length
            )
            _state.update {
                it.copy(
                    cursorOffset   = newCursorEager,
                    selectionStart = -1,
                    selectionEnd   = -1,
                )
            }
            // lastExternalText was already updated eagerly above — notify now so
            // onTextChange fires before the background coroutine finishes.
            notifyTextChanged()
            scope.launch(Dispatchers.Default) {
                mutex.withLock {
                    document.insert(cursorPos, text)
                    document.rebuildLineIndex()
                    styleBuffer.invalidateFrom(cursorPos)
                    styleBuffer.grow(document.length)
                    displayLineMap.reset(document.lineCount)
                }
                val fullText = document.toFullString()
                val newCursor = (cursorPos + text.length).coerceAtMost(document.length)
                lastExternalText = fullText
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            version        = document.version,
                            cursorOffset   = newCursor,
                            selectionStart = -1,
                            selectionEnd   = -1,
                            totalDisplayLines = displayLineMap.totalDisplayLines,
                        )
                    }
                }
                idleLexer.scheduleFrom(cursorPos, scope)
                scheduleDiagnostics()
            }
        }
    }

    fun deleteBeforeCursor() {
        editSequence++
        val st = _state.value
        if (st.selectionStart >= 0 && st.selectionEnd > st.selectionStart) {
            deleteRange(st.selectionStart, st.selectionEnd)
            return
        }
        val cursor = st.cursorOffset
        if (cursor <= 0) return
        val old = lastExternalText
        lastExternalText = if (cursor - 1 < old.length) old.removeRange(cursor - 1, minOf(cursor, old.length)) else old
        document.delete(cursor - 1, cursor)
        styleBuffer.invalidateFrom(cursor - 1)
        val newCursor = cursor - 1
        displayLineMap.reset(document.lineCount)
        _state.update {
            it.copy(
                version      = document.version,
                cursorOffset = newCursor,
                selectionStart = -1, selectionEnd = -1,
                totalDisplayLines = displayLineMap.totalDisplayLines,
            )
        }
        notifyTextChanged()
        idleLexer.scheduleFrom(newCursor, scope)
        scheduleDiagnostics()
    }

    fun deleteForwardAtCursor() {
        editSequence++
        val st = _state.value
        if (st.selectionStart >= 0 && st.selectionEnd > st.selectionStart) {
            deleteRange(st.selectionStart, st.selectionEnd)
            return
        }
        val cursor = st.cursorOffset
        if (cursor >= document.length) return
        val old = lastExternalText
        lastExternalText = if (cursor < old.length) old.removeRange(cursor, minOf(cursor + 1, old.length)) else old
        document.delete(cursor, cursor + 1)
        styleBuffer.invalidateFrom(cursor)
        displayLineMap.reset(document.lineCount)
        _state.update {
            it.copy(
                version      = document.version,
                cursorOffset = cursor,
                selectionStart = -1, selectionEnd = -1,
                totalDisplayLines = displayLineMap.totalDisplayLines,
            )
        }
        notifyTextChanged()
        idleLexer.scheduleFrom(cursor, scope)
        scheduleDiagnostics()
    }

    private fun deleteRange(from: Int, to: Int) {
        val old = lastExternalText
        val f = from.coerceIn(0, old.length)
        val t = to.coerceIn(f, old.length)
        lastExternalText = if (f < t) old.removeRange(f, t) else old
        document.delete(from, to)
        styleBuffer.invalidateFrom(from)
        displayLineMap.reset(document.lineCount)
        _state.update {
            it.copy(
                version      = document.version,
                cursorOffset = from,
                selectionStart = -1, selectionEnd = -1,
                totalDisplayLines = displayLineMap.totalDisplayLines,
            )
        }
        notifyTextChanged()
        idleLexer.scheduleFrom(from, scope)
        scheduleDiagnostics()
    }

    fun moveCursorTo(offset: Int, extendSelection: Boolean = false) {
        val clamped = offset.coerceIn(0, document.length)
        _state.update { st ->
            if (extendSelection) {
                val anchor = if (st.selectionStart >= 0) st.selectionStart else st.cursorOffset
                val (selStart, selEnd) = if (anchor <= clamped) anchor to clamped else clamped to anchor
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

    fun selectAll() {
        _state.update { it.copy(selectionStart = 0, selectionEnd = document.length, cursorOffset = document.length) }
    }

    fun onVisibleRangeChanged(firstDisplayLine: Int, lastDisplayLine: Int) {
        val firstDocLine = displayLineMap.docFromDisplay(firstDisplayLine)
        val firstCharInViewport = document.lineStart(firstDocLine).coerceAtLeast(0)
        if (firstCharInViewport < styleBuffer.endStyled) return
        idleLexer.scheduleFrom(firstCharInViewport, scope)
    }

    var foldRegions: List<FoldRegion> = emptyList()
        private set

    private fun scheduleInitialFolds() {
        scope.launch(Dispatchers.Default) {
            computeAndApplyFolds()
            withContext(Dispatchers.Main) { emitFoldUpdate() }
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

    private fun emitFoldUpdate() {
        _state.update {
            it.copy(
                foldVersion = it.foldVersion + 1,
                totalDisplayLines = displayLineMap.totalDisplayLines,
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
        val full = document.toFullString()
        val s = st.selectionStart.coerceIn(0, full.length)
        val e = st.selectionEnd.coerceIn(s, full.length)
        return full.substring(s, e)
    }

    private fun notifyTextChanged() {
        _textChangedFlow.tryEmit(Unit)
    }

    private fun scheduleDiagnostics() {
        diagnosticsJob?.cancel()
        diagnosticsJob = scope.launch(Dispatchers.Default) {
            delay(500L)
            val text = document.toFullString()
            val errors = provider.validate(text)
            withContext(Dispatchers.Main) {
                _state.update { it.copy(diagnostics = errors) }
            }
        }
    }

    fun dispose() {
        idleLexer.cancel()
        scope.cancel()
    }
}
