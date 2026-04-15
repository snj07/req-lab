package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.editor.core.InlineEditorError
import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.ui.EditorColors
import com.reqlab.editor.ui.EditorRendererV2
import com.reqlab.editor.ui.EditorTheme
import com.reqlab.editor.ui.EditorViewModelV2
import com.reqlab.editor.ui.SyntaxHighlighterRegistry
import com.reqlab.ui.shared.platform.copyToClipboard
import com.reqlab.ui.shared.platform.readFromClipboard
import com.reqlab.ui.shared.theme.LightAppColors
import com.reqlab.ui.shared.theme.LocalAppColors
import com.reqlab.ui.shared.theme.ReqLabColors

/**
 * Threshold above which [CodeEditorV2] is used instead of [CodeEditor].
 * Below this size the difference is imperceptible; above it, BasicTextField
 * hangs the UI thread on measure / highlight.
 */
private const val V2_SWITCH_CHARS = 100_000

/**
 * Scintilla-inspired code editor for large files.
 *
 * Same external API as [CodeEditor] — callers can substitute this directly.
 *
 * Internally uses:
 *  – [GapBuffer] + [LineIndex] for O(1) amortized text mutation
 *  – [StyleBuffer] + [IdleLexer] for incremental background highlighting
 *  – [DisplayLineMap] for O(log n) fold mapping
 *  – [EditorRendererV2] with [LazyColumn] so only visible lines are measured
 *
 * @param text          Current document content.
 * @param onTextChange  Editable callback; null → read-only.
 * @param language      Syntax highlighting language.
 * @param showToolbar   Whether to show the toolbar row.
 * @param enableCopy    Show copy-to-clipboard button.
 * @param enableDownload Show download button.
 * @param onDownload    Download callback.
 * @param inlineErrors  Inline diagnostics (1-based line/col).
 * @param testTagPrefix Compose test-tag prefix.
 */
@Composable
fun CodeEditorV2(
    text: String,
    onTextChange: ((String) -> Unit)? = null,
    language: SyntaxLanguage = SyntaxLanguage.PLAIN,
    modifier: Modifier = Modifier,
    showToolbar: Boolean = true,
    enableCopy: Boolean = true,
    enableDownload: Boolean = false,
    onDownload: (() -> Unit)? = null,
    inlineErrors: List<InlineEditorError> = emptyList(),
    testTagPrefix: String = "code-editor-v2",
) {
    val isReadOnly   = onTextChange == null
    val languageMode = language.toLanguageMode()

    // ── ViewModel lifecycle ───────────────────────────────────────

    val viewModel = remember(languageMode) {
        if (!SyntaxHighlighterRegistry.hasHighlighter(LanguageMode.PLAIN_TEXT)) {
            SyntaxHighlighterRegistry.registerBuiltinHighlighters()
        }
        EditorViewModelV2(initialText = text, languageMode = languageMode)
    }

    // Sync external text changes into the ViewModel
    LaunchedEffect(text) {
        viewModel.onExternalTextChanged(text)
    }

    // Cancel background coroutines when this composable leaves the tree
    DisposableEffect(viewModel) {
        onDispose { viewModel.dispose() }
    }

    Column(modifier = modifier.testTag(testTagPrefix)) {
        // ── Toolbar ──────────────────────────────────────────────
        if (showToolbar) {
            CodeEditorV2Toolbar(
                language   = language,
                isReadOnly = isReadOnly,
                onCopy     = if (enableCopy) {{ copyToClipboard(viewModel.getFullText()) }} else null,
                onDownload = if (enableDownload) onDownload else null,
                testTagPrefix = testTagPrefix,
            )
        }

        // ── Content ───────────────────────────────────────────────
        EditorRendererV2(
            viewModel      = viewModel,
            isReadOnly     = isReadOnly,
            language       = language.toLanguageMode(),
            theme          = currentEditorTheme(),
            testTagPrefix  = testTagPrefix,
            modifier       = Modifier.weight(1f).fillMaxWidth(),
            onTextChange   = onTextChange,
            onPasteRequest = { readFromClipboard() },
            onCopyRequest  = { text -> copyToClipboard(text) },
        )
    }
}

// ── Toolbar ──────────────────────────────────────────────────────

@Composable
private fun CodeEditorV2Toolbar(
    language: SyntaxLanguage,
    isReadOnly: Boolean,
    onCopy: (() -> Unit)?,
    onDownload: (() -> Unit)?,
    testTagPrefix: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReqLabColors.SurfaceContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("$testTagPrefix-toolbar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Language badge
        Text(
            text       = language.name,
            fontSize   = 10.sp,
            fontWeight = FontWeight.Medium,
            color      = ReqLabColors.Primary,
            modifier   = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(ReqLabColors.Primary.copy(alpha = 0.10f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )

        Spacer(Modifier.weight(1f))

        if (onCopy != null) {
            IconButton(
                onClick  = onCopy,
                modifier = Modifier.size(28.dp).testTag("$testTagPrefix-copy-button"),
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint     = ReqLabColors.OnSurfaceDim,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (onDownload != null) {
            IconButton(
                onClick  = onDownload,
                modifier = Modifier.size(28.dp).testTag("$testTagPrefix-download-button"),
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Download",
                    tint     = ReqLabColors.OnSurfaceDim,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }

    Box(Modifier.fillMaxWidth().height(1.dp).background(ReqLabColors.Border))
}

// ── Language mapping ──────────────────────────────────────────────

/**
 * Maps [SyntaxLanguage] (used by the legacy CodeEditor API) to [LanguageMode]
 * (used by editor-core).  Now delegated to [SyntaxLanguage.toLanguageMode].
 */
// Removed: extension is now a member of SyntaxLanguage

// ── Theme helper ──────────────────────────────────────────────────

/**
 * Builds an [EditorTheme] that matches the current [LocalAppColors] palette.
 * Light palette  → [EditorTheme.Light].
 * Dark palette   → colours derived from the palette so the editor background,
 *                  gutter, and scrollbars all align with the rest of the app UI.
 */
@Composable
private fun currentEditorTheme(): EditorTheme {
    val p = LocalAppColors.current
    return if (p === LightAppColors) {
        EditorTheme.Light
    } else {
        EditorTheme(
            background       = p.surface,           // 0xFF1E1F32 – matches cards/panels
            foreground       = p.onSurface,          // 0xFFD4D4E4 – readable text
            lineNumberFg     = p.onSurfaceDim,       // 0xFF6C6C85 – muted numbers
            lineNumberBg     = p.surfaceVariant,     // 0xFF252640 – slightly off-surface
            gutterBorder     = p.border,             // 0xFF383952
            selectionBg      = p.primaryContainer,  // 0xFF3D4580 – visible highlight
            cursorLine       = p.surfaceHigh,        // 0xFF30314D – subtle current-line bg
            foldIndicator    = p.onSurfaceDim,
            indentGuide      = p.borderLight,        // 0xFF45466A
            errorUnderline   = p.error,              // 0xFFE06C75
            warningUnderline = EditorColors.warningUnderline,
            accent           = p.primary,            // 0xFF7B8DEF – cursor + selection accent
        )
    }
}
