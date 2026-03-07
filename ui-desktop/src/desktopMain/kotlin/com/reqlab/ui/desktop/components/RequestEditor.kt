package com.reqlab.ui.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.core.model.AuthType
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.ui.desktop.state.AppState
import com.reqlab.ui.desktop.state.HeaderKind
import com.reqlab.ui.desktop.state.MutableKeyValue
import com.reqlab.ui.desktop.state.RequestEditorTab
import com.reqlab.ui.desktop.state.RequestTabState
import com.reqlab.ui.desktop.state.SystemHeaderRules
import com.reqlab.ui.desktop.theme.CodeFontFamily
import com.reqlab.ui.desktop.theme.ReqLabColors
import com.reqlab.ui.desktop.theme.httpMethodColor
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun RequestEditor(tab: RequestTabState, state: AppState, onSend: () -> Unit, onSave: () -> Unit) {
    val markDirty = { tab.markDirty() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReqLabColors.Background)
            .testTag("request-editor"),
    ) {
        // ── Request Bar ─────────────────────────────────────────
        RequestBar(
            method = tab.method,
            onMethodChanged = { tab.method = it; markDirty() },
            url = tab.url,
            onUrlChanged = { newUrl ->
                tab.url = newUrl
                syncParamsFromUrl(tab, newUrl)
                markDirty()
            },
            isLoading = tab.isLoading,
            onSend = onSend,
            onSave = onSave,
            onCopyCurl = { copyToClipboard(buildCurlCommand(tab)) },
            retryCount = tab.retryCount,
            retryDelayMs = tab.retryDelayMs,
            onRetryCountChanged = {
                tab.retryCount = it
                markDirty()
            },
            onRetryDelayChanged = {
                tab.retryDelayMs = it
                markDirty()
            },
        )

        // ── Editor Tabs ─────────────────────────────────────────
        EditorTabBar(
            selectedTab = tab.selectedEditorTab,
            onTabSelected = { tab.selectedEditorTab = it },
            paramCount = tab.params.size,
            headerCount = tab.headers.size,
        )

        // ── Tab Content ─────────────────────────────────────────
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(1.dp)) {
            when (tab.selectedEditorTab) {
                RequestEditorTab.PARAMS      -> KeyValueEditor(tab.params, "param") {
                    syncUrlFromParams(tab)
                    markDirty()
                }
                RequestEditorTab.HEADERS     -> KeyValueEditor(tab.headers, "header") { markDirty() }
                RequestEditorTab.BODY        -> BodyEditor(tab) { markDirty() }
                RequestEditorTab.AUTH        -> AuthEditor(tab) { markDirty() }
                RequestEditorTab.PRE_REQUEST -> ScriptEditor(tab.preRequestScript, { tab.preRequestScript = it; markDirty() }, "Pre-request Script")
                RequestEditorTab.TESTS       -> ScriptEditor(tab.testScript, { tab.testScript = it; markDirty() }, "Tests")
            }
        }
    }
}

// ── Request Bar ─────────────────────────────────────────────────

