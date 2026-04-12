package com.reqlab.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.editor.core.*

/**
 * Configuration for the CodeEditorView composable.
 */
data class EditorConfig(
    val readOnly: Boolean = false,
    val showLineNumbers: Boolean = true,
    val showFoldIndicators: Boolean = true,
    val showIndentGuides: Boolean = true,
    val wordWrap: Boolean = false,
    val fontSize: Float = 13f,
    val lineHeight: Float = 20f,
    val tabSize: Int = 2,
    val theme: EditorTheme = EditorTheme.Dark,
)

/**
 * Generic, reusable code editor composable.
 *
 * This is the primary UI entry point for the editor engine.
 * It renders text with syntax highlighting, line numbers,
 * fold indicators, indent guides, and inline error highlighting.
 *
 * Uses virtualized rendering via LazyColumn for large files.
 *
 * @param state The current editor state from EditorEngine
 * @param onTextChange Callback when text is modified
 * @param modifier Compose modifier
 * @param config Editor configuration
 */
@Composable
fun CodeEditorView(
    state: EditorState,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    config: EditorConfig = EditorConfig(),
) {
    val engine = remember { EditorEngine() }
    val theme = config.theme
    val fontSize = config.fontSize.sp
    val lineHeightSp = config.lineHeight.sp

    // Compute visible lines (respecting folds)
    val visibleLines = remember(state.document, state.folding) {
        engine.visibleLines(state)
    }

    // Tokenize only visible range
    val tokenCache = remember(state.document, state.languageMode) {
        if (state.document.lineCount <= 5000) {
            engine.tokenizeRange(state, 1, state.document.lineCount)
        } else {
            emptyMap() // lazy tokenize per-line for huge files
        }
    }

    // Line number gutter width
    val lineNumberWidth = remember(state.document.lineCount) {
        val digits = state.document.lineCount.toString().length
        (digits * 10 + 16).dp
    }

    val lazyListState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()

    Row(modifier = modifier.background(theme.background)) {
        // ── Gutter (line numbers + fold indicators) ─────────────────
        if (config.showLineNumbers) {
            EditorGutter(
                visibleLines = visibleLines,
                foldingModel = state.folding,
                theme = theme,
                fontSize = config.fontSize,
                lineHeight = config.lineHeight,
                gutterWidth = lineNumberWidth,
                showFoldIndicators = config.showFoldIndicators,
                lazyListState = lazyListState,
                onToggleFold = { lineNum ->
                    val newState = engine.toggleFold(state, lineNum)
                    onTextChange(newState.text) // trigger recomposition
                },
            )
        }

        // ── Editor content ──────────────────────────────────────────
        if (config.readOnly) {
            ReadOnlyEditorContent(
                visibleLines = visibleLines,
                tokenCache = tokenCache,
                state = state,
                engine = engine,
                theme = theme,
                config = config,
                lazyListState = lazyListState,
                horizontalScrollState = horizontalScrollState,
            )
        } else {
            EditableEditorContent(
                state = state,
                onTextChange = onTextChange,
                tokenCache = tokenCache,
                engine = engine,
                theme = theme,
                config = config,
            )
        }
    }
}

/**
 * Gutter column with line numbers and fold indicators.
 */
