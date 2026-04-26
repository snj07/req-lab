# Code Editor Architecture

## Overview

The code editor is a **Compose Multiplatform–native** implementation targeting both `jvm("desktop")` and `wasmJs{browser()}`. It is split into two modules:

| Layer | Module | Purpose |
|---|---|---|
| Data contracts | `editor-core` | Pure Kotlin engine — no Compose, no runtime dependencies |
| UI renderer | `editor-ui` | Compose composables, ViewModel, background tokenizer |

The public composable entry point is `CodeEditor.kt` in `ui-shared`, which wraps `EditorRenderer` with a toolbar, search bar, format button, and fold controls.

> **Design inspiration:** The data structures throughout `editor-core` and `editor-ui` are
> modelled after [Scintilla](https://www.scintilla.org/ScintillaDoc.html):
> `GapBuffer` ← SplitVector/CellBuffer, `StyleBuffer` ← CellBuffer style array,
> `DisplayLineMap` ← ContractionState, `IdleLexer` ← IdleStyling/ActionDuration.

---

## Why Not WebView + CodeMirror / Monaco?

**No built-in WebView in Compose Desktop.** Embedding an HTML editor engine requires JCEF (Java Chromium Embedded Framework), which adds ~150 MB to the distribution, requires per-platform native binaries, and complicates the Gradle build.

**KMP wasmJs target is incompatible.** The `ui-web` target runs as a WebAssembly application inside a browser canvas. Nesting a second browser sandbox inside Wasm creates complex sandboxing constraints and no net benefit.

**Compose is the single UI toolkit.** Introducing HTML/CSS/JS would fragment the styling system and double the maintenance cost for theme and font changes.

---

## Module Structure

```
editor-core/src/commonMain/kotlin/com/reqlab/editor/core/
├── GapBuffer.kt            O(1) amortized insert/delete text storage
├── LineIndex.kt            Maps line numbers ↔ character offsets (prefix array)
├── DocumentModel.kt        Document API backed by GapBuffer + LineIndex
├── EditorDocument.kt       EditorDocument interface (legacy contract)
├── EditorEngine.kt         Functional (immutable-state) engine — used
│                           by editor-core tests and EditorEngine.validate()
├── EditorState.kt          EditorEngine's state snapshot (EditorDocument +
│                           CursorState + SelectionModel + FoldingModel)
├── CursorState.kt          Cursor position + anchor
├── SelectionModel.kt       Selection range(s) — normalised start/end
├── StyleBuffer.kt          Parallel byte array: one TokenType per char;
│                           endStyled high-water mark; styleClock counter
├── DisplayLineMap.kt       Prefix-sum fold/hide mapping (doc ↔ display lines)
├── FoldingModel.kt         Fold region set + collapsed-lines set
├── Token.kt                Token + TokenType enum + FoldRegion data class
├── LanguageMode.kt         LanguageMode enum + FoldingStyle enum +
│                           LanguageModeProvider interface + TextFormatter
├── LanguageRegistry.kt     Registry: LanguageMode → LanguageModeProvider;
│                           file-extension + MIME-type detection
├── JsonMode.kt             JSON tokenizer + fold region provider + formatter
├── XmlMode.kt              XML tokenizer + fold region provider
├── HtmlMode.kt             HTML tokenizer + fold region provider
├── JavaScriptMode.kt       JavaScript tokenizer
├── GraphQLMode.kt          GraphQL tokenizer
├── PlainTextMode.kt        No-op fallback provider
├── TextFormatter.kt        TextFormatter interface (ISP separation)
├── ContentTypeUtil.kt      Content-type string → LanguageMode helpers
└── InlineEditorError.kt    InlineEditorError + InlineErrorSeverity

editor-ui/src/commonMain/kotlin/com/reqlab/editor/ui/
├── EditorViewModel.kt      Mutable state coordinator — document, cursor,
│                           folds, search, undo/redo, diagnostics
├── EditorRenderer.kt       Compose LazyColumn renderer (gutter + content)
├── LineView.kt             Single visible line composable
├── IdleLexer.kt            Background incremental tokenizer (adaptive budget)
├── SyntaxHighlighter.kt    SyntaxHighlighter interface +
│                           DefaultSyntaxHighlighter implementation
├── SyntaxHighlighterRegistry.kt  Runtime registration of SyntaxHighlighters
├── EditorHighlighter.kt    Top-level façade: `highlightText()` / `highlightLine()`
│                           free functions delegating to SyntaxHighlighterRegistry
├── TokenColorRegistry.kt   TokenType → Compose color, per-language scheme
├── EditorColors.kt         Editor palette constants (dark defaults)
├── EditorTheme.kt          EditorTheme data class (Dark + Light presets)
├── SyntaxColors.kt         Named dark-theme syntax color constants
├── FoldingSupport.kt       FoldState, FoldRegion, VisibleLine data classes;
│                           detectBraceFoldRegions / detectXmlFoldRegions /
│                           detectCommentFoldRegions / detectFoldRegions /
│                           computeVisibleLines helpers
└── SearchHighlight.kt      Search-match highlight span builders
```

---

## `editor-core` — Data Contracts

### Text Storage: `GapBuffer` + `DocumentModel`

`GapBuffer` stores text in a single `CharArray` with a gap (empty region) at the current edit point. Inserting into the gap fills characters; deleting widens the gap. Moving the gap cost is O(distance), but is O(1) amortised for sequential edits (character-by-character typing). The buffer doubles capacity when the gap is exhausted.

```kotlin
// Relevant public API
class GapBuffer(initialCapacity: Int = 8192) {
    val length: Int
    fun charAt(index: Int): Char          // O(1)
    fun subSequence(from: Int, to: Int): String  // O(end-start)
    fun toFullString(): String            // O(n)
    fun insert(at: Int, text: String)     // O(1) amortized
    fun delete(from: Int, to: Int)        // O(|from - gapStart|) gap move
}
```

`LineIndex` keeps a compact `IntArray` of line-start character offsets. It is updated incrementally on each edit — single-character inserts/deletes update only the affected entries in O(1) lazy steps; large edits trigger a full O(n) rebuild. This gives O(1) line-start lookup and O(log n) char-offset → line-number lookup (binary search).

`DocumentModel` wraps both:

```kotlin
class DocumentModel(initialText: String = "") {
    val buffer: GapBuffer
    val lineIndex: LineIndex
    var version: Int          // increments atomically after every edit

    val length: Int
    val lineCount: Int

    fun lineStart(line: Int): Int      // O(1)
    fun lineText(line: Int): String    // O(line length)
    fun lineAt(offset: Int): Int       // O(log n)
    fun insert(at: Int, text: String)  // increments version
    fun delete(from: Int, to: Int)     // increments version
    fun replaceAll(newText: String)    // O(n)
    fun rebuildLineIndex()             // O(n)
    fun toFullString(): String         // O(n)
    fun linesText(fromLine: Int, toLine: Int): String
}
```

**Concurrency contract:** All mutations must come from a single coroutine at a time. `EditorViewModel` uses a `Mutex` to guarantee this. Reads of `lineText`/`lineCount`/`length` are safe from the UI thread after the `version` StateFlow has been observed (the StateFlow provides the JVM happens-before barrier).

---

### `StyleBuffer` — Per-Character Token Cache

`StyleBuffer` maintains a parallel `ByteArray` — one `TokenType` ordinal per character in the document. This enables O(1) colour lookup at render time without re-tokenising.

```
endStyled    high-water mark: positions in [0, endStyled) have valid styles
styleClock   monotonic counter; bumped after every IdleLexer chunk
```

On any document edit at position P, `invalidateFrom(P)` resets `endStyled = min(endStyled, P)` — O(1). The `IdleLexer` re-tokenises from `endStyled` forward.

**Thread safety:** No explicit locks. `applyStyle` is called only from `Dispatchers.Default`; `invalidateFrom` is called only from the UI thread. The StateFlow update from the background thread acts as the JVM memory barrier — the UI's next StateFlow read sees all preceding style writes. Benign race conditions (one stale colour frame) are self-correcting and imperceptible.

---

### `DisplayLineMap` — Prefix-Sum Fold Mapping

Inspired by Scintilla's `ContractionState`. Maintains:
- `displayHeight[i]` — 1 if doc line `i` is visible, 0 if folded/hidden
- `prefixSums[i]` — number of display lines before doc line `i`

This gives:
- `displayFromDoc(docLine)` — O(1) prefix lookup → display line index or -1 if hidden
- `docFromDisplay(displayLine)` — O(log n) binary search in `prefixSums`
- `setFolded(from, to)` / `setVisible(from, to)` — O(n) suffix rebuild (rare; fold operations are infrequent)

`LazyColumn` item indices are display-line indices, **not** document-line indices. The map is reset on large structural edits (`reset(newDocLineCount)`), and fold regions are reapplied from the fold-detection background job.

---

### `FoldingModel` — Fold Region State

Pure immutable data class holding the full set of detected fold regions and the set of currently collapsed start-lines:

```kotlin
data class FoldingModel(
    val regions: List<FoldRegion> = emptyList(),
    val collapsedLines: Set<Int> = emptySet(),
) {
    val hasFolds: Boolean
    fun toggleFold(startLine: Int): FoldingModel
    fun fold(startLine: Int): FoldingModel
    fun unfold(startLine: Int): FoldingModel
    fun foldAll(): FoldingModel
    fun unfoldAll(): FoldingModel
    fun isLineHidden(line: Int): Boolean
    fun isFoldStart(line: Int): Boolean
    fun isCollapsed(line: Int): Boolean
    fun getRegion(startLine: Int): FoldRegion?
    fun visibleLines(totalLines: Int): List<Int>
    fun updateRegions(newRegions: List<FoldRegion>): FoldingModel
}

// Defined in editor-core/Token.kt — used by FoldingModel and LanguageModeProvider
data class FoldRegion(
    val startLine: Int,
    val endLine: Int,
    val placeholder: String = "...",
)
```

`EditorViewModel` holds the current `FoldingModel` and applies it to `DisplayLineMap` on every fold change.

---

### Language System

#### `LanguageMode` enum

```kotlin
enum class LanguageMode {
    PLAIN_TEXT, JSON, XML, HTML, JAVASCRIPT, GRAPHQL
}
```

Content-type string → `LanguageMode` via `fromContentType(contentType: String?)`. Built-in mappings: `json/*` → JSON, `*graphql*` → GRAPHQL, `*xml*` → XML, `text/html` → HTML, `*javascript*` → JAVASCRIPT, everything else → PLAIN_TEXT.

#### `FoldingStyle` enum

```kotlin
enum class FoldingStyle {
    BRACE,   // fold by matching { / } and [ / ] — JSON, JS, GraphQL
    XML,     // fold by matching open/close XML/HTML tags
    PLAIN,   // no structural folding
}
```

Language providers declare which strategy applies; the `editor-ui` layer dispatches without hardcoding language names.

#### `LanguageModeProvider` interface

```kotlin
interface LanguageModeProvider : TextFormatter {
    val mode: LanguageMode
    val displayName: String
    val fileExtensions: List<String>
    val mimeTypes: List<String>
    val foldingStyle: FoldingStyle get() = FoldingStyle.PLAIN

    fun tokenizeLine(line: String, lineNumber: Int, state: Any? = null): Pair<List<Token>, Any?>
    fun foldingRegions(document: EditorDocument): List<FoldRegion>
    fun validate(text: String): List<InlineEditorError>

    // Bulk range tokenization into StyleBuffer — called from IdleLexer.
    // Default impl iterates tokenizeLine; stateful lexers may override.
    fun tokenizeRangeIntoBuffer(
        document: DocumentModel, fromChar: Int, toChar: Int, buffer: StyleBuffer
    )

    override fun format(text: String): String = text  // no-op default
}
```

#### `TextFormatter` interface (ISP separation)

`LanguageModeProvider` extends `TextFormatter`. A `TextFormatter` can be registered independently of the full tokenizer — useful if only formatting is needed without implementing lexing.

#### `LanguageRegistry`

```kotlin
object LanguageRegistry {
    fun register(provider: LanguageModeProvider)
    fun getProvider(mode: LanguageMode): LanguageModeProvider
    fun detectFromExtension(extension: String): LanguageMode
    fun detectFromMimeType(mimeType: String): LanguageMode
    fun registerBuiltins()   // registers all 6 built-in providers
}
```

---

### Token Types

```kotlin
enum class TokenType {
    KEYWORD, STRING, NUMBER, COMMENT, OPERATOR,
    PUNCTUATION, TAG, ATTRIBUTE, PROPERTY, VALUE,
    PLAIN, ERROR,
}

data class Token(
    val startOffset: Int,  // relative to the line start
    val endOffset: Int,
    val type: TokenType,
)
```

---

### `EditorState` (used by `EditorEngine` functional API)

```kotlin
data class EditorState(
    val document: EditorDocument,
    val languageMode: LanguageMode,
    val cursor: CursorState,
    val selection: SelectionModel,
    val folding: FoldingModel,
    val diagnostics: List<InlineEditorError>,
    val lineNumbersEnabled: Boolean,
    val foldingEnabled: Boolean,
)
```

`EditorEngine` is a stateless object with pure functions that transform `EditorState` → new `EditorState` (functional style). It is used in `editor-core` unit tests and by `validate()`. **`EditorViewModel` does NOT use `EditorState`** — it operates directly on `DocumentModel` and emits `EditorDisplayState`.

---

### `InlineEditorError`

```kotlin
data class InlineEditorError(
    val line: Int,     // 1-based document line
    val col: Int,      // 1-based column
    val message: String,
    val severity: InlineErrorSeverity,  // ERROR | WARNING
)
```

Pure data — no Compose dependency. Produced by `LanguageModeProvider.validate()`. `BodyEditor` calls `validate()` on every body content change and passes the result into `CodeEditor` → `EditorRenderer`.

---

## `editor-ui` — Compose Renderer

### `EditorDisplayState` — The Render State

`EditorViewModel` emits this as its `StateFlow`:

```kotlin
data class EditorDisplayState(
    val version: Int = 0,           // increments on every document edit
    val styleClock: Long = 0L,      // increments after each IdleLexer chunk
    val foldVersion: Int = 0,       // increments after fold changes
    val cursorOffset: Int = 0,      // absolute char offset
    val selectionStart: Int = -1,   // -1 = no selection
    val selectionEnd: Int = -1,
    val diagnostics: List<InlineEditorError> = emptyList(),
    val totalDisplayLines: Int = 1,
    val hasLineTruncation: Boolean = false,  // true if any line > 50,000 chars
)
```

`EditorRenderer` uses three separate `remember` keys — `version`, `styleClock`, `foldVersion` — to limit recomposition scope: a style update doesn't recompose fold indicators, and a fold change doesn't recompute token spans.

---

### `EditorViewModel`

`EditorViewModel` is the single mutable owner for an editable session. It holds:

- `document: DocumentModel` — mutable text storage
- `styleBuffer: StyleBuffer` — per-character token cache
- `displayLineMap: DisplayLineMap` — fold-aware visible-line map
- `state: StateFlow<EditorDisplayState>` — read by `EditorRenderer` via `collectAsState()`
- `textChangedFlow: SharedFlow<Unit>` — emitted on every text change; the composable debounces it by 150 ms before calling `onTextChange`
- `undoStack / redoStack` — bounded by `MAX_UNDO_COMMANDS = 2,000` entries and `MAX_UNDO_BYTES = 32 MB`
- A `Mutex` — ensures single-coroutine mutation access to `document`
- A `CoroutineScope(Dispatchers.Main + SupervisorJob())` — owns all background jobs
- An `IdleLexer` reference — started in `init`, cancelled in `dispose()`

#### Key constants

| Constant | Value | Meaning |
|---|---|---|
| `DISPLAY_LINE_LENGTH_LIMIT` | 50,000 chars | Lines longer than this set `hasLineTruncation = true` |
| `MAX_UNDO_COMMANDS` | 2,000 | Maximum history entries |
| `MAX_UNDO_BYTES` | 32 MB | Maximum total undo memory |
| `LARGE_EDIT_SCROLL_SUPPRESS_THRESHOLD_CHARS` (renderer) | 100,000 chars | Pastes larger than this suppress scroll-to-cursor |

#### Input/edit API

| Function | Description |
|---|---|
| `onExternalTextChanged(text)` | Replace document from external source (e.g. body loaded from collection). Guards against feedback loops. Runs replace on `Dispatchers.Default`. |
| `insertAtCursor(text)` | Insert text at cursor; replaces selection if active |
| `deleteBeforeCursor()` | Backspace — delete char before cursor or selection |
| `deleteForwardAtCursor()` | Delete key — delete char after cursor |
| `deleteWordBeforeCursor()` | Ctrl/Alt+Backspace — delete the whole word before cursor |
| `insertNewlineWithAutoIndent()` | Enter — inserts `\n` plus leading whitespace matching the current line |
| `dedentAtCursor()` | Shift+Tab — removes up to 4 spaces of leading indent on current line |
| `undo()` / `redo()` | Pop from undo/redo stacks; capped by command count and byte budget |

#### Cursor & selection API

| Function | Description |
|---|---|
| `moveCursorTo(offset, extendSelection)` | Jump to absolute offset |
| `moveCursorLeft/Right/Up/Down(extendSelection)` | Arrow key navigation |
| `moveCursorWordLeft/Right(extendSelection)` | Ctrl/Alt+←/→ word jump |
| `moveCursorToLineStart/End(extendSelection)` | Home/End |
| `moveCursorToDocStart/End(extendSelection)` | Cmd/Ctrl+Home/End |
| `moveCursorPageUp/Down(pageSize, extendSelection)` | PgUp/PgDn |
| `selectAll()` | Selects entire document |
| `selectWordAt(offset)` | Double-click word selection |
| `deleteSelection()` | Delete selected range |
| `getSelectedText(): String` | Returns selected substring |

#### Fold API

| Function | Description |
|---|---|
| `toggleFold(docLine)` | Expand or collapse fold at doc line (0-based) |
| `foldAll()` | Collapse all detected regions |
| `unfoldAll()` | Expand all regions |

Fold regions are detected asynchronously by `computeAndApplyFolds()`, which runs on `Dispatchers.Default` after every structural edit. The detected `List<FoldRegion>` is applied to `DisplayLineMap` and `state.foldVersion` is incremented.

#### Diagnostic API

`scheduleDiagnostics()` is a **private** internal function launched after every edit. It calls `LanguageModeProvider.validate()` on the current text and emits results into `state.diagnostics`. The job is cancelled and re-launched on each subsequent edit. Callers control diagnostics by passing `inlineErrors` directly to `CodeEditor`.

#### Read API

| Function | Description |
|---|---|
| `getFullText(): String` | Returns the last externally-synced text (`lastExternalText`). Identical to the current document content unless an edit is in-flight. |
| `getSelectedText(): String` | Returns the currently selected substring, or `""` if no selection is active. |

#### Viewport tracking

`onVisibleRangeChanged(firstDisplayLine, lastDisplayLine)` — called by `EditorRenderer`'s `LaunchedEffect` on scroll. `EditorViewModel` notifies `IdleLexer` to prioritise styling of visible lines before off-screen lines.

#### Lifecycle

`dispose()` cancels the `CoroutineScope` (and all child jobs including `IdleLexer`). Must be called when the composable leaves the composition — wired via `DisposableEffect` in `CodeEditor.kt`.

---

### `IdleLexer` — Adaptive Background Tokenizer

Implements Scintilla's IdleStyling / ActionDuration model.

**Strategy:**
1. On each edit, `scheduleFrom(fromChar, scope)` cancels any running job and launches a new one on `Dispatchers.Default`.
2. The coroutine processes the document in chunks. After each chunk it calls `onStyled()` (updates `state.styleClock` via `StateFlow.update`) and calls `yield()` so the Compose frame scheduler can interleave.
3. Chunk size is adaptive: an EMA of actual throughput (bytes styled per millisecond) targets `BUDGET_MS = 40 ms` of work per chunk, clamped to `[MIN_BUDGET_BYTES=2,000, MAX_BUDGET_BYTES=500,000]`.

```kotlin
class IdleLexer(
    document: DocumentModel,
    styleBuffer: StyleBuffer,
    provider: LanguageModeProvider,
    onStyled: suspend () -> Unit,  // → StateFlow update after each chunk
) {
    fun scheduleFrom(fromChar: Int, scope: CoroutineScope)
    fun cancel()
    suspend fun cancelAndWait()
}
```

This design ensures syntax highlighting never blocks the UI thread even on 1 MB+ documents — the EMA-based budget self-calibrates to available CPU headroom.

---

### `EditorRenderer` — LazyColumn Renderer

`EditorRenderer` is the main Compose composable. Signature:

```kotlin
@Composable
fun EditorRenderer(
    viewModel: EditorViewModel,
    isReadOnly: Boolean,
    language: LanguageMode,
    theme: EditorTheme = EditorTheme.Dark,
    wordWrap: Boolean = true,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "",
    onTextChange: ((String) -> Unit)? = null,
    onPasteRequest: (() -> String?)? = null,
    onCopyRequest: ((String) -> Unit)? = null,
    searchMatchRangesByLine: Map<Int, List<IntRange>> = emptyMap(),
    activeSearchMatch: Pair<Int, IntRange>? = null,
    onHorizontalScroll: ((Int) -> Unit)? = null,     // for tests
    onScrollStateReady: ((ScrollState) -> Unit)? = null, // for tests
)
```

Each `LazyColumn` item is one **display** line:

```
Box (clip, background, border, focus, key events, pointer input)
└── LazyColumn (listState)
    └── item[displayLine] ─► Row {
            GutterCell(lineNumber, foldIndicator, gutterWidth)
            VerticalDivider()
            LineView(tokens, cursor, selection, searchHighlights, inlineErrors)
        }
```

#### Gutter width stability

Pre-allocated for `maxOf(lineCount.toString().length, 2)` digits — prevents layout shift at the 9→10 line boundary:

```kotlin
val gutterDigits = maxOf(viewModel.document.lineCount.toString().length, 2)
val gutterWidth  = remember(gutterDigits) { (gutterDigits * 9 + 40).dp }
```

#### Scroll-to-cursor suppression

Single `LaunchedEffect(state.cursorOffset, state.version)` — no separate flag or race:

```kotlin
LaunchedEffect(state.cursorOffset, state.version) {
    val charDelta = abs(newLen - prevDocState.length)
    val lineDelta = newLineCount - prevDocState.lineCount
    // Suppress for large edits OR multi-line paste (Enter adds exactly 1 → still scrolls)
    if (charDelta >= 100_000 || lineDelta >= 2) return@LaunchedEffect
    // Scroll LazyColumn to cursor display line if outside visible range
}
```

#### Cursor blink animation

A single `InfiniteTransition` is hoisted at the `EditorRenderer` level and the resulting `cursorBlinkAlpha` is passed into `LineView`. This avoids creating one `InfiniteTransition` per visible row.

#### Horizontal scrolling (word-wrap off)

When `wordWrap = false`, a shared `ScrollState` drives a `horizontalScroll` modifier on each `LineView`. The widest line seen (`hMaxContentWidthPx`) is tracked and maintained in a dummy `Spacer` so the `HorizontalScrollbar` range stays correct after lines are deleted.

#### Text change debounce

`textChangedFlow` is a `SharedFlow<Unit>`. The composable collects it with `collectLatest` and invokes `onTextChange(viewModel.getFullText())`. The 150 ms debounce is applied in a `LaunchedEffect` inside `CodeEditor.kt` (not in `EditorViewModel`) so that Compose tests can advance the test clock past the debounce with `waitForIdle()`.

#### Input handling

Key events are processed in `Modifier.onPreviewKeyEvent`:

| Category | Keys |
|---|---|
| Navigation | Arrow keys, Home/End, PgUp/PgDn, Cmd+Home/End, Ctrl+←/→ |
| Editing | Printable characters, Enter (auto-indent), Backspace, Delete, Tab (4 spaces), Shift+Tab (dedent) |
| Clipboard | Cmd/Ctrl+C, Cmd/Ctrl+V, Cmd/Ctrl+X |
| Selection | Shift + any navigation key, Cmd+A |
| History | Cmd/Ctrl+Z (undo), Cmd/Ctrl+Shift+Z / Cmd/Ctrl+Y (redo) |

Both read-only and editable modes support Cmd+C and Cmd+A. All other keys are ignored in read-only mode.

#### Context menu

A `DropdownMenu` appears on right-click (secondary pointer press). Options: Copy, Select All, and (editable only) Cut, Paste, Undo, Redo.

#### Click-to-place cursor

Primary pointer press calls `posToDragOffset()` — it maps the pointer position to the nearest character offset in the `TextLayoutResult` for that display line. Drag extends the selection via `moveCursorTo(offset, extendSelection = true)`.

---

### `LineView` — Single Display Line

```kotlin
@Composable
internal fun LineView(
    displayLine: Int,
    viewModel: EditorViewModel,
    state: EditorDisplayState,
    theme: EditorTheme,
    wordWrap: Boolean,
    cursorBlinkAlpha: Float,   // hoisted from EditorRenderer
    searchMatchRanges: List<IntRange>,
    activeSearchMatch: IntRange?,
    onLayout: ((TextLayoutResult) -> Unit)? = null,
)
```

`LineView` builds an `AnnotatedString` for the display line on each recomposition keyed by `state.styleClock` (style changes) and `state.version` (document changes). The string is composed of:

1. **Syntax spans** — built from `StyleBuffer.styleAt()` using run-length encoding via `nextStyleChangeAfter()` to minimise allocations.
2. **Search-match highlight** — yellow `Background` span over each match in `searchMatchRanges`; active match gets a distinct accent colour.
3. **Inline-error underline** — red (ERROR) or amber (WARNING) `TextDecoration.Underline` span, covering the rest of the logical line from the `InlineEditorError.col` position.
4. **Selection highlight** — `Background` span covering the selected character range.
5. **Cursor** — drawn as a 2 dp animated `Canvas` line at the exact cursor character position, using `TextLayoutResult.getCursorRect()`. Alpha is `cursorBlinkAlpha` (keyframe: 1f at 0–530 ms, 0f at 600–930 ms, 1f at 1000 ms).

---

### Syntax Highlighting Pipeline

There are **two separate abstractions** in `editor-ui`:

| Abstraction | Location | Responsibility |
|---|---|---|
| `LanguageModeProvider` | `editor-core` | Pure tokenization (produces `List<Token>` with char-relative offsets); also validates and detects folds. |
| `SyntaxHighlighter` | `editor-ui` | Compose-side: builds an `AnnotatedString` from a string. Colour + bold/italic decisions live here. |

This separation (Interface Segregation Principle) means `LanguageModeProvider` has no Compose dependency.

#### `SyntaxHighlighter` interface

```kotlin
interface SyntaxHighlighter {
    val mode: LanguageMode
    fun highlight(text: String): AnnotatedString
    fun highlightLine(line: String): AnnotatedString  // defaults to highlight()
}
```

#### `SyntaxHighlighterRegistry`

```kotlin
object SyntaxHighlighterRegistry {
    fun register(highlighter: SyntaxHighlighter)
    fun getHighlighter(mode: LanguageMode): SyntaxHighlighter
    fun registerBuiltinHighlighters()  // registers DefaultSyntaxHighlighter for all 6 modes
}
```

Built-in languages are pre-registered at app startup. Custom languages register a `SyntaxHighlighter` here **and** a `LanguageModeProvider` in `LanguageRegistry` for tokenization.

#### Token → colour rendering

`TokenColorRegistry` maps `(LanguageMode, TokenType)` → `Color`. Individual line annotation is done in `LineView` using `StyleBuffer.styleAt()` for each character position — O(line length) per recomposition, offset by run-length encoding.

---

### `EditorTheme`

```kotlin
data class EditorTheme(
    val background: Color,
    val foreground: Color,
    val lineNumberFg: Color,
    val lineNumberBg: Color,
    val gutterBorder: Color,
    val selectionBg: Color,
    val cursorLine: Color,
    val foldIndicator: Color,
    val indentGuide: Color,
    val errorUnderline: Color,
    val warningUnderline: Color,
    val accent: Color,
    val tokenColors: Map<TokenType, Color>,
) {
    companion object {
        val Dark: EditorTheme   // default — dark background, syntax colours from EditorColors
        val Light: EditorTheme  // light background, VS Code Light+ token colours
    }
    fun spanStyleFor(type: TokenType): SpanStyle
}
```

`EditorRenderer` receives the active theme from `CodeEditor.kt`, which maps the app-level `AppTheme.DARK/LIGHT/SYSTEM` to `EditorTheme.Dark/Light`.

---

### `FoldingSupport.kt` — Fold Detection Helpers

`FoldingSupport.kt` provides stateful fold helpers used primarily by the composable layer:

```kotlin
// editor-ui FoldRegion (used by the detect functions below — different from editor-core!)
data class FoldRegion(val startLine: Int, val endLine: Int, val depth: Int = 0)

data class VisibleLine(
    val originalIndex: Int,
    val text: String,
    val isFoldStart: Boolean = false,
    val isFolded: Boolean = false,
    val foldedLineCount: Int = 0,
)

class FoldState {
    fun isFolded(startLine: Int): Boolean
    fun toggle(startLine: Int)
    fun fold(startLine: Int)
    fun unfold(startLine: Int)
    fun foldAll(regions: List<FoldRegion>)
    fun unfoldAll()
}

@Composable
fun rememberFoldState(): FoldState

fun detectBraceFoldRegions(lines: List<String>): List<FoldRegion>
fun detectXmlFoldRegions(lines: List<String>): List<FoldRegion>
fun detectCommentFoldRegions(lines: List<String>): List<FoldRegion>
fun detectFoldRegions(lines: List<String>, mode: LanguageMode): List<FoldRegion>
fun computeVisibleLines(lines: List<String>, foldState: FoldState): List<VisibleLine>
```

`FoldState` is a mutable composable-layer data structure (distinct from the pure-data `editor-core.FoldingModel`). The fold detection functions use their own `FoldRegion(startLine, endLine, depth: Int = 0)` type (defined in `FoldingSupport.kt`) rather than the `editor-core.FoldRegion(startLine, endLine, placeholder)` type used by `FoldingModel`.

`detectFoldRegions(lines, mode)` is the language-aware dispatcher — it internally calls the appropriate `detectBrace/Xml/CommentFoldRegions` helper based on the `FoldingStyle` of the `LanguageModeProvider`. `computeVisibleLines(lines, foldState)` returns the filtered `List<VisibleLine>` for render-time use.

`ui-shared/CodeFolding.kt` re-exports all of these types and functions as backward-compatible aliases so callers that imported from `ui-shared` continue to compile without changes.

---

## Public API — `CodeEditor` Composable

`CodeEditor` in `ui-shared/components/CodeEditor.kt` wraps `EditorRenderer` and adds the toolbar, search bar, and lifecycle wiring:

```kotlin
@Composable
fun CodeEditor(
    text: String,
    onTextChange: ((String) -> Unit)? = null,   // null → read-only
    language: SyntaxLanguage = SyntaxLanguage.PLAIN,
    showToolbar: Boolean = true,
    enableFolding: Boolean = true,
    enableSearch: Boolean = true,
    enableFormat: Boolean = true,
    enableWordWrap: Boolean = true,
    enableCopy: Boolean = true,
    enableDownload: Boolean = false,
    onDownload: (() -> Unit)? = null,
    placeholder: String = "",
    inlineErrors: List<InlineEditorError> = emptyList(),
    testTagPrefix: String = "code-editor",
)
```

`SyntaxLanguage` is a `ui-shared` enum mirroring `LanguageMode` values (`JSON`, `XML`, `HTML`, `GRAPHQL`, `JAVASCRIPT`, `PLAIN`). It decouples call sites from the `editor-core` type — internally it is converted to `LanguageMode` via `SyntaxLanguage.toLanguageMode()`. `ui-shared/CodeFolding.kt` provides backward-compatible type aliases (`FoldRegion`, `FoldState`, `VisibleLine`) that re-export the canonical types from `editor-ui`.

Toolbar actions: pretty-print / minify (Format), Fold All, Unfold All, Word-Wrap toggle, Copy, Download.
Search bar: incremental match highlighting, match count `n/m`, Previous/Next navigation.

**ViewModel lifecycle:** `CodeEditor` creates one `EditorViewModel` per instance via `remember(language)`. It is disposed via `DisposableEffect { onDispose { viewModel.dispose() } }`, which cancels the `IdleLexer` and all coroutines.

**External text sync:** A `LaunchedEffect(text)` guards against feedback loops:
```kotlin
LaunchedEffect(text) {
    if (viewModel.getFullText() != text) viewModel.onExternalTextChanged(text)
}
```

Usage sites:
- `BodyEditor.kt` — request body (JSON, XML, GraphQL, raw text, etc.)
- `ScriptEditor.kt` — pre-request and post-request scripts (JavaScript)
- `ResponseViewer.kt` — response body (read-only)

---

## Thread Safety Summary

| Operation | Thread | Mechanism |
|---|---|---|
| `document.insert/delete/replaceAll` | `Dispatchers.Default` (under `Mutex`) | `Mutex.withLock` in `EditorViewModel` |
| `styleBuffer.invalidateFrom` | UI thread (in `applyReplace`) | Called before coroutine is launched |
| `styleBuffer.applyStyle/grow` | `Dispatchers.Default` (IdleLexer) | Memory barrier via StateFlow update |
| `displayLineMap.setFolded/reset` | `Dispatchers.Default` (fold job) | Called inside `mutex.withLock` |
| `_state.update { }` | Any thread | `StateFlow.update` is thread-safe |
| `EditorRenderer` reads `state` | UI thread | `collectAsState()` |

---

## Test Coverage

### `editor-core` (commonTest)

| Test class | Coverage |
|---|---|
| `GapBufferTest` | Insert, delete, move-gap, boundary conditions, growth |
| `DocumentModelTest` | Line splitting, multi-line edits, version increments |
| `EditorDocumentTest` | EditorDocument contract compliance |
| `EditorEngineTest` | Functional state transitions |
| `LineIndexTest` | Offset↔line mapping, incremental updates, lazy steps |
| `CursorStateTest` | Cursor movement, anchor pinning |
| `SelectionModelTest` | Range normalisation |
| `DisplayLineMapCapacityTest` | Large document capacity, prefix sums |
| `DisplayLineMapFoldPreservationTest` | Fold state survives structural edits |
| `FoldingModelTest` | Region detection, open/close/foldAll transitions |
| `JsonModeTest` | JSON tokenisation, fold regions |
| `XmlHtmlModeTest` | XML + HTML tokenisation |
| `JavaScriptModeTest` | JS tokenisation |
| `LanguageModeTest` | Registry dispatch, file-extension detection |
| `PerformanceBenchmarkTest` | Throughput and latency gates |

### `editor-ui` (commonTest)

| Test class | Coverage |
|---|---|
| `EditorViewModelFixTest` | Mutation correctness, undo/redo, cursor navigation |
| `DiagnosticsAndFoldUpdateTest` | Inline error spawning + fold interaction |
| `PerformanceIssueReproTest` | Large-document regression |

### `ui-shared` (commonTest — editor-related tests)

| Test class | Coverage |
|---|---|
| `EditorArchitectureTest` | Architectural invariants: clean layer separation, no illegal cross-layer dependencies |
| `EditorEngineIntegrationTest` | Integrated editor operations through the shared API |
| `EditorQaUnitTest` | Unit-level QA coverage for editor shared behaviour |
| `BodyEditorStateTest` | Body editor tab state: switching, reset, content preservation |
| `CodeFoldingTest` | Brace-based, tag-based, and comment-based fold region detection and state |
| `SyntaxHighlighterTest` | JSON/XML/HTML/GraphQL/JS token colorization correctness |
| `LargeDocumentHighlightingTest` | Highlighting performance and correctness on large payloads |
| `IssueFixTest` | Regression guards for tracked `ui-shared` bugs |
| `PreReleaseQaTest` | Pre-release sanity checks for the shared layer |
| `RequestExecutorLogResolutionTest` | Variable resolution in log/request-executor calls |

### `ui-desktop` (desktopTest, Compose Desktop UI tests)

| Test class | Coverage |
|---|---|
| `EditorLargePasteScrollUiTest` | 1) Paste into empty editor → line 0 stays visible; 2) 220 K-char paste does not scroll; 3) gutter width unchanged at 9→10 line boundary |
| `EditorQaUiTest` | Editor component smoke tests at the Compose Desktop UI level |
| `EditorInteractionBugTest` | Regression guards for tracked editor interaction bugs |
| `EditorInputCorrectnessTest` | Typed input, paste, and backspace produce correct document state |
| `EditorScrollAndSelectionBugTest` | Scroll and selection consistency after edits |
| `EditorScrollKeyClickBackspaceUndoTest` | Compound keyboard + click + undo sequences |
| `EditorConstraintSelectionContextMenuTest` | Context menu presence and actions on constrained selections |
| `EditorV2RegressionTest` | V2 editor architecture regression suite |
| `EditorNoWrapRegressionTest` | No-wrap mode invariants: no line wrap, horizontal scroll range correct |
| `EditorKnownIssuesBugTest` | Guards preventing previously-fixed known bugs from recurring |
| `GutterLayoutStabilityTest` | Gutter width stability across content changes and fold toggles |
| `LargeTextEditorUiTest` | Large document load and render — no jank, correct display line count |
| `LargePayloadSaveUiTest` | Large body save + reload round-trip correctness |