@Composable
private fun RequestBar(
    method: HttpMethodType,
    onMethodChanged: (HttpMethodType) -> Unit,
    url: String,
    onUrlChanged: (String) -> Unit,
    isLoading: Boolean,
    onSend: () -> Unit,
    onSave: () -> Unit,
    onCopyCurl: () -> Unit,
    retryCount: Int,
    retryDelayMs: Long,
    onRetryCountChanged: (Int) -> Unit,
    onRetryDelayChanged: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReqLabColors.Surface)
            .padding(8.dp)
            .testTag("request-bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Method dropdown
        MethodDropdown(method, onMethodChanged)

        // URL field
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(ReqLabColors.SurfaceContainer)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BasicTextField(
                value = url,
                onValueChange = onUrlChanged,
                singleLine = true,
                textStyle = TextStyle(color = ReqLabColors.OnSurface, fontSize = 14.sp, fontFamily = CodeFontFamily),
                cursorBrush = SolidColor(ReqLabColors.Primary),
                modifier = Modifier.fillMaxWidth().testTag("url-input"),
                decorationBox = { inner ->
                    if (url.isEmpty()) {
                        Text("Enter request URL…", color = ReqLabColors.OnSurfaceDim, fontSize = 14.sp)
                    }
                    inner()
                },
            )
        }

        // Send button
        SendButton(isLoading, onSend)

        SaveButton(isLoading = isLoading, onClick = onSave)

        RetryControlsButton(
            retryCount = retryCount,
            retryDelayMs = retryDelayMs,
            isLoading = isLoading,
            onRetryCountChanged = onRetryCountChanged,
            onRetryDelayChanged = onRetryDelayChanged,
        )

        CopyCurlButton(isLoading = isLoading, onClick = onCopyCurl)
    }
}

