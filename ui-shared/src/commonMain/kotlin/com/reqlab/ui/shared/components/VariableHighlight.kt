package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.reqlab.ui.shared.i18n.Strings
import com.reqlab.ui.shared.state.AppState
import kotlin.math.roundToInt
import com.reqlab.ui.shared.state.MutableKeyValue
import com.reqlab.ui.shared.theme.CodeFontFamily
import com.reqlab.ui.shared.theme.ReqLabColors

// ── Constants ─────────────────────────────────────────────────────

/**
 * Variable token pattern used across URL/body/header editors.
 *
 * Postman exports and real-world projects frequently use names like
 * `{{base-url}}`, `{{api.version}}`, or `{{ my_var }}`. We accept any
 * non-brace content inside `{{...}}` and normalize surrounding whitespace.
 */
private val VARIABLE_REGEX = Regex("""\{\{\s*([^{}\r\n]+?)\s*\}\}""")
private const val VARIABLE_TAG = "variable"
private fun normalizeVariableDisplayName(rawName: String): String =
    rawName.trim().trim('{', '}').trim()


/** Orange token colour — bright amber so it clearly stands out against both dark and light editor backgrounds. */
internal val VariableHighlightColor = Color(0xFFF97316)

/** Red token colour — shown when the variable has no value in the active environment. */
internal val VariableUnresolvedColor = Color(0xFFEF4444)

private val VARIABLE_SPAN_STYLE = SpanStyle(
    color = VariableHighlightColor,
    fontWeight = FontWeight.SemiBold,
    background = VariableHighlightColor.copy(alpha = 0.15f),
)

private val VARIABLE_UNRESOLVED_SPAN_STYLE = SpanStyle(
    color = VariableUnresolvedColor,
    fontWeight = FontWeight.SemiBold,
    background = VariableUnresolvedColor.copy(alpha = 0.12f),
)

// ── Public utilities (also used in unit tests) ─────────────────────

/** Returns every `{{name}}` variable name found in [text], in order. */
fun parseVariableNames(text: String): List<String> =
    VARIABLE_REGEX.findAll(text)
        .map { normalizeVariableDisplayName(it.groupValues[1]) }
        .filter { it.isNotEmpty() }
        .toList()

/** Returns the variable name under [offset], if the cursor is inside `{{...}}`. */
fun variableNameAtOffset(text: String, offset: Int): String? {
    if (offset < 0 || offset > text.length) return null
    for (match in VARIABLE_REGEX.findAll(text)) {
        val start = match.range.first
        val endExclusive = match.range.last + 1
        if (offset in start..endExclusive) {
            val name = normalizeVariableDisplayName(match.groupValues[1])
            if (name.isNotEmpty()) return name
        }
    }
    return null
}

/**
 * Returns per-character-range color spans for every `{{...}}` token in [line].
 *
 * Tokens whose name is present in [definedNames] use [VariableHighlightColor] (orange).
 * Tokens that are not defined anywhere use [VariableUnresolvedColor] (red), so the user
 * can immediately see which variables are missing from the active environment.
 */
fun variableRangesForLine(
    line: String,
    definedNames: Set<String>,
): List<Pair<IntRange, Color>> =
    VARIABLE_REGEX.findAll(line).mapNotNull { match ->
        val name = normalizeVariableDisplayName(match.groupValues[1])
        if (name.isEmpty()) return@mapNotNull null
        val color = if (name in definedNames) VariableHighlightColor else VariableUnresolvedColor
        match.range to color
    }.toList()

/**
 * Builds an [AnnotatedString] that colour-highlights every `{{variable}}`
 * token with [VARIABLE_SPAN_STYLE] (orange) and attaches a VARIABLE_TAG string
 * annotation containing the variable name for click/cursor detection.
 *
 * All tokens are treated as resolved. Use [highlightVariablesWithStatus] when
 * you have the set of defined variable names available.
 */
