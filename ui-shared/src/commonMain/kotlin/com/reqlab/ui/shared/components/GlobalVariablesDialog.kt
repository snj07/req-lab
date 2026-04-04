package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.reqlab.ui.shared.i18n.Strings
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.MutableKeyValue
import com.reqlab.ui.shared.theme.CodeFontFamily
import com.reqlab.ui.shared.theme.ReqLabColors
import kotlin.math.roundToInt

/**
 * Global Variables Manager dialog.
 *
 * Global variables are available across all environments and all requests.
 * Resolution priority: Local script vars → Environment vars → Global vars.
 * If the same key exists in an environment, the environment value wins.
 */
@Composable
fun GlobalVariablesDialog(state: AppState) {
    if (!state.showGlobalVariablesDialog) return

    val workingVars = remember {
        mutableStateListOf<MutableKeyValue>().also { list ->
            state.globalVariables.forEach { v ->
                list.add(MutableKeyValue(v.key, v.value, v.enabled, v.secret))
            }
        }
    }

    fun cancelDialog() {
        state.showGlobalVariablesDialog = false
    }

    fun saveDialog() {
        state.globalVariables.clear()
        workingVars.forEach { v ->
            if (v.key.isNotBlank() || v.value.isNotBlank()) {
                state.globalVariables.add(MutableKeyValue(v.key, v.value, v.enabled, v.secret))
            }
        }
        state.pruneEmptyGlobalVariables()
        state.showGlobalVariablesDialog = false
    }

    Dialog(onDismissRequest = { cancelDialog() }) {
        var dialogSize by remember { mutableStateOf(IntSize.Zero) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { cancelDialog() }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 500.dp, max = 700.dp)
                    .fillMaxHeight(0.7f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ReqLabColors.Surface)
                    .border(1.dp, ReqLabColors.Border, RoundedCornerShape(12.dp))
                    .pointerInput(Unit) { detectTapGestures { /* consume tap */ } }
                    .onSizeChanged { dialogSize = it }
                    .testTag("global-variables-dialog"),
            ) {
                // ── Header ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ReqLabColors.SurfaceContainer)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Public,
                        contentDescription = null,
                        tint = ReqLabColors.Primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Strings.globalVariables,
                        color = ReqLabColors.OnSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { cancelDialog() },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Default.Close, Strings.close, tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(16.dp))
                    }
                }

                HorizontalDivider(color = ReqLabColors.Border)

                // ── Description ──
                Text(
                    Strings.globalVariablesDesc,
                    color = ReqLabColors.OnSurfaceDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )

                Text(
                    text = Strings.t("secret_value_hint"),
                    color = ReqLabColors.OnSurfaceDim,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                // ── Column headers ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ReqLabColors.SurfaceContainer)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(32.dp))
                    Text(Strings.t("key_upper"), color = ReqLabColors.OnSurfaceDim, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(Strings.t("value_upper"), color = ReqLabColors.OnSurfaceDim, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(Strings.t("type_upper"), color = ReqLabColors.OnSurfaceDim, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(72.dp))
                    Spacer(Modifier.width(52.dp))
                }

                // ── Variable list ──
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("global-variables-list"),
                ) {
                    itemsIndexed(
                        items = workingVars,
                        key = { _, item -> item.uid },
                    ) { index, variable ->
                        GlobalVariableRow(
                            variable = variable,
                            index = index,
                            onDelete = { workingVars.removeAt(index) },
                        )
                    }

                    if (workingVars.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    Strings.noGlobalVariables,
                                    color = ReqLabColors.OnSurfaceDim,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = ReqLabColors.Border)

                // ── Add button ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ReqLabColors.Primary.copy(alpha = 0.12f))
                            .clickable {
                                workingVars.add(MutableKeyValue())
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("add-global-variable"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Default.Add, null, tint = ReqLabColors.Primary, modifier = Modifier.size(14.dp))
                        Text(Strings.addVariable, color = ReqLabColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                HorizontalDivider(color = ReqLabColors.Border)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ReqLabColors.SurfaceContainer)
                            .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp))
                            .clickable { cancelDialog() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("global-vars-cancel"),
                    ) {
                        Text(Strings.cancel, color = ReqLabColors.OnSurface, fontSize = 13.sp)
                    }

                    Spacer(Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ReqLabColors.Primary)
                            .clickable { saveDialog() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("global-vars-save"),
                    ) {
                        Text(Strings.save, color = ReqLabColors.OnPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalVariableRow(
    variable: MutableKeyValue,
    index: Int,
    onDelete: () -> Unit,
) {
    var showValue by remember(variable.uid) { mutableStateOf(!variable.secret) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Enabled checkbox
        Checkbox(
            checked = variable.enabled,
            onCheckedChange = { variable.enabled = it },
            modifier = Modifier.size(28.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = ReqLabColors.Primary,
                uncheckedColor = ReqLabColors.OnSurfaceDim,
            ),
        )

        Spacer(Modifier.width(4.dp))

        // Key input
        BasicTextField(
            value = variable.key,
            onValueChange = { variable.key = it },
            singleLine = true,
            textStyle = TextStyle(
                color = ReqLabColors.OnSurface,
                fontSize = 12.sp,
                fontFamily = CodeFontFamily,
            ),
            cursorBrush = SolidColor(ReqLabColors.Primary),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(ReqLabColors.Background)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(4.dp))
                .testTag("global-var-key-$index")
                .padding(horizontal = 8.dp, vertical = 6.dp),
            decorationBox = { inner ->
                Box {
                    if (variable.key.isEmpty()) {
                        Text(Strings.variableName, color = ReqLabColors.OnSurfaceDim, fontSize = 12.sp, fontFamily = CodeFontFamily)
                    }
                    inner()
                }
            },
        )

        Spacer(Modifier.width(8.dp))

        // Value input
        BasicTextField(
            value = variable.value,
            onValueChange = { variable.value = it },
            singleLine = true,
            visualTransformation = if (variable.secret && !showValue) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = TextStyle(
                color = ReqLabColors.OnSurface,
                fontSize = 12.sp,
                fontFamily = CodeFontFamily,
            ),
            cursorBrush = SolidColor(ReqLabColors.Primary),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(ReqLabColors.Background)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(4.dp))
                .testTag("global-var-value-$index")
                .padding(horizontal = 8.dp, vertical = 6.dp),
            decorationBox = { inner ->
                Box {
                    if (variable.value.isEmpty()) {
                        Text(Strings.value, color = ReqLabColors.OnSurfaceDim, fontSize = 12.sp, fontFamily = CodeFontFamily)
                    }
                    inner()
                }
            },
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = if (variable.secret) Strings.t("secret") else Strings.t("normal"),
            color = if (variable.secret) ReqLabColors.Tertiary else ReqLabColors.OnSurfaceDim,
            fontSize = 10.sp,
            modifier = Modifier
                .width(72.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (variable.secret) ReqLabColors.Tertiary.copy(alpha = 0.10f) else ReqLabColors.SurfaceContainer)
                .clickable {
                    variable.secret = !variable.secret
                    if (variable.secret) showValue = false
                }
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .testTag("global-var-type-$index"),
        )

        Spacer(Modifier.width(4.dp))

        // Secret toggle
        if (variable.secret) {
            IconButton(
                onClick = { showValue = !showValue },
                modifier = Modifier.size(24.dp).testTag("global-var-eye-$index"),
            ) {
                Icon(
                    if (showValue) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showValue) Strings.t("hide_value") else Strings.t("show_value"),
                    tint = ReqLabColors.OnSurfaceDim,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            Spacer(Modifier.width(24.dp))
        }

        // Delete
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(24.dp).testTag("global-var-delete-$index"),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = Strings.t("delete_variable"),
                tint = ReqLabColors.Error,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