@Composable
private fun MethodDropdown(method: HttpMethodType, onMethodChanged: (HttpMethodType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val color = httpMethodColor(method)

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.12f))
                .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("method-dropdown"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(method.name, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            HttpMethodType.entries.forEach { m ->
                DropdownMenuItem(
                    text = {
                        Text(m.name, color = httpMethodColor(m), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    },
                    onClick = { onMethodChanged(m); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun SendButton(isLoading: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isHovered) ReqLabColors.Primary.copy(alpha = 0.9f) else ReqLabColors.Primary)
            .hoverable(interactionSource)
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("send-button"),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = ReqLabColors.OnPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Send", color = ReqLabColors.OnPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = ReqLabColors.OnPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SaveButton(isLoading: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isHovered) ReqLabColors.SurfaceHigh else ReqLabColors.SurfaceContainer)
            .hoverable(interactionSource)
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("save-button"),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Save", color = ReqLabColors.OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Icon(Icons.Default.Save, contentDescription = null, tint = ReqLabColors.OnSurface, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun CopyCurlButton(isLoading: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isHovered) ReqLabColors.SurfaceHigh else ReqLabColors.SurfaceContainer)
            .hoverable(interactionSource)
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .testTag("copy-curl-button"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.ContentCopy, contentDescription = "Copy cURL", tint = ReqLabColors.OnSurface, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun RetryControlsButton(
    retryCount: Int,
    retryDelayMs: Long,
    isLoading: Boolean,
    onRetryCountChanged: (Int) -> Unit,
    onRetryDelayChanged: (Long) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isHovered) ReqLabColors.SurfaceHigh else ReqLabColors.SurfaceContainer)
                .hoverable(interactionSource)
                .clickable(enabled = !isLoading) { expanded = true }
                .padding(horizontal = 7.dp, vertical = 8.dp)
                .testTag("retry-menu-button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Retry ($retryCount)",
                tint = ReqLabColors.OnSurface,
                modifier = Modifier.size(15.dp),
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(1, 2, 3, 5).forEach { attempts ->
                DropdownMenuItem(
                    text = { Text("Attempts: $attempts") },
                    onClick = {
                        onRetryCountChanged(attempts)
                        expanded = false
                    },
                )
            }
            listOf(100L, 250L, 500L, 1000L).forEach { delay ->
                DropdownMenuItem(
                    text = { Text("Delay: ${delay}ms") },
                    onClick = {
                        onRetryDelayChanged(delay)
                        expanded = false
                    },
                )
            }
        }
    }
}

// ── URL ↔ Params two-way sync ───────────────────────────────────

/**
 * Parses query parameters out of [url] and replaces the tab's params list.
 * Called whenever the user edits the URL field directly.
 * Plain-list params are NOT URL-decoded here to keep it simple & predictable.
 */
internal fun syncParamsFromUrl(tab: RequestTabState, url: String) {
    val qIdx = url.indexOf('?')
    val newParams: List<MutableKeyValue> = if (qIdx < 0 || qIdx == url.lastIndex) {
        emptyList()
    } else {
        url.substring(qIdx + 1).split('&').mapNotNull { pair ->
            if (pair.isBlank()) return@mapNotNull null
            val eqIdx = pair.indexOf('=')
            when {
                eqIdx < 0  -> MutableKeyValue(key = pair,                    value = "")
                eqIdx == 0 -> MutableKeyValue(key = "",                      value = pair.substring(1))
                else       -> MutableKeyValue(key = pair.substring(0, eqIdx), value = pair.substring(eqIdx + 1))
            }
        }
    }
    tab.params.clear()
    tab.params.addAll(newParams)
}

/**
 * Rebuilds the URL's query string from the current params table.
 * Called whenever a param key, value, or enabled-state changes.
 */
internal fun syncUrlFromParams(tab: RequestTabState) {
    val base = tab.url.substringBefore('?')
    val enabled = tab.params.filter { it.enabled && it.key.isNotBlank() }
    tab.url = if (enabled.isEmpty()) base
              else enabled.joinToString(separator = "&", prefix = "$base?") { "${it.key}=${it.value}" }
}

private fun buildCurlCommand(tab: RequestTabState): String {
    val parts = mutableListOf("curl")
    parts += "-X ${tab.method.name}"

    tab.headers
        .filter { it.enabled && it.key.isNotBlank() }
        .forEach { header ->
            parts += "-H ${shellQuote("${header.key}: ${header.value}")}"
        }

    when (tab.authType) {
        AuthType.BEARER -> {
            val token = tab.authToken.trim()
            if (token.isNotEmpty()) parts += "-H ${shellQuote("Authorization: Bearer $token")}"
        }
        AuthType.BASIC -> {
            if (tab.authUsername.isNotBlank() || tab.authPassword.isNotBlank()) {
                parts += "-u ${shellQuote("${tab.authUsername}:${tab.authPassword}")}"
            }
        }
        AuthType.API_KEY -> {
            if (tab.authApiKey.isNotBlank() && tab.authApiValue.isNotBlank()) {
                parts += "-H ${shellQuote("${tab.authApiKey}: ${tab.authApiValue}")}"
            }
        }
        AuthType.JWT -> {
            val token = tab.authToken.trim()
            if (token.isNotEmpty()) parts += "-H ${shellQuote("Authorization: Bearer $token")}"
        }
        AuthType.OAUTH2, AuthType.NONE -> Unit
    }

    if (tab.bodyType != BodyType.NONE && tab.bodyContent.isNotBlank()) {
        parts += "--data ${shellQuote(tab.bodyContent)}"
    }

    parts += shellQuote(tab.url)
    return parts.joinToString(" ")
}

private fun shellQuote(value: String): String {
    return "'" + value.replace("'", "'\\''") + "'"
}

private fun copyToClipboard(text: String) {
    runCatching {
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    }
}

// ── Editor Tab Bar ──────────────────────────────────────────────

@Composable
private fun EditorTabBar(
    selectedTab: RequestEditorTab,
    onTabSelected: (RequestEditorTab) -> Unit,
    paramCount: Int,
    headerCount: Int,
) {
    val icons = mapOf(
        RequestEditorTab.PARAMS      to null,
        RequestEditorTab.HEADERS     to null,
        RequestEditorTab.BODY        to Icons.Default.Code,
        RequestEditorTab.AUTH        to Icons.Default.Lock,
        RequestEditorTab.PRE_REQUEST to Icons.Default.PlayArrow,
        RequestEditorTab.TESTS       to Icons.Default.CheckCircle,
    )
    val counts = mapOf(
        RequestEditorTab.PARAMS  to paramCount,
        RequestEditorTab.HEADERS to headerCount,
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReqLabColors.Surface)
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color.Transparent,
            contentColor = ReqLabColors.OnSurface,
            edgePadding = 0.dp,
            divider = {},
            indicator = {},
        ) {
            RequestEditorTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Tab(
                    selected = selected,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.height(36.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 12.dp),
                    ) {
                        icons[tab]?.let { icon ->
                            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp),
                                tint = if (selected) ReqLabColors.Primary else ReqLabColors.OnSurfaceDim)
                        }
                        Text(
                            tab.label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) ReqLabColors.Primary else ReqLabColors.OnSurfaceVariant,
                        )
                        counts[tab]?.takeIf { it > 0 }?.let { count ->
                            Text(
                                "($count)",
                                fontSize = 10.sp,
                                color = ReqLabColors.OnSurfaceDim,
                            )
                        }
                    }
                }
            }
        }

        // bottom line
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(ReqLabColors.Border)
        )
    }
}

