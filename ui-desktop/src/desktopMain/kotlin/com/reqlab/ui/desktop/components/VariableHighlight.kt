package com.reqlab.ui.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.reqlab.ui.desktop.state.AppState
import com.reqlab.ui.desktop.state.MutableKeyValue
import com.reqlab.ui.desktop.theme.CodeFontFamily
import com.reqlab.ui.desktop.theme.ReqLabColors

// ── Constants ─────────────────────────────────────────────────────

/**
 * Strict variable-name pattern — only `[a-zA-Z0-9_]` characters are valid
 * (Issue 5). Tokens like `{{my var}}` or `{{a-b}}` are intentionally excluded.
 */
private val VARIABLE_REGEX = Regex("""\{\{([a-zA-Z0-9_]+)\}\}""")
private const val VARIABLE_TAG = "variable"

/** Orange token colour — mirrors Postman's variable highlight style. */
internal val VariableHighlightColor = Color(0xFFE67E22)

private val VARIABLE_SPAN_STYLE = SpanStyle(
    color = VariableHighlightColor,
    fontWeight = FontWeight.SemiBold,
    background = VariableHighlightColor.copy(alpha = 0.10f),
)

// ── Public utilities (also used in unit tests) ─────────────────────

/**
 * Returns every `{{name}}` variable name found in [text], in order of
 * appearance. Only strict [a-zA-Z0-9_] variable names are matched.
 */
internal fun parseVariableNames(text: String): List<String> =
    VARIABLE_REGEX.findAll(text).map { it.groupValues[1] }.toList()

/**
 * Builds an [AnnotatedString] that colour-highlights every `{{variable}}`
 * token with [VARIABLE_SPAN_STYLE] and attaches a VARIABLE_TAG string
 * annotation (containing the variable name) to each span so click/cursor
 * detection can retrieve the name.
 */
internal fun highlightVariables(text: String): AnnotatedString = buildAnnotatedString {
    var lastIndex = 0
    for (match in VARIABLE_REGEX.findAll(text)) {
        append(text.substring(lastIndex, match.range.first))
        val spanStart = length
        withStyle(VARIABLE_SPAN_STYLE) { append(match.value) }
        addStringAnnotation(VARIABLE_TAG, match.groupValues[1], spanStart, length)
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) append(text.substring(lastIndex))
}

// ── VariableAwareTextField ────────────────────────────────────────────

/**
 * A drop-in replacement for a plain [BasicTextField] that:
 *
 *  1. **Colour-highlights** every `{{variable}}` token in orange.
 *  2. **Shows a floating popup ONLY when the user explicitly CLICKS** on a
 *     highlighted variable span — never on keyboard events, never on startup.
 *
 * ### Popup trigger design (Fixes Issues 1 & 4)
 *
 * The old implementation fired `LaunchedEffect(fieldValue.selection)` on every
 * cursor change, including typing, Backspace, and app initialisation, causing
 * the popup to appear on startup (Issue 1) and when pressing Backspace (Issue 4).
 *
 * The new design uses a two-step click-detection pattern:
 *  - A `pendingClickRef` flag (a plain non-State object, so toggling it never
 *    triggers recomposition) is set to `true` ONLY by real pointer-press events.
 *  - Inside `onValueChange`, the flag is read and immediately cleared. Only if
 *    it was set will the annotation at the new cursor position be checked.
 *  - Keyboard events (typing, Backspace, arrows) call `onValueChange` too, but
 *    they never set `pendingClickRef`, so they can never open the popup.
 *
 * When [state] is `null` the component degrades gracefully to a plain text
 * field with no highlighting or popup.
 */
@Composable
internal fun VariableAwareTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    textStyle: TextStyle = TextStyle(
        color = ReqLabColors.OnSurface,
        fontSize = 14.sp,
        fontFamily = CodeFontFamily,
    ),
    state: AppState? = null,
) {
    // Internal TextFieldValue that carries the highlighted AnnotatedString.
    var fieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                if (state != null) highlightVariables(value) else AnnotatedString(value),
            ),
        )
    }

    // Popup visibility — null means hidden. Set ONLY from the click handler
    // inside onValueChange. Never set during initialisation (fixes Issue 1).
    var popupVariable by remember { mutableStateOf<String?>(null) }

    // Non-State flag: toggled by pointer-press, read+cleared in onValueChange.
    // Using a plain object (not mutableStateOf) ensures setting it never
    // triggers an unnecessary recomposition.
    val pendingClickRef = remember { object { var value = false } }

    // Sync when an external value change overrides the text (e.g. params table
    // updates the URL, or a saved request is loaded). Rebuilds highlighting only
    // when the plain text has actually changed to avoid losing cursor position.
    LaunchedEffect(value) {
        if (fieldValue.text != value) {
            val annotated = if (state != null) highlightVariables(value) else AnnotatedString(value)
            fieldValue = fieldValue.copy(annotatedString = annotated)
        }
    }

    // Box is a bare container (no modifier): it just groups BasicTextField and
    // the popup trigger so both are emitted at the same composable level.
    // The full caller `modifier` (including testTag) goes on BasicTextField
    // directly so that semantic actions like RequestFocus remain accessible —
    // critical for Compose test performTextInput() to locate and focus the field.
    Box {
        BasicTextField(
            value = fieldValue,
            onValueChange = { new ->
                val annotated = if (state != null) highlightVariables(new.text) else AnnotatedString(new.text)
                val newFieldValue = TextFieldValue(
                    annotatedString = annotated,
                    selection = new.selection,
                    composition = new.composition,
                )
                fieldValue = newFieldValue
                onValueChange(new.text)

                // Evaluate popup only when a pointer click caused this change.
                // Keyboard events (typing, Backspace, arrow keys) never reach
                // this branch — they cannot open the popup (fixes Issues 1 & 4).
                if (pendingClickRef.value) {
                    pendingClickRef.value = false
                    if (state != null && new.selection.collapsed) {
                        val cursor = new.selection.start
                        val found = annotated
                            .getStringAnnotations(VARIABLE_TAG, cursor, cursor)
                            .firstOrNull()
                        // null → close any open popup (click outside a token)
                        popupVariable = found?.item
                    } else {
                        popupVariable = null
                    }
                }
            },
            singleLine = singleLine,
            textStyle = textStyle,
            cursorBrush = SolidColor(ReqLabColors.Primary),
            // Apply the caller's modifier (layout + testTag) here so the
            // BasicTextField node owns the tag and its RequestFocus action.
            // The pointerInput observer is appended to detect real clicks only.
            modifier = modifier.pointerInput(state != null) {
                if (state == null) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) {
                            pendingClickRef.value = true
                        }
                    }
                }
            },
            decorationBox = { inner ->
                if (fieldValue.text.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = ReqLabColors.OnSurfaceDim,
                        fontSize = textStyle.fontSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                inner()
            },
        )

        if (state != null) {
            popupVariable?.let { varName ->
                VariableEditorPopup(
                    variableName = varName,
                    state = state,
                    onDismiss = { popupVariable = null },
                )
            }
        }
    }
}

