package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.ui.shared.i18n.Strings
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.MutableKeyValue
import com.reqlab.ui.shared.state.RequestEditorTab
import com.reqlab.ui.shared.state.RequestTabState
import com.reqlab.ui.shared.theme.ReqLabColors
import com.reqlab.ui.shared.platform.copyToClipboard as platformCopyToClipboard
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * Top-level composable for a single request tab's editor area.
 *
 * Delegates to focused sub-composables:
 *  - [RequestBar]      — method + URL + action buttons  (RequestBar.kt)
 *  - [EditorTabBar]    — Params / Headers / Body / Auth / Script tab selector
 *  - [KeyValueEditor]  — params and headers tables      (KeyValueEditor.kt)
 *  - [BodyEditor]      — body type + content            (BodyEditor.kt)
 *  - [AuthEditor]      — auth type + credentials        (AuthEditor.kt)
 *  - [ScriptEditor]    — pre-request / post-request scripts (ScriptEditor.kt)
 */
@OptIn(ExperimentalSerializationApi::class)
@Composable
fun RequestEditor(
    tab: RequestTabState,
    state: AppState,
    onSend: () -> Unit,
    onSave: () -> Unit,
    /** Cancels the in-flight request when the Stop button is pressed (H-2). */
    onCancel: () -> Unit = {},
) {
    val markDirty = { tab.markDirty() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReqLabColors.Background)
            .testTag("request-editor"),
    ) {
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
            onCancel = onCancel,
            onSave = onSave,
            copyFormats = buildCopyFormats(tab, state),
            retryEnabled = tab.retryEnabled,
            retryCount = tab.retryCount,
            retryDelayMs = tab.retryDelayMs,
            onRetryEnabledChanged = { tab.retryEnabled = it; markDirty() },
            onRetryCountChanged = { tab.retryCount = it; markDirty() },
            onRetryDelayChanged = { tab.retryDelayMs = it; markDirty() },
            state = state,
        )

        EditorTabBar(
            selectedTab = tab.selectedEditorTab,
            onTabSelected = { tab.selectedEditorTab = it },
            paramCount = tab.params.size,
            headerCount = tab.headers.size,
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(1.dp)) {
            when (tab.selectedEditorTab) {
                RequestEditorTab.PARAMS      -> KeyValueEditor(tab.params, "param", state = state) {
                    syncUrlFromParams(tab)
                    markDirty()
                }
                RequestEditorTab.HEADERS     -> KeyValueEditor(tab.headers, "header", state = state) { markDirty() }
                RequestEditorTab.BODY        -> BodyEditor(tab, state) { markDirty() }
                RequestEditorTab.AUTH        -> AuthEditor(tab, state) { markDirty() }
                RequestEditorTab.PRE_REQUEST -> ScriptEditor(
                    script          = tab.preRequestScript,
                    onScriptChanged = { tab.preRequestScript = it; markDirty() },
                    title           = "Pre-request Script",
                )
                RequestEditorTab.TESTS       -> ScriptEditor(
                    script          = tab.testScript,
                    onScriptChanged = { tab.testScript = it; markDirty() },
                    title           = "Post-request Script",
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
fun syncParamsFromUrl(tab: RequestTabState, url: String) {
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
fun syncUrlFromParams(tab: RequestTabState) {
    val base = tab.url.substringBefore('?')
    val enabled = tab.params.filter { it.enabled && it.key.isNotBlank() }
    tab.url = if (enabled.isEmpty()) base
              else enabled.joinToString(separator = "&", prefix = "$base?") { "${it.key}=${it.value}" }
}

private fun copyToClipboard(text: String) {
    runCatching { platformCopyToClipboard(text) }
}

/**
 * Builds the list of (label, action) pairs shown in the copy-as dropdown.
 * Variables from the active environment are resolved for "resolved" variants.
 */
private fun buildCopyFormats(tab: RequestTabState, state: AppState): List<Pair<String, () -> Unit>> {
    val layers = state.activeVariableLayers()
    return listOf(
        "cURL"                   to { copyToClipboard(buildCurlCommand(tab, layers)) },
        "Python"                 to { copyToClipboard(buildPythonCommand(tab, layers)) },
        "PowerShell"             to { copyToClipboard(buildPowerShellCommand(tab, layers)) },
    )
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
                val label = when (tab) {
                    RequestEditorTab.PARAMS -> Strings.params
                    RequestEditorTab.HEADERS -> Strings.headers
                    RequestEditorTab.BODY -> Strings.body
                    RequestEditorTab.AUTH -> Strings.auth
                    RequestEditorTab.PRE_REQUEST -> Strings.preRequest
                    RequestEditorTab.TESTS -> Strings.tests
                }
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
                            label,
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

