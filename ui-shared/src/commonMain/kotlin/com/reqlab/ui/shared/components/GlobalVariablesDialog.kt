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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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

    Dialog(onDismissRequest = { state.showGlobalVariablesDialog = false }) {
        var dialogSize by remember { mutableStateOf(IntSize.Zero) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { state.showGlobalVariablesDialog = false }
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
                        "Global Variables",
                        color = ReqLabColors.OnSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { state.showGlobalVariablesDialog = false },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Default.Close, "Close", tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(16.dp))
                    }
                }

                HorizontalDivider(color = ReqLabColors.Border)

                // ── Description ──
                Text(
                    "Global variables are available in all environments and requests. " +
                            "Use {{variableName}} syntax. Environment variables override globals.",
                    color = ReqLabColors.OnSurfaceDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )

                // ── Column headers ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ReqLabColors.SurfaceContainer)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("", modifier = Modifier.width(32.dp))
                    Text("Key", color = ReqLabColors.OnSurfaceDim, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.4f))
                    Text("Value", color = ReqLabColors.OnSurfaceDim, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.5f))
                    Text("", modifier = Modifier.width(64.dp))
                }

                // ── Variable list ──
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("global-variables-list"),
                ) {
                    itemsIndexed(
                        items = state.globalVariables,
                        key = { _, item -> item.uid },
                    ) { index, variable ->
                        GlobalVariableRow(
                            variable = variable,
                            onDelete = { state.globalVariables.removeAt(index) },
                        )
                    }

                    if (state.globalVariables.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "No global variables defined",
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
                                state.globalVariables.add(MutableKeyValue())
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("add-global-variable"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Default.Add, null, tint = ReqLabColors.Primary, modifier = Modifier.size(14.dp))
                        Text("Add Variable", color = ReqLabColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalVariableRow(
    variable: MutableKeyValue,
    onDelete: () -> Unit,
) {
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
                .weight(0.4f)
                .clip(RoundedCornerShape(4.dp))
                .background(ReqLabColors.Background)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            decorationBox = { inner ->
                Box {
                    if (variable.key.isEmpty()) {
                        Text("Variable name", color = ReqLabColors.OnSurfaceDim, fontSize = 12.sp, fontFamily = CodeFontFamily)
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
            textStyle = TextStyle(
                color = ReqLabColors.OnSurface,
                fontSize = 12.sp,
                fontFamily = CodeFontFamily,
            ),
            cursorBrush = SolidColor(ReqLabColors.Primary),
            modifier = Modifier
                .weight(0.5f)
                .clip(RoundedCornerShape(4.dp))
                .background(ReqLabColors.Background)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            decorationBox = { inner ->
                Box {
                    if (variable.value.isEmpty()) {
                        Text("Value", color = ReqLabColors.OnSurfaceDim, fontSize = 12.sp, fontFamily = CodeFontFamily)
                    }
                    inner()
                }
            },
        )

        Spacer(Modifier.width(4.dp))

        // Secret toggle
        IconButton(
            onClick = { variable.secret = !variable.secret },
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                if (variable.secret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (variable.secret) "Show value" else "Hide value",
                tint = ReqLabColors.OnSurfaceDim,
                modifier = Modifier.size(14.dp),
            )
        }

        // Delete
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete variable",
                tint = ReqLabColors.Error,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