fun highlightVariables(text: String): AnnotatedString = buildAnnotatedString {
    var lastIndex = 0
    for (match in VARIABLE_REGEX.findAll(text)) {
        append(text.substring(lastIndex, match.range.first))
        val spanStart = length
        withStyle(VARIABLE_SPAN_STYLE) { append(match.value) }
        val variableName = normalizeVariableDisplayName(match.groupValues[1])
        if (variableName.isNotEmpty()) {
            addStringAnnotation(VARIABLE_TAG, variableName, spanStart, length)
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) append(text.substring(lastIndex))
}

/**
 * Builds an [AnnotatedString] that colour-highlights every `{{variable}}` token
 * using **two colours** depending on resolution status:
 *  - **Orange** ([VARIABLE_SPAN_STYLE]) — the name appears in [definedNames] (resolved)
 *  - **Red** ([VARIABLE_UNRESOLVED_SPAN_STYLE]) — the name is absent from [definedNames]
 *
 * Also attaches VARIABLE_TAG string annotations so click-to-edit works the same way.
 */
fun highlightVariablesWithStatus(text: String, definedNames: Set<String>): AnnotatedString =
    buildAnnotatedString {
        var lastIndex = 0
        for (match in VARIABLE_REGEX.findAll(text)) {
            append(text.substring(lastIndex, match.range.first))
            val spanStart = length
            val varName = normalizeVariableDisplayName(match.groupValues[1])
            val spanStyle = if (varName.isNotEmpty() && varName in definedNames)
                VARIABLE_SPAN_STYLE
            else
                VARIABLE_UNRESOLVED_SPAN_STYLE
            withStyle(spanStyle) { append(match.value) }
            if (varName.isNotEmpty()) {
                addStringAnnotation(VARIABLE_TAG, varName, spanStart, length)
            }
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
fun VariableAwareTextField(
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
    // Resolved variable names from whatever environment/collection layers are active.
    // Reading mutableState properties of AppState inside the composable body automatically
    // registers this composable for recomposition when the environment changes.
    val definedNames: Set<String> = if (state != null)
        state.activeVariableLayers().flatMap { it.keys }.toSet()
    else
        emptySet()

    // Internal TextFieldValue that carries the highlighted AnnotatedString.
    var fieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                if (state != null) highlightVariablesWithStatus(value, definedNames)
                else AnnotatedString(value),
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

    // Sync when the external text value changes (e.g. params table updates the URL).
    LaunchedEffect(value) {
        if (fieldValue.text != value) {
            val annotated = if (state != null) highlightVariablesWithStatus(value, definedNames)
                            else AnnotatedString(value)
            fieldValue = fieldValue.copy(annotatedString = annotated)
        }
    }

    // Re-colour when the active environment/variables change without a text change
    // (e.g. user activates a different env or adds a new variable).
    LaunchedEffect(definedNames) {
        if (state != null) {
            val annotated = highlightVariablesWithStatus(fieldValue.text, definedNames)
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
                val annotated = if (state != null) highlightVariablesWithStatus(new.text, definedNames)
                                else AnnotatedString(new.text)
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
fun VariableEditorPopup(
    variableName: String,
    state: AppState,
    onDismiss: () -> Unit,
    initialOffset: IntOffset = IntOffset(40, 32),
    onPositionChanged: (IntOffset) -> Unit = {},
) {
    val cleanName = remember(variableName) { normalizeVariableDisplayName(variableName) }
    val env = state.selectedEnvironment
    val envVar = remember(cleanName, env) { env?.variables?.firstOrNull { it.key == cleanName } }
    val resolved = env?.toVariableMap()?.get(cleanName)

    var editValue by remember(cleanName) { mutableStateOf(TextFieldValue(envVar?.value ?: "")) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var popupSize by remember { mutableStateOf(IntSize.Zero) }
    // Use Float state to avoid the sub-pixel truncation bug where small drag
    // deltas (e.g. 0.7f) become 0 after .toInt() and the popup never moves.
    var popupOffsetX by remember(cleanName) { mutableStateOf(initialOffset.x.toFloat()) }
    var popupOffsetY by remember(cleanName) { mutableStateOf(initialOffset.y.toFloat()) }

    fun movePopupBy(dx: Float, dy: Float) {
        // Accumulate in Float — never truncate to Int until layout time.
        val rawX = popupOffsetX + dx
        val rawY = popupOffsetY + dy
        val (cx, cy) = clampPopupOffsetToViewportF(
            x = rawX,
            y = rawY,
            popupSize = popupSize,
            viewportSize = viewportSize,
        )
        popupOffsetX = cx
        popupOffsetY = cy
        onPositionChanged(IntOffset(cx.roundToInt(), cy.roundToInt()))
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .onSizeChanged { size ->
                    viewportSize = size
                    val (cx, cy) = clampPopupOffsetToViewportF(
                        x = popupOffsetX, y = popupOffsetY,
                        popupSize = popupSize, viewportSize = size,
                    )
                    popupOffsetX = cx
                    popupOffsetY = cy
                }
                // Use detectTapGestures instead of clickable so that drag
                // gestures initiated on the popup card do not prematurely
                // trigger a backdrop-dismiss before touch-slop is exceeded.
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                .testTag("variable-popup-backdrop"),
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(popupOffsetX.roundToInt(), popupOffsetY.roundToInt()) }
                    .widthIn(min = 340.dp, max = 520.dp)
                    .shadow(16.dp, RoundedCornerShape(12.dp), clip = false)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ReqLabColors.Surface)
                    .border(1.dp, ReqLabColors.Border, RoundedCornerShape(12.dp))
                    .onSizeChanged { size ->
                        popupSize = size
                        val (cx, cy) = clampPopupOffsetToViewportF(
                            x = popupOffsetX, y = popupOffsetY,
                            popupSize = size, viewportSize = viewportSize,
                        )
                        popupOffsetX = cx
                        popupOffsetY = cy
                    }
                    // Consume every Main-pass event that was not already consumed
                    // by a child to become a drag on the popup.
                    .draggableNoSlop(key = cleanName) { dx, dy -> movePopupBy(dx, dy) }
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(16.dp)
                    .testTag("variable-editor-popup"),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ReqLabColors.SurfaceContainer)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("variable-popup-title-bar"),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${Strings.t("variable")}: $cleanName",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VariableHighlightColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${Strings.environments}: ${env?.name ?: Strings.t("none")}",
                                fontSize = 11.sp,
                                color = ReqLabColors.OnSurfaceDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(24.dp).testTag("variable-popup-close"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = Strings.t("close_variable_editor"),
                                tint = ReqLabColors.OnSurfaceDim,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ReqLabColors.SurfaceVariant)
                            .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = Strings.t("current_value"),
                            fontSize = 11.sp,
                            color = ReqLabColors.OnSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = resolved ?: Strings.t("not_defined"),
                            fontSize = 12.sp,
                            color = if (resolved == null) ReqLabColors.Error else ReqLabColors.OnSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("variable-popup-current-value"),
                        )
                    }

                    HorizontalDivider(color = ReqLabColors.Border)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = Strings.value,
                            fontSize = 11.sp,
                            color = ReqLabColors.OnSurfaceVariant,
                            fontWeight = FontWeight.Medium,
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
                                .clip(RoundedCornerShape(8.dp))
                                .background(ReqLabColors.SurfaceContainer)
                                .border(1.dp, ReqLabColors.BorderLight, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("variable-popup-value-input"),
                            decorationBox = { inner ->
                                if (editValue.text.isEmpty()) {
                                    Text(Strings.t("enter_value"), color = ReqLabColors.OnSurfaceDim, fontSize = 13.sp)
                                }
                                inner()
                            },
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = Strings.t("open_in_environments"),
                            fontSize = 12.sp,
                            color = ReqLabColors.Primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ReqLabColors.Primary.copy(alpha = 0.10f))
                                .clickable {
                                    val idx = env?.let { state.environments.indexOf(it) } ?: -1
                                    if (idx >= 0) state.openEnvEdit(idx)
                                    onDismiss()
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("variable-popup-open-env"),
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ReqLabColors.SurfaceContainer)
                                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp))
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("variable-popup-cancel"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Cancel",
                                fontSize = 12.sp,
                                color = ReqLabColors.OnSurface,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ReqLabColors.Primary)
                                .clickable {
                                    if (env == null) {
                                        onDismiss()
                                    } else if (envVar != null) {
                                        envVar.value = editValue.text
                                    } else {
                                        env.variables.add(MutableKeyValue(key = cleanName, value = editValue.text))
                                    }
                                    onDismiss()
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
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
}
