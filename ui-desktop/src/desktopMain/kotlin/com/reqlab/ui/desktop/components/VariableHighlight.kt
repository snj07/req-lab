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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.reqlab.ui.desktop.state.AppState
import com.reqlab.ui.desktop.state.MutableKeyValue
import com.reqlab.ui.desktop.theme.CodeFontFamily
import com.reqlab.ui.desktop.theme.ReqLabColors

// ── Constants ─────────────────────────────────────────────────────

private val VARIABLE_REGEX = Regex("""\{\{([^}]+)}}""")
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
 * appearance. Variable names are trimmed of surrounding whitespace.
 */
internal fun parseVariableNames(text: String): List<String> =
    VARIABLE_REGEX.findAll(text).map { it.groupValues[1].trim() }.toList()

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
        addStringAnnotation(VARIABLE_TAG, match.groupValues[1].trim(), spanStart, length)
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) append(text.substring(lastIndex))
}

// ── VariableAwareTextField ────────────────────────────────────────────

/**
 * A drop-in replacement for a plain [BasicTextField] that:
 *
 *  1. **Colour-highlights** every `{{variable}}` token in orange.
 *  2. **Shows a floating popup** when the cursor lands inside a variable
 *     span, allowing the user to view and edit the variable's value in the
 *     active environment without leaving the editor.
 *
 * When [state] is `null` the behaviour degrades gracefully to a plain
 * text field with no highlighting or popup.
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
        mutableStateOf(TextFieldValue(if (state != null) highlightVariables(value) else AnnotatedString(value)))
    }

    // Name of the variable whose popup is currently visible (null = hidden).
    var popupVariable by remember { mutableStateOf<String?>(null) }

    // Sync when an external change overrides the displayed value
    // (e.g. URL is updated from the params table, or a prior-saved value is
    // loaded). Only rebuild highlighting when the text actually differs.
    LaunchedEffect(value) {
        if (fieldValue.text != value) {
            val annotated = if (state != null) highlightVariables(value) else AnnotatedString(value)
            fieldValue = fieldValue.copy(annotatedString = annotated)
        }
    }

    // Show / hide the popup based on whether the cursor is inside a variable span.
    LaunchedEffect(fieldValue.selection) {
        if (state == null) return@LaunchedEffect
        if (!fieldValue.selection.collapsed) {
            popupVariable = null
            return@LaunchedEffect
        }
        val cursor = fieldValue.selection.start
        // getStringAnnotations(tag, start, end) returns annotations whose range
        // overlaps [start, end].  Using (cursor, cursor) effectively means "at cursor".
        val found = fieldValue.annotatedString
            .getStringAnnotations(VARIABLE_TAG, cursor, cursor)
            .firstOrNull()
        popupVariable = found?.item
    }

    Box {
        BasicTextField(
            value = fieldValue,
            onValueChange = { new ->
                val annotated = if (state != null) highlightVariables(new.text) else AnnotatedString(new.text)
                fieldValue = TextFieldValue(
                    annotatedString = annotated,
                    selection = new.selection,
                    composition = new.composition,
                )
                onValueChange(new.text)
            },
            singleLine = singleLine,
            textStyle = textStyle,
            cursorBrush = SolidColor(ReqLabColors.Primary),
            modifier = modifier,
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

@Composable
private fun VariableEditorPopup(
    variableName: String,
    state: AppState,
    onDismiss: () -> Unit,
) {
    val env = state.selectedEnvironment
    val envVar = env.variables.firstOrNull { it.key == variableName }
    val resolved = env.toVariableMap()[variableName]
    var editValue by remember(variableName) { mutableStateOf(envVar?.value ?: "") }

    Popup(onDismissRequest = onDismiss) {
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

                // ─ Header: variable name + active environment badge ─
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                            if (editValue.isEmpty()) {
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
                                if (envVar != null) {
                                    envVar.value = editValue
                                } else {
                                    env.variables.add(
                                        MutableKeyValue(key = variableName, value = editValue),
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