// ── Variable editor popup ─────────────────────────────────────────────

/**
 * Floating popup for inline environment variable editing.
 *
 * Fixes applied:
 *  - Issue 2: Close (✕) button in the header; ESC + click-outside dismiss.
 *  - Issue 3: Edit field uses [TextFieldValue] state preserving cursor position;
 *    Backspace now removes one character as expected.
 *  - Issue 6: Anchored below-start of its parent text field.
 *  - Issue 7: Save mutates reactive env state; all observers refresh immediately.
 */
@Composable
private fun VariableEditorPopup(
    variableName: String,
    state: AppState,
    onDismiss: () -> Unit,
) {
    val env = state.selectedEnvironment
    val envVar = remember(variableName, env) { env.variables.firstOrNull { it.key == variableName } }
    val resolved = env.toVariableMap()[variableName]

    // TextFieldValue preserves cursor position across recompositions — fixes
    // Issue 3 (Backspace was resetting the entire value with plain String state).
    var editValue by remember(variableName) { mutableStateOf(TextFieldValue(envVar?.value ?: "")) }

    Popup(
        alignment = Alignment.BottomStart,  // anchored below the text field (Issue 6)
        offset = IntOffset(x = 0, y = 4),
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,             // allows keyboard input in the edit field
            dismissOnBackPress = true,    // ESC closes the popup (Issue 2)
            dismissOnClickOutside = true, // click outside closes popup (Issue 2)
        ),
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 240.dp, max = 400.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ReqLabColors.Surface)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(10.dp))
                .padding(12.dp)
                .testTag("variable-editor-popup"),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                // ─ Header: variable name · environment badge · ✕ close button ─
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "{{$variableName}}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VariableHighlightColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = env.name,
                        fontSize = 10.sp,
                        color = ReqLabColors.OnSurfaceDim,
                        maxLines = 1,
                    )
                    // ✕ Close button — Issue 2
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(20.dp).testTag("variable-popup-close"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close variable editor",
                            tint = ReqLabColors.OnSurfaceDim,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                // ─ Current resolved value (or "not found" warning) ─
                if (resolved != null) {
                    Text(
                        text = "Current: $resolved",
                        fontSize = 11.sp,
                        color = ReqLabColors.OnSurfaceDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = "Not defined in \"${env.name}\"",
                        fontSize = 11.sp,
                        color = ReqLabColors.Error,
                        maxLines = 1,
                    )
                }

                HorizontalDivider(color = ReqLabColors.Border)

                // ─ Editable value field ─
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Value",
                        fontSize = 11.sp,
                        color = ReqLabColors.OnSurfaceVariant,
                    )
                    BasicTextField(
                        value = editValue,
                        onValueChange = { editValue = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = ReqLabColors.OnSurface,
                            fontSize = 13.sp,
                            fontFamily = CodeFontFamily,
                        ),
                        cursorBrush = SolidColor(ReqLabColors.Primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(ReqLabColors.SurfaceContainer)
                            .border(1.dp, ReqLabColors.Border, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("variable-popup-value-input"),
                        decorationBox = { inner ->
                            // Use editValue.text (TextFieldValue) for empty check
                            if (editValue.text.isEmpty()) {
                                Text("Enter value…", color = ReqLabColors.OnSurfaceDim, fontSize = 13.sp)
                            }
                            inner()
                        },
                    )
                }

                // ─ Actions row ─
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Open in Environments",
                        fontSize = 11.sp,
                        color = ReqLabColors.Primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                val idx = state.environments.indexOf(env)
                                if (idx >= 0) state.openEnvEdit(idx)
                                onDismiss()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("variable-popup-open-env"),
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ReqLabColors.Primary)
                            .clickable {
                                // Issue 7: directly mutate reactive env state so all
                                // observers (URL preview, params table) refresh immediately.
                                if (envVar != null) {
                                    envVar.value = editValue.text
                                } else {
                                    env.variables.add(
                                        MutableKeyValue(key = variableName, value = editValue.text),
                                    )
                                }
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("variable-popup-save"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Save",
                            fontSize = 12.sp,
                            color = ReqLabColors.OnPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
