# ReqLab Editor Architecture

## Overview

The code editor in ReqLab is a **Compose Multiplatform–native** implementation
targeting `jvm("desktop")` and `wasmJs{browser()}`.  It is split into two layers:

| Layer | Module | Purpose |
|-------|--------|---------|
| Data contracts | `editor-core` | Pure Kotlin data types; no Compose, no runtime dependencies |
| UI components | `ui-shared` | Compose composables consumed by `ui-desktop` and `ui-web` |

---

## Why Not WebView + CodeMirror / Monaco?

The request to embed CodeMirror or Monaco via a WebView was evaluated and
rejected for the following reasons:

1. **No built-in WebView in Compose Desktop.**  
   Embedding a browser engine on the JVM requires JCEF (Java Chromium Embedded
   Framework), which adds ~150 MB to the distribution, requires per-platform
   native binaries, and complicates the Gradle build considerably.

2. **KMP wasmJs target is incompatible.**  
   The `ui-web` target runs in a browser *as* a WebAssembly application.
   Nesting a second browser sandbox inside the same DOM yields no benefit and
   creates complex sandboxing/security constraints.

3. **`BasicTextField.onTextLayout` solves the core alignment problem.**  
   The alignment bug that motivated the WebView request was a Session 3 regression
   (per-line `Text` composables not tracking word-wrap).  
   `onTextLayout` provides exact pixel coordinates for every visual line, making
   a native fix both simpler and lighter than a WebView integration.

4. **Compose is the single UI toolkit for the whole project.**  
   Introducing a second rendering stack (HTML/CSS/JS) would fragment the styling
   system (themes, fonts, colours) and double the maintenance burden for future
   UI work.

---

## Module Structure

```
editor-core/
└── src/commonMain/kotlin/com/reqlab/editor/core/
    └── InlineEditorError.kt   ← InlineEditorError, InlineErrorSeverity

ui-shared/
└── src/commonMain/kotlin/com/reqlab/ui/shared/components/
    ├── CodeEditor.kt          ← public CodeEditor() composable + internals
    ├── CodeFolding.kt         ← fold detection + FoldState (read-only only)
    └── SyntaxHighlighter.kt   ← tokenization, validation, formatting
```

`ui-shared` declares `api(project(":editor-core"))`, so consumers of
`ui-shared` (desktop shell, web shell) automatically gain the `InlineEditorError`
type without an extra dependency declaration.

---

## Component Responsibilities

### `editor-core` — `InlineEditorError`

A pure data class carrying the location and severity of a parser diagnostic:

```kotlin
data class InlineEditorError(
    val line: Int,               // 1-based
    val col: Int,                // 1-based
    val message: String,
    val severity: InlineErrorSeverity,   // ERROR | WARNING
)
```

No Compose, no coroutines, no serialization — deliberately a leaf module so it
can be used in a future `editor-desktop` or `editor-web` platform module without
dragging in Compose.

---

### `CodeEditor` — public composable

```
CodeEditor(
  text, onTextChange?,     // null → read-only mode
  language,                // syntax highlighting + fold detection
  inlineErrors,            // list of InlineEditorError to underline
  showToolbar, enableFolding, enableSearch, enableFormat,
  enableWordWrap, enableCopy, enableDownload,
  placeholder, testTagPrefix,
)
```

Internally dispatches to two distinct rendering paths:

| Mode | Composable | Folding | Inline errors |
|------|-----------|---------|---------------|
| Read-only | `ReadOnlyCodeContent` → `SmallCodeView` / `LargeCodeView` | ✅ full fold/unfold via `FoldState` | N/A (static content) |
| Editable | `EditableCodeContent` | ✗ (see below) | ✅ red/amber underline spans |

---

### `EditableCodeContent` — pixel-perfect line numbers

**Problem (pre-fix):**  
Session 3 introduced a `Column { forEachIndexed { Text(lineNum) } }` gutter
alongside `BasicTextField`.  Each gutter row had height = 1 logical line, causing
misalignment whenever word-wrap caused a logical line to span multiple visual rows.

**Solution:**  
`BasicTextField` exposes an `onTextLayout: (TextLayoutResult) -> Unit` callback.
`TextLayoutResult` provides:

- `getLineForOffset(charOffset)` → which visual line a character is on
- `getLineTop(visualLine)` / `getLineBottom(visualLine)` → exact pixel bounds

For each logical line `i`, the composable computes:

```
logicalLineHeight[i] = getLineBottom(lastVisLine) − getLineTop(firstVisLine)
```

where `firstVisLine` and `lastVisLine` are the first and last visual rows
occupied by logical line `i`.

The gutter `Column` entries are then explicitly sized with
`Modifier.height(logicalLineHeight[i])`, so the `i`-th line number cell is
exactly as tall as the `i`-th logical line in the text field — regardless of
word-wrap.  Both the gutter column and the `BasicTextField` are siblings inside a
shared `Row` wrapped in a `verticalScroll`, so scrolling is inherently
synchronised.

#### First-frame fallback

`textLayoutResult` is initially `null` (populated only after the first layout
pass).  Before the first frame renders, the gutter falls back to equal-height
rows.  On the next frame `logicalLineHeightsDp` is populated and the gutter
re-renders with correct heights.  The transition is invisible to the user.

---

