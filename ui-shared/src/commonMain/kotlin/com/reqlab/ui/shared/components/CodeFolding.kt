package com.reqlab.ui.shared.components

import androidx.compose.runtime.Composable
import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.ui.FoldRegion as EditorFoldRegion
import com.reqlab.editor.ui.VisibleLine as EditorVisibleLine
import com.reqlab.editor.ui.FoldState as EditorFoldState
import com.reqlab.editor.ui.rememberFoldState as editorRememberFoldState
import com.reqlab.editor.ui.detectFoldRegions as editorDetectFoldRegions
import com.reqlab.editor.ui.detectBraceFoldRegions as editorDetectBraceFoldRegions
import com.reqlab.editor.ui.detectXmlFoldRegions as editorDetectXmlFoldRegions
import com.reqlab.editor.ui.detectCommentFoldRegions as editorDetectCommentFoldRegions
import com.reqlab.editor.ui.computeVisibleLines as editorComputeVisibleLines

// ── Data structures (backward-compatible re-exports) ────────────

/**
 * Backward-compatible type alias for [EditorFoldRegion].
 * New code should use [com.reqlab.editor.ui.FoldRegion] directly.
 */
typealias FoldRegion = EditorFoldRegion

/**
 * Backward-compatible type alias for [EditorVisibleLine].
 * New code should use [com.reqlab.editor.ui.VisibleLine] directly.
 */
typealias VisibleLine = EditorVisibleLine

/**
 * Backward-compatible type alias for [EditorFoldState].
 * New code should use [com.reqlab.editor.ui.FoldState] directly.
 */
typealias FoldState = EditorFoldState

@Composable
fun rememberFoldState(): FoldState = editorRememberFoldState()

// ── Region detection (delegates to editor-ui) ───────────────────

fun detectBraceFoldRegions(lines: List<String>): List<FoldRegion> =
    editorDetectBraceFoldRegions(lines)

fun detectXmlFoldRegions(lines: List<String>): List<FoldRegion> =
    editorDetectXmlFoldRegions(lines)

fun detectCommentFoldRegions(lines: List<String>): List<FoldRegion> =
    editorDetectCommentFoldRegions(lines)

fun detectFoldRegions(lines: List<String>, language: SyntaxLanguage): List<FoldRegion> =
    editorDetectFoldRegions(lines, language.toLanguageMode())

// ── Visible-line computation (delegates to editor-ui) ───────────

fun computeVisibleLines(
    lines: List<String>,
    foldRegions: List<FoldRegion>,
    foldState: FoldState,
): List<VisibleLine> = editorComputeVisibleLines(lines, foldRegions, foldState)
