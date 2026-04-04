package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.ui.shared.i18n.Strings
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.HeaderKind
import com.reqlab.ui.shared.state.MutableKeyValue
import com.reqlab.ui.shared.state.SystemHeaderRules
import com.reqlab.ui.shared.theme.CodeFontFamily
import com.reqlab.ui.shared.theme.ReqLabColors

/**
 * Generic key-value table editor used for both Params and Headers.
 *
 * @param entries the mutable list of entries (backed by Compose snapshot state)
 * @param tag     semantic label used for test-tags and "Add …" button text
 * @param onDirty callback invoked whenever any entry changes
 */
@Composable
fun KeyValueEditor(
    entries: MutableList<MutableKeyValue>,
    tag: String,
    state: AppState? = null,
    onDirty: () -> Unit,
) {
    val isHeaderEditor = tag == "header"
    Column(modifier = Modifier.fillMaxSize().background(ReqLabColors.Background).padding(8.dp)) {
        // Column header labels
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            androidx.compose.foundation.layout.Spacer(Modifier.size(32.dp))
            Text(Strings.t("key_upper"),   style = MaterialTheme.typography.labelSmall, color = ReqLabColors.OnSurfaceDim, modifier = Modifier.weight(1f))
            Text(Strings.t("value_upper"), style = MaterialTheme.typography.labelSmall, color = ReqLabColors.OnSurfaceDim, modifier = Modifier.weight(1f))
            if (isHeaderEditor) {
                Text(Strings.t("type_upper"), style = MaterialTheme.typography.labelSmall, color = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(70.dp, 20.dp))
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(32.dp))
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(entries, key = { idx, _ -> idx }) { idx, kv ->
                KeyValueRow(
                    kv = kv,
                    onDelete = {
                        if (!(isHeaderEditor && kv.kind == HeaderKind.SYSTEM)) {
                            entries.removeAt(idx)
                            onDirty()
                        }
                    },
                    onDirty = onDirty,
                    isHeaderEditor = isHeaderEditor,
                    state = state,
                    testTag = "$tag-row-$idx",
                )
            }
        }

        // "Add …" button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                    entries.add(
                        if (isHeaderEditor) MutableKeyValue(kind = HeaderKind.USER)
                        else MutableKeyValue()
                    )
                    onDirty()
                }
                .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = Strings.add, tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(16.dp))
            Text("${Strings.add} ${tag.replaceFirstChar { it.uppercaseChar() }}", color = ReqLabColors.OnSurfaceDim, fontSize = 12.sp)
        }
    }
}

// ── Key-value row ───────────────────────────────────────────────

@Composable
private fun KeyValueRow(
    kv: MutableKeyValue,
    onDelete: () -> Unit,
    onDirty: () -> Unit,
    isHeaderEditor: Boolean,
    state: AppState? = null,
    testTag: String,
) {
    val isSystemHeader = isHeaderEditor &&
        (kv.kind == HeaderKind.SYSTEM || SystemHeaderRules.isSystemHeader(kv.key))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(
            checked = kv.enabled,
            onCheckedChange = { kv.enabled = it; onDirty() },
            modifier = Modifier.size(24.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = ReqLabColors.Primary,
                uncheckedColor = ReqLabColors.OnSurfaceDim,
                checkmarkColor = ReqLabColors.OnPrimary,
            ),
        )

        InlineTextField(
            value = kv.key,
            onValueChange = { if (!isSystemHeader) { kv.key = it; onDirty() } },
            placeholder = Strings.t("key_upper"),
            modifier = Modifier.weight(1f),
            state = state,
        )
        InlineTextField(
            value = kv.value,
            onValueChange = { kv.value = it; onDirty() },
            placeholder = Strings.t("value_upper"),
            modifier = Modifier.weight(1f),
            state = state,
        )

        if (isHeaderEditor) {
            Row(
                modifier = Modifier.size(70.dp, 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isSystemHeader) {
                    Icon(Icons.Default.Lock, contentDescription = Strings.t("system_header"), tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(12.dp))
                    Text(Strings.t("system"), fontSize = 11.sp, color = ReqLabColors.OnSurfaceDim)
                } else {
                    Text(Strings.t("user"), fontSize = 11.sp, color = ReqLabColors.OnSurfaceDim)
                }
            }
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp), enabled = !isSystemHeader) {
            Icon(Icons.Default.Delete, contentDescription = Strings.t("remove"), tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(14.dp))
        }
    }
}

// ── Shared inline text field ────────────────────────────────────

@Composable
fun InlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    state: AppState? = null,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ReqLabColors.SurfaceContainer)
            .border(1.dp, ReqLabColors.Border, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (state != null) {
            VariableAwareTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder,
                textStyle = TextStyle(color = ReqLabColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
                state = state,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = ReqLabColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
                cursorBrush = SolidColor(ReqLabColors.Primary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(placeholder, color = ReqLabColors.OnSurfaceDim, fontSize = 13.sp)
                    inner()
                },
            )
        }
    }
}
