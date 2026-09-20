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
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.FormEntryType
import com.reqlab.ui.shared.theme.ReqLabColors
import com.reqlab.ui.shared.platform.copyToClipboard as platformCopyToClipboard
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter
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
            urlUndoStack = tab.urlUndoStack,
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
 * Rows hold literal values decoded once from the pasted URL.
 */
fun syncParamsFromUrl(tab: RequestTabState, url: String) {
    val parts = splitRequestUrl(url)
    tab.params.clear()
    tab.params.addAll(parseQueryPairs(parts.query).map { MutableKeyValue(key = it.first, value = it.second) })
}

/**
 * Rebuilds the URL's query string from the current params table.
 * Called whenever a param key, value, or enabled-state changes.
 */
fun syncUrlFromParams(tab: RequestTabState) {
    val parts = splitRequestUrl(tab.url)
    val enabled = tab.params.filter { it.enabled && it.key.isNotBlank() }
    val query = encodeOrderedQuery(enabled.map { it.key to it.value }, preserveTemplates = true)
    tab.url = joinRequestUrl(parts.base, query, parts.fragment)
}

internal data class RequestUrlParts(val base: String, val query: String, val fragment: String)

internal fun splitRequestUrl(url: String): RequestUrlParts {
    val hashIdx = url.indexOf('#')
    val withoutFrag = if (hashIdx >= 0) url.substring(0, hashIdx) else url
    val fragment = if (hashIdx >= 0) url.substring(hashIdx + 1) else ""
    val qIdx = withoutFrag.indexOf('?')
    val base = if (qIdx >= 0) withoutFrag.substring(0, qIdx) else withoutFrag
    val query = if (qIdx >= 0 && qIdx < withoutFrag.lastIndex) withoutFrag.substring(qIdx + 1) else ""
    return RequestUrlParts(base, query, fragment)
}

internal fun joinRequestUrl(base: String, query: String, fragment: String): String {
    val withQuery = if (query.isEmpty()) base else "$base?$query"
    return if (fragment.isEmpty()) withQuery else "$withQuery#$fragment"
}

internal fun parseQueryPairs(query: String): List<Pair<String, String>> {
    if (query.isEmpty()) return emptyList()
    return query.split('&').mapNotNull { pair ->
        if (pair.isBlank()) return@mapNotNull null
        val eqIdx = pair.indexOf('=')
        when {
            eqIdx < 0 -> decodeQueryComponent(pair) to ""
            eqIdx == 0 -> "" to decodeQueryComponent(pair.substring(1))
            else -> decodeQueryComponent(pair.substring(0, eqIdx)) to
                decodeQueryComponent(pair.substring(eqIdx + 1))
        }
    }
}

private fun decodeQueryComponent(value: String): String =
    runCatching { value.decodeURLQueryComponent() }.getOrDefault(value)

private val templateToken = Regex("\\{\\{[^{}]+}}")

internal fun encodeQueryComponent(value: String, preserveTemplates: Boolean = false): String {
    if (!preserveTemplates) return value.encodeURLParameter()
    val result = StringBuilder()
    var offset = 0
    templateToken.findAll(value).forEach { match ->
        result.append(value.substring(offset, match.range.first).encodeURLParameter())
        result.append(match.value)
        offset = match.range.last + 1
    }
    result.append(value.substring(offset).encodeURLParameter())
    return result.toString()
}

internal fun encodeOrderedQuery(entries: List<Pair<String, String>>, preserveTemplates: Boolean = false): String =
    entries.joinToString("&") { (key, value) ->
        "${encodeQueryComponent(key, preserveTemplates)}=${encodeQueryComponent(value, preserveTemplates)}"
    }

private fun copyToClipboard(text: String) {
    runCatching { platformCopyToClipboard(text) }
}

/**
 * Builds the list of (label, action) pairs shown in the copy-as dropdown.
 * Variables from the active environment are resolved for "resolved" variants.
 */
internal fun buildCopyFormats(tab: RequestTabState, state: AppState): List<CopyFormatOption> {
    val layers = state.activeVariableLayers()
    val fileReason = when {
        tab.bodyType == BodyType.BINARY -> "Embedded binary has no reusable file path"
        tab.bodyType == BodyType.FORM_DATA && tab.formRows.any { it.enabled && it.type == FormEntryType.FILE } ->
            "Embedded multipart file has no reusable file path"
        else -> null
    }
    val headers = copyHeaderList(tab, layers, omitMultipartContentType = false)
    val duplicateHeaders = headers.map { it.first.lowercase() }.distinct().size != headers.size
    val mapReason = fileReason ?: if (duplicateHeaders) "Duplicate headers cannot be represented by this API" else null
    val multipartKeys = tab.formRows.filter { it.enabled }.map { it.key }
    val powerShellReason = mapReason ?: if (
        tab.bodyType == BodyType.FORM_DATA && multipartKeys.distinct().size != multipartKeys.size
    ) "Duplicate multipart field names cannot be represented by PowerShell -Form" else null
    return listOf(
        CopyFormatOption("cURL", fileReason == null, fileReason) {
            copyToClipboard(buildCurlCommand(tab, layers, state.settings.allowJson5InJsonBodies))
        },
        CopyFormatOption("Python", mapReason == null, mapReason) {
            copyToClipboard(buildPythonCommand(tab, layers, state.settings.allowJson5InJsonBodies))
        },
        CopyFormatOption("PowerShell", powerShellReason == null, powerShellReason) {
            copyToClipboard(buildPowerShellCommand(tab, layers, state.settings.allowJson5InJsonBodies))
        },
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