// ── Key-Value Editor ────────────────────────────────────────────

@Composable
fun KeyValueEditor(entries: MutableList<MutableKeyValue>, tag: String, onDirty: () -> Unit) {
    val isHeaderEditor = tag == "header"
    Column(modifier = Modifier.fillMaxSize().background(ReqLabColors.Background).padding(8.dp)) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Spacer(Modifier.width(32.dp))
            Text("Key", style = MaterialTheme.typography.labelSmall, color = ReqLabColors.OnSurfaceDim, modifier = Modifier.weight(1f))
            Text("Value", style = MaterialTheme.typography.labelSmall, color = ReqLabColors.OnSurfaceDim, modifier = Modifier.weight(1f))
            if (isHeaderEditor) {
                Text("Type", style = MaterialTheme.typography.labelSmall, color = ReqLabColors.OnSurfaceDim, modifier = Modifier.width(70.dp))
            }
            Spacer(Modifier.width(32.dp))
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(entries, key = { idx, _ -> idx }) { idx, kv ->
                KeyValueRow(
                    kv,
                    onDelete = {
                        if (!(isHeaderEditor && kv.kind == HeaderKind.SYSTEM)) {
                            entries.removeAt(idx)
                            onDirty()
                        }
                    },
                    onDirty = onDirty,
                    isHeaderEditor = isHeaderEditor,
                    testTag = "$tag-row-$idx",
                )
            }
        }

        // Add row button
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
            Icon(Icons.Default.Add, contentDescription = "Add", tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(16.dp))
            Text("Add ${tag.replaceFirstChar { it.uppercaseChar() }}", color = ReqLabColors.OnSurfaceDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun KeyValueRow(
    kv: MutableKeyValue,
    onDelete: () -> Unit,
    onDirty: () -> Unit,
    isHeaderEditor: Boolean,
    testTag: String,
) {
    val isSystemHeader = isHeaderEditor && (kv.kind == HeaderKind.SYSTEM || SystemHeaderRules.isSystemHeader(kv.key))

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
            kv.key,
            {
                if (!isSystemHeader) {
                    kv.key = it
                    onDirty()
                }
            },
            "Key",
            Modifier.weight(1f),
        )
        InlineTextField(kv.value, { kv.value = it; onDirty() }, "Value", Modifier.weight(1f))

        if (isHeaderEditor) {
            Row(
                modifier = Modifier.width(70.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isSystemHeader) {
                    Icon(Icons.Default.Lock, contentDescription = "System header", tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(12.dp))
                    Text("System", fontSize = 11.sp, color = ReqLabColors.OnSurfaceDim)
                } else {
                    Text("User", fontSize = 11.sp, color = ReqLabColors.OnSurfaceDim)
                }
            }
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp), enabled = !isSystemHeader) {
            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun InlineTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = ReqLabColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
        cursorBrush = SolidColor(ReqLabColors.Primary),
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ReqLabColors.SurfaceContainer)
            .border(1.dp, ReqLabColors.Border, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, color = ReqLabColors.OnSurfaceDim, fontSize = 13.sp)
            inner()
        },
    )
}

// ── Body Editor ─────────────────────────────────────────────────