### Code Folding

Folding is **read-only only**.  `BasicTextField` cannot hide arbitrary character
ranges without a custom `VisualTransformation`; implementing a correct fold
`VisualTransformation` is significantly complex and introduces cursor-offset
mapping bugs.

The toolbar's **Fold All / Unfold All** buttons are hidden when the editor is in
editable mode (`isReadOnly = false`).  No misleading fold indicators appear in
the editable gutter.

Folding continues to work correctly in `ReadOnlyCodeContent` (both
`SmallCodeView` and `LargeCodeView`) via `computeVisibleLines()` in
`CodeFolding.kt`.

---

### Inline Error Highlighting

The `buildHighlightedWithErrors()` private function:

1. Calls `highlightText(text, language)` to produce the syntax-coloured
   `AnnotatedString`.
2. For each `InlineEditorError`, resolves the 1-based `(line, col)` to a
   character offset in the flat string.
3. Appends a `SpanStyle(textDecoration = TextDecoration.Underline, color = …)`
   annotation covering the rest of that logical line:
   - `ERROR` → red (`#FF6B6B`)
   - `WARNING` → amber (`#FFBB44`)

The annotated string is fed directly into `BasicTextField`, so error
highlighting is part of the text itself — not a banner, tooltip, or overlay.

The `BodyEditor` composable computes `inlineErrors` via `remember(bodyContent)`
from `validateJson()` / `validateXml()` and passes them to `CodeEditor`.
`JsonValidationError` carries `line` and `col` from the parser; `XmlValidationError`
has no position info, so the underline covers line 1 as a conservative fallback.

---

### Performance

| Condition | Behaviour |
|-----------|-----------|
| `text.length > 500_000` chars | Syntax highlighting disabled (`SyntaxLanguage.PLAIN`); `BasicTextField` still editable |
| `visibleLines.size > 200` (read-only) | `LargeCodeView` uses `LazyColumn` (virtualised rendering) |
| `visibleLines.size ≤ 200` (read-only) | `SmallCodeView` uses a regular `Column` (enables full text selection) |

---

## Test Coverage

All editor logic is tested in `ui-shared` commonTest without a UI harness:

- **`EditorArchitectureTest`** — 55 tests: state isolation, JSON/XML validation,
  fold detection, fold state transitions, syntax highlighting, search,
  formatting, language detection, performance thresholds, edge cases.
- **`BodyEditorStateTest`** — 28 tests: JSON/XML validators, `MutableFormDataRow`.
- **`DesktopShellUiTest`** (Compose UI test) — includes
  `editors_show_line_numbers_for_body_and_scripts` which verifies the
  `"body-editor-line-numbers"` and `"script-editor-line-numbers"` test tags are
  present and visible.

---

## Future: `editor-desktop` / `editor-web` Platform Modules

If platform-specific editor capabilities are needed in the future (e.g. JCEF
WebView on desktop, native `<textarea>` on web), the recommended path is:

1. Add `editor-desktop/` as a new Gradle module with `jvm("desktop")` target
   only, depending on `:editor-core`.
2. Add `editor-web/` as a new Gradle module with `wasmJs` target only.
3. Define an `expect fun PlatformCodeEditor(…)` in `ui-shared` and provide
   `actual` implementations in `editor-desktop` and `editor-web`.

---

## 100 MB Scalability Strategy (Notepad++/Postman Class)

The current editor is stable for normal and large formatted payloads, but true
single-line multi-MB payloads can still hit Compose Desktop measurement limits.
To remove practical limits, the architecture should move from a full-string UI
model to a strict windowed document model.

### Target Architecture

1. `EditorDocumentStore`
- Owns full text as chunked buffers (rope/piece-table style).
- Supports O(log n) insert/delete/replace without rebuilding whole strings.

2. `ViewportModel`
- Exposes only a visible window (+ small guard bands) as display text.
- Maps viewport offsets to document offsets bidirectionally.
- Keeps selection/caret in document coordinates.

3. `EditorLayoutModel`
- Maintains per-line metadata cache (line starts, fold state, token state).
- Incrementally updates only affected ranges after edits.

4. `HighlighterEngine`
- Tokenizes incrementally by dirty ranges.
- Avoids global re-highlight and supports million-line documents.

### Required Runtime Rules

1. Never bind full document text directly to `BasicTextField` for very large docs.
2. Never compute gutter heights from entire document in one pass.
3. Keep folding state independent from parser validity, with tolerant fallback.
4. Preserve replace-all semantics (Cmd+A + paste/delete) at document level.

### Delivery Plan

1. Phase A (done in current patch set)
- Capped display mode in unified path.
- Robust replace-all / clear-all behavior in capped mode.
- Tolerant fold-region fallback to avoid indicator flicker while typing invalid JSON.

2. Phase B (next)
- Introduce `EditorDocumentStore` + `ViewportModel` in `editor-core`.
- Route desktop/web editors through viewport text instead of full text.

3. Phase C
- Incremental tokenizer + incremental fold recomputation.
- Add stress tests at 10 MB, 25 MB, 50 MB, and 100 MB.

4. Phase D
- Performance SLO gates in CI: open-time, type-latency, replace-all-latency.

This keeps the data-contract module (`editor-core`) stable and avoids changes
to `ui-shared` consumers.
