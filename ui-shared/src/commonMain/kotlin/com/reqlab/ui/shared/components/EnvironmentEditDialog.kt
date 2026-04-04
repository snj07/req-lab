package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.reqlab.ui.shared.i18n.Strings
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.EnvState
import com.reqlab.ui.shared.state.MutableKeyValue
import com.reqlab.ui.shared.theme.CodeFontFamily
import com.reqlab.ui.shared.theme.ReqLabColors
import kotlin.math.roundToInt

@Composable
fun EnvironmentEditDialog(state: AppState) {
    if (!state.showEnvEditDialog) return
    val envIndex = state.editingEnvIndex
    val env = state.environments.getOrNull(envIndex) ?: return

    // Local working copy – committed on Save, discarded on Cancel
    val workingName = remember(envIndex) { mutableStateOf(env.name) }
    val workingVars = remember(envIndex) {
        mutableStateListOf<MutableKeyValue>().also { list ->
            env.variables.forEach { v ->
                list.add(MutableKeyValue(v.key, v.value, v.enabled, v.secret))
            }
        }
    }

    // Drag state — Float accumulation prevents sub-pixel truncation.
    var envOffsetX by remember { mutableStateOf(0f) }
    var envOffsetY by remember { mutableStateOf(0f) }
    var envViewportSize by remember { mutableStateOf(IntSize.Zero) }
    var envCardSize by remember { mutableStateOf(IntSize.Zero) }

    Dialog(onDismissRequest = { state.showEnvEditDialog = false }) {
        // Full-screen transparent backdrop: centres the card and dismisses on
        // tap outside the card boundary.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { envViewportSize = it }
                .pointerInput(Unit) {
                    detectTapGestures { state.showEnvEditDialog = false }
                },
            contentAlignment = Alignment.Center,
        ) {
            // The Column doubles as the styled card so we avoid an extra layer
            // of nesting yet keep identical indentation for all content below.
            Column(
                modifier = Modifier
                    // Offset must come first so shadow/clip follow the moved position.
                    .offset { IntOffset(envOffsetX.roundToInt(), envOffsetY.roundToInt()) }
                    .widthIn(min = 600.dp, max = 900.dp)
                    .onSizeChanged { envCardSize = it }
                    .clip(RoundedCornerShape(12.dp))
                    .background(ReqLabColors.Surface)
                    .border(1.dp, ReqLabColors.Border, RoundedCornerShape(12.dp))
                    .draggableNoSlop { dx, dy ->
                        val rawX = envOffsetX + dx
                        val rawY = envOffsetY + dy
                        val (cx, cy) = clampDialogOffsetFromCenter(
                            offsetX = rawX,
                            offsetY = rawY,
                            cardSize = envCardSize,
                            viewportSize = envViewportSize
                        )
                        envOffsetX = cx
                        envOffsetY = cy
                    }
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(24.dp)
                    .testTag("env-edit-dialog"),
            ) {
                // ── Title with drag handle ────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()

                        .padding(bottom = 16.dp)
                        .testTag("env-dialog-title-bar"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Strings.t("edit_environment"),
                        style = MaterialTheme.typography.titleLarge,
                        color = ReqLabColors.OnSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                }

                // ── Environment Name ─────────────────────────
                Text(Strings.t("name"), style = MaterialTheme.typography.labelMedium, color = ReqLabColors.OnSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                EnvTextField(
                    value = workingName.value,
                    onValueChange = { workingName.value = it },
                    placeholder = Strings.t("environment_name"),
                    tag = "env-name-field",
                )
                Spacer(Modifier.height(20.dp))

                // ── Variables table header ────────────────────
                Text(Strings.variables, style = MaterialTheme.typography.labelMedium, color = ReqLabColors.OnSurfaceVariant)
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(ReqLabColors.SurfaceHigh)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Spacer(Modifier.width(32.dp))
                    TableHeader(Strings.t("key_upper"),   Modifier.weight(1.0f))
                    TableHeader(Strings.t("value_upper"), Modifier.weight(1.0f))
                    TableHeader(Strings.t("type_upper"),  Modifier.width(90.dp))
                    Spacer(Modifier.width(36.dp))
                }

                // ── Variables rows ────────────────────────────
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(ReqLabColors.Surface)
                        .border(1.dp, ReqLabColors.Border, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                        .testTag("env-variables-list"),
                ) {
                    // M-7: Key by stable uid instead of index so Compose correctly
                    // animates insertions/deletions without recomposing unaffected rows.
                    itemsIndexed(workingVars, key = { _, kv -> kv.uid }) { idx, kv ->
                        EnvVariableRow(
                            kv = kv,
                            index = idx,
                            onDelete = { workingVars.removeAt(idx) },
                        )
                        Box(Modifier.fillMaxWidth().height(1.dp).background(ReqLabColors.Border))
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── Add Variable button ───────────────────────
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { workingVars.add(MutableKeyValue()) }
                        .testTag("env-add-variable")
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = ReqLabColors.Primary, modifier = Modifier.size(16.dp))
                    Text(Strings.addVariable, color = ReqLabColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(Modifier.height(20.dp))

                // ── Action buttons ────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Cancel
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ReqLabColors.SurfaceContainer)
                            .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp))
                            .clickable { state.showEnvEditDialog = false }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("env-cancel-button"),
                    ) {
                        Text(Strings.cancel, color = ReqLabColors.OnSurface, fontSize = 13.sp)
                    }

                    Spacer(Modifier.width(8.dp))

                    // Save
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ReqLabColors.Primary)
                            .clickable {
                                // Commit changes back to the real environment state
                                env.name = workingName.value
                                env.variables.clear()
                                workingVars.forEach { v ->
                                    if (v.key.isNotBlank() || v.value.isNotBlank()) {
                                        env.variables.add(MutableKeyValue(v.key, v.value, v.enabled, v.secret))
                                    }
                                }
                                state.pruneEmptyVariablesForEnvironment(envIndex)
                                state.showEnvEditDialog = false
                                state.log("✓ Environment '${env.name}' saved (${env.variables.size} variables)", com.reqlab.ui.shared.state.LogLevel.SUCCESS)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("env-save-button"),
                    ) {
                        Text(Strings.save, color = ReqLabColors.OnPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ── Sub-components ──────────────────────────────────────────────

@Composable
private fun TableHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = ReqLabColors.OnSurfaceDim,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        modifier = modifier,
    )
}