@Composable
private fun BodyEditor(tab: RequestTabState, onDirty: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        // Body type selector
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BodyType.entries.forEach { bt ->
                val selected = bt == tab.bodyType
                Text(
                    text = bt.name.replace('_', ' '),
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) ReqLabColors.Primary else ReqLabColors.OnSurfaceDim,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) ReqLabColors.SelectedItem else Color.Transparent)
                        .clickable {
                            tab.bodyType = bt
                            tab.syncSystemHeaders()
                            onDirty()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        // Content editor
        BasicTextField(
            value = tab.bodyContent,
            onValueChange = { tab.bodyContent = it; onDirty() },
            textStyle = TextStyle(color = ReqLabColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
            cursorBrush = SolidColor(ReqLabColors.Primary),
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(ReqLabColors.SurfaceContainer)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp))
                .padding(12.dp)
                .testTag("body-editor"),
            decorationBox = { inner ->
                if (tab.bodyContent.isEmpty()) {
                    Text(
                        when (tab.bodyType) {
                            BodyType.JSON    -> "{\n  \n}"
                            BodyType.GRAPHQL -> "query {\n  \n}"
                            else             -> "Enter request body…"
                        },
                        color = ReqLabColors.OnSurfaceDim,
                        fontSize = 13.sp,
                        fontFamily = CodeFontFamily,
                    )
                }
                inner()
            },
        )
    }
}

// ── Auth Editor ─────────────────────────────────────────────────

@Composable
private fun AuthEditor(tab: RequestTabState, onDirty: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Auth type selector
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AuthType.entries.forEach { at ->
                val selected = at == tab.authType
                Text(
                    text = at.name.replace('_', ' '),
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) ReqLabColors.Primary else ReqLabColors.OnSurfaceDim,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) ReqLabColors.SelectedItem else Color.Transparent)
                        .clickable { tab.authType = at; onDirty() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        // Auth fields based on type
        when (tab.authType) {
            AuthType.NONE -> {
                Text("No authentication", color = ReqLabColors.OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
            }
            AuthType.BASIC -> {
                LabeledField("Username", tab.authUsername) { tab.authUsername = it; onDirty() }
                LabeledField("Password", tab.authPassword) { tab.authPassword = it; onDirty() }
            }
            AuthType.BEARER, AuthType.JWT -> {
                LabeledField("Token", tab.authToken) { tab.authToken = it; onDirty() }
            }
            AuthType.API_KEY -> {
                LabeledField("Key", tab.authApiKey) { tab.authApiKey = it; onDirty() }
                LabeledField("Value", tab.authApiValue) { tab.authApiValue = it; onDirty() }
            }
            AuthType.OAUTH2 -> {
                Text("OAuth 2.0 configuration (coming soon)", color = ReqLabColors.OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LabeledField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ReqLabColors.OnSurfaceVariant)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = ReqLabColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
            cursorBrush = SolidColor(ReqLabColors.Primary),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ReqLabColors.SurfaceContainer)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) Text("Enter $label…", color = ReqLabColors.OnSurfaceDim, fontSize = 13.sp)
                inner()
            },
        )
    }
}

// ── Script Editor ───────────────────────────────────────────────

@Composable
private fun ScriptEditor(script: String, onScriptChanged: (String) -> Unit, title: String) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = ReqLabColors.OnSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        BasicTextField(
            value = script,
            onValueChange = onScriptChanged,
            textStyle = TextStyle(color = ReqLabColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
            cursorBrush = SolidColor(ReqLabColors.Primary),
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(ReqLabColors.SurfaceContainer)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp))
                .padding(12.dp),
            decorationBox = { inner ->
                if (script.isEmpty()) Text("// Write your ${title.lowercase()} here…", color = ReqLabColors.OnSurfaceDim, fontSize = 13.sp, fontFamily = CodeFontFamily)
                inner()
            },
        )
    }
}
