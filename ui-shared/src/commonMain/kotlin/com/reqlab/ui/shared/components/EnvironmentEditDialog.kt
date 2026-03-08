package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.EnvState
import com.reqlab.ui.shared.state.MutableKeyValue
import com.reqlab.ui.shared.theme.CodeFontFamily
import com.reqlab.ui.shared.theme.ReqLabColors

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

    Dialog(onDismissRequest = { state.showEnvEditDialog = false }) {
        Box(
            modifier = Modifier
                .widthIn(min = 600.dp, max = 900.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ReqLabColors.Surface)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(12.dp))
                .testTag("env-edit-dialog"),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // ── Title ────────────────────────────────────
                Text(
                    "Edit Environment",
                    style = MaterialTheme.typography.titleLarge,
                    color = ReqLabColors.OnSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(16.dp))

                // ── Environment Name ─────────────────────────
                Text("Name", style = MaterialTheme.typography.labelMedium, color = ReqLabColors.OnSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                EnvTextField(
                    value = workingName.value,
                    onValueChange = { workingName.value = it },
                    placeholder = "Environment name",
                    tag = "env-name-field",
                )
                Spacer(Modifier.height(20.dp))

                // ── Variables table header ────────────────────
                Text("Variables", style = MaterialTheme.typography.labelMedium, color = ReqLabColors.OnSurfaceVariant)
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(ReqLabColors.SurfaceHigh)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Spacer(Modifier.width(32.dp))
                    TableHeader("KEY",   Modifier.weight(1.0f))
                    TableHeader("VALUE", Modifier.weight(1.0f))
                    TableHeader("TYPE",  Modifier.width(90.dp))
                    Spacer(Modifier.width(36.dp))
                }

                // ── Variables rows ────────────────────────────
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .border(1.dp, ReqLabColors.Border, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                        .testTag("env-variables-list"),
                ) {
                    itemsIndexed(workingVars, key = { idx, _ -> idx }) { idx, kv ->
                        EnvVariableRow(kv, onDelete = { workingVars.removeAt(idx) })
                        Box(Modifier.fillMaxWidth().height(1.dp).background(ReqLabColors.Border))
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── Add Variable button ───────────────────────
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { workingVars.add(MutableKeyValue()) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = ReqLabColors.Primary, modifier = Modifier.size(16.dp))
                    Text("Add Variable", color = ReqLabColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                        Text("Cancel", color = ReqLabColors.OnSurface, fontSize = 13.sp)
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
                                    env.variables.add(MutableKeyValue(v.key, v.value, v.enabled, v.secret))
                                }
                                state.showEnvEditDialog = false
                                state.log("✓ Environment '${env.name}' saved (${env.variables.size} variables)", com.reqlab.ui.shared.state.LogLevel.SUCCESS)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("env-save-button"),
                    ) {
                        Text("Save", color = ReqLabColors.OnPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
private fun EnvVariableRow(kv: MutableKeyValue, onDelete: () -> Unit) {
    var showValue by remember { mutableStateOf(!kv.secret) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReqLabColors.SurfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
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
            placeholder = "variable_name",
            modifier = Modifier.weight(1f),
        )

        // Value field (masked if secret)
        EnvTextField(
            value = kv.value,
            onValueChange = { kv.value = it },
            placeholder = if (kv.secret) "••••••••" else "value",
            masked = kv.secret && !showValue,
            modifier = Modifier.weight(1f),
        )

        // Type toggle (Normal / Secret)
        Row(
            modifier = Modifier.width(90.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (kv.secret) "Secret" else "Normal",
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
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(14.dp))
        }
    }
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
    BasicTextField(
        value = if (masked) displayValue else value,
        onValueChange = if (masked) ({}) else onValueChange,
        singleLine = true,
        readOnly = masked,
        textStyle = TextStyle(color = ReqLabColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
        cursorBrush = SolidColor(ReqLabColors.Primary),
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ReqLabColors.SurfaceContainer)
            .border(1.dp, ReqLabColors.Border, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
        decorationBox = { inner ->
            if (value.isEmpty() && !masked) {
                Text(placeholder, color = ReqLabColors.OnSurfaceDim, fontSize = 13.sp)
            }
            inner()
        },
    )
}