@Composable
private fun EnvVariableRow(kv: MutableKeyValue, index: Int, onDelete: () -> Unit) {
    var showValue by remember { mutableStateOf(!kv.secret) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val baseRowColor = when (environmentRowTone(index, isHovered)) {
        EnvironmentRowTone.EVEN -> ReqLabColors.SurfaceVariant
        EnvironmentRowTone.ODD -> ReqLabColors.Surface
        EnvironmentRowTone.HOVERED -> ReqLabColors.Primary.copy(alpha = 0.08f)
    }
    val rowColor = baseRowColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowColor)
            .hoverable(interactionSource)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("env-var-row-$index"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Enabled checkbox
        Checkbox(
            checked = kv.enabled,
            onCheckedChange = { kv.enabled = it },
            modifier = Modifier.size(24.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = ReqLabColors.Primary,
                uncheckedColor = ReqLabColors.OnSurfaceDim,
            ),
        )

        // Key field
        EnvTextField(
            value = kv.key,
            onValueChange = { kv.key = it },
            placeholder = Strings.variableName,
            tag = "env-var-key-$index",
            modifier = Modifier.weight(1f),
        )

        // Value field (masked if secret)
        EnvTextField(
            value = kv.value,
            onValueChange = { kv.value = it },
            placeholder = if (kv.secret) "••••••••" else Strings.value,
            masked = kv.secret && !showValue,
            tag = "env-var-value-$index",
            modifier = Modifier.weight(1f),
        )

        // Type toggle (Normal / Secret)
        Row(
            modifier = Modifier.width(90.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (kv.secret) Strings.t("secret") else Strings.t("normal"),
                fontSize = 11.sp,
                color = if (kv.secret) ReqLabColors.Tertiary else ReqLabColors.OnSurfaceDim,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (kv.secret) ReqLabColors.Tertiary.copy(alpha = 0.1f) else Color.Transparent)
                    .clickable { kv.secret = !kv.secret; if (kv.secret) showValue = false }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }

        // Reveal / hide value for secrets
        if (kv.secret) {
            IconButton(onClick = { showValue = !showValue }, modifier = Modifier.size(24.dp)) {
                Icon(
                    if (showValue) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = ReqLabColors.OnSurfaceDim,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            Spacer(Modifier.width(24.dp))
        }

        // Delete
        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Delete, contentDescription = Strings.delete, tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(14.dp))
        }
    }
}

enum class EnvironmentRowTone {
    EVEN,
    ODD,
    HOVERED,
}

fun environmentRowTone(index: Int, isHovered: Boolean): EnvironmentRowTone {
    if (isHovered) return EnvironmentRowTone.HOVERED
    return if (index % 2 == 0) EnvironmentRowTone.EVEN else EnvironmentRowTone.ODD
}

@Composable
private fun EnvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    masked: Boolean = false,
    tag: String = "",
    modifier: Modifier = Modifier,
) {
    val displayValue = if (masked) "•".repeat(value.length.coerceAtMost(20)) else value
    var isFocused by remember { mutableStateOf(false) }
    BasicTextField(
        value = if (masked) displayValue else value,
        onValueChange = if (masked) ({}) else onValueChange,
        singleLine = true,
        readOnly = masked,
        textStyle = TextStyle(color = ReqLabColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
        cursorBrush = SolidColor(ReqLabColors.Primary),
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isFocused) ReqLabColors.Surface else ReqLabColors.SurfaceContainer)
            .border(1.dp, if (isFocused) ReqLabColors.Primary else ReqLabColors.Border, RoundedCornerShape(4.dp))
            .onFocusChanged { focusState -> isFocused = focusState.isFocused }
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
        decorationBox = { inner ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (value.isEmpty() && !masked) {
                    Text(placeholder, color = ReqLabColors.OnSurfaceDim, fontSize = 13.sp)
                }
                inner()
                if (isFocused && tag.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(1.dp)
                            .align(Alignment.TopStart)
                            .testTag("$tag-focused"),
                    )
                }
            }
        },
    )
}