@Composable
private fun EditorGutter(
    visibleLines: List<Pair<Int, String>>,
    foldingModel: FoldingModel,
    theme: EditorTheme,
    fontSize: Float,
    lineHeight: Float,
    gutterWidth: Dp,
    showFoldIndicators: Boolean,
    lazyListState: LazyListState,
    onToggleFold: (Int) -> Unit,
) {
    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .width(gutterWidth)
            .fillMaxHeight()
            .background(theme.lineNumberBg)
            .drawBehind {
                // Right border
                drawLine(
                    color = theme.gutterBorder,
                    start = Offset(size.width - 1f, 0f),
                    end = Offset(size.width - 1f, size.height),
                    strokeWidth = 1f,
                )
            },
    ) {
        itemsIndexed(visibleLines) { _, (lineNum, _) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(lineHeight.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Fold indicator
                if (showFoldIndicators && foldingModel.isFoldStart(lineNum)) {
                    val isCollapsed = foldingModel.isCollapsed(lineNum)
                    androidx.compose.material3.Text(
                        text = if (isCollapsed) ">" else "v",
                        color = theme.foldIndicator,
                        fontSize = 8.sp,
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .width(12.dp),
                    )
                } else {
                    Spacer(Modifier.width(14.dp))
                }

                // Line number
                androidx.compose.material3.Text(
                    text = lineNum.toString(),
                    color = theme.lineNumberFg,
                    fontSize = fontSize.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }
}

/**
 * Read-only editor content with virtualized rendering.
 */
@Composable
private fun ReadOnlyEditorContent(
    visibleLines: List<Pair<Int, String>>,
    tokenCache: Map<Int, List<Token>>,
    state: EditorState,
    engine: EditorEngine,
    theme: EditorTheme,
    config: EditorConfig,
    lazyListState: LazyListState,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
) {
    val fontSize = config.fontSize.sp
    val lineHeightDp = config.lineHeight.dp

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (!config.wordWrap) Modifier.horizontalScroll(horizontalScrollState)
                else Modifier
            ),
    ) {
        itemsIndexed(visibleLines) { _, (lineNum, lineText) ->
            val tokens = tokenCache[lineNum] ?: engine.tokenizeLine(state, lineNum)
            val annotated = buildHighlightedLine(lineText, tokens, state.diagnostics, lineNum, theme)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = lineHeightDp)
                    .then(indentGuideModifier(lineText, config, theme)),
            ) {
                androidx.compose.material3.Text(
                    text = annotated,
                    fontSize = fontSize,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = config.lineHeight.sp,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

/**
 * Editable editor content using a single BasicTextField (unified document model).
 */
@Composable
private fun EditableEditorContent(
    state: EditorState,
    onTextChange: (String) -> Unit,
    tokenCache: Map<Int, List<Token>>,
    engine: EditorEngine,
    theme: EditorTheme,
    config: EditorConfig,
) {
    val fontSize = config.fontSize.sp

    BasicTextField(
        value = state.text,
        onValueChange = onTextChange,
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(start = 4.dp),
        textStyle = androidx.compose.ui.text.TextStyle(
            color = theme.foreground,
            fontSize = fontSize,
            fontFamily = FontFamily.Monospace,
            lineHeight = config.lineHeight.sp,
        ),
        cursorBrush = SolidColor(theme.foreground),
    )
}

// ── Helpers ─────────────────────────────────────────────────────────────

/**
 * Build an AnnotatedString with syntax highlighting and error underlines
 * for a single line.
 */
internal fun buildHighlightedLine(
    lineText: String,
    tokens: List<Token>,
    diagnostics: List<InlineEditorError>,
    lineNumber: Int,
    theme: EditorTheme,
): AnnotatedString {
    if (lineText.isEmpty()) return AnnotatedString("")

    return buildAnnotatedString {
        if (tokens.isEmpty()) {
            withStyle(SpanStyle(color = theme.foreground)) {
                append(lineText)
            }
        } else {
            var last = 0
            for (token in tokens) {
                val start = token.startOffset.coerceIn(0, lineText.length)
                val end = token.endOffset.coerceIn(start, lineText.length)
                // Fill gap before token
                if (start > last) {
                    withStyle(SpanStyle(color = theme.foreground)) {
                        append(lineText.substring(last, start))
                    }
                }
                if (end > start) {
                    withStyle(theme.spanStyleFor(token.type)) {
                        append(lineText.substring(start, end))
                    }
                }
                last = end
            }
            // Remaining text after last token
            if (last < lineText.length) {
                withStyle(SpanStyle(color = theme.foreground)) {
                    append(lineText.substring(last))
                }
            }
        }

        // Apply error underlines for this line
        for (error in diagnostics) {
            if (error.line == lineNumber) {
                val errorCol = (error.col - 1).coerceIn(0, lineText.length)
                val errorEnd = (errorCol + 1).coerceAtMost(lineText.length)
                if (errorEnd > errorCol) {
                    addStyle(
                        SpanStyle(
                            textDecoration = TextDecoration.Underline,
                            color = if (error.severity == InlineErrorSeverity.ERROR)
                                theme.errorUnderline else theme.warningUnderline,
                        ),
                        start = errorCol,
                        end = errorEnd.coerceAtMost(this.length),
                    )
                }
            }
        }
    }
}

/**
 * Creates a modifier that draws indent guides as dashed vertical lines.
 */
internal fun indentGuideModifier(
    lineText: String,
    config: EditorConfig,
    theme: EditorTheme,
): Modifier {
    if (!config.showIndentGuides) return Modifier

    val indentLevel = lineText.takeWhile { it == ' ' }.length / config.tabSize
    if (indentLevel == 0) return Modifier

    return Modifier.drawBehind {
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f), 0f)
        for (level in 1..indentLevel) {
            val x = (level * config.tabSize * 7.5f) + 4f // approximate char width
            drawLine(
                color = theme.indentGuide,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f,
                pathEffect = dashEffect,
            )
        }
    }
}
