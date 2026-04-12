package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.core.model.ResponseDefinition
import com.reqlab.ui.shared.i18n.Strings
import com.reqlab.ui.shared.state.RequestTabState
import com.reqlab.ui.shared.state.ResponseTab
import com.reqlab.ui.shared.theme.CodeFontFamily
import com.reqlab.ui.shared.theme.ReqLabColors
import com.reqlab.ui.shared.theme.statusCodeColor
import kotlinx.serialization.ExperimentalSerializationApi
import com.reqlab.ui.shared.platform.saveFileForExport

@OptIn(ExperimentalSerializationApi::class)
@Composable
fun ResponseViewer(tab: RequestTabState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReqLabColors.Background)
            .testTag("response-viewer"),
    ) {
        val response = tab.response

        if (response == null) {
            EmptyResponseState(tab)
        } else {
            // ── Status bar ──────────────────────────────────────
            ResponseStatusBar(response)

            // ── Tabs ────────────────────────────────────────────
            ResponseTabBar(tab.responseTab, onTabSelected = { tab.responseTab = it })

            // ── Content ─────────────────────────────────────────
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (tab.responseTab) {
                    ResponseTab.BODY    -> ResponseBodyView(response)
                    ResponseTab.HEADERS -> ResponseHeadersView(response)
                    ResponseTab.COOKIES -> ResponseCookiesView(response)
                    ResponseTab.TIMING  -> ResponseTimingView(response)
                    ResponseTab.RAW     -> ResponseRawView(response)
                }
            }
        }
    }
}

// ── Empty State ─────────────────────────────────────────────────

@Composable
private fun EmptyResponseState(tab: RequestTabState) {
    val error = tab.lastError
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                tab.isLoading -> {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = ReqLabColors.Primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(Strings.sendingRequest, color = ReqLabColors.OnSurfaceDim, fontSize = 13.sp)
                }
                error != null -> {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = ReqLabColors.Error,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(Strings.requestFailed, color = ReqLabColors.Error, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        error,
                        color = ReqLabColors.OnSurfaceDim,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ReqLabColors.Error.copy(alpha = 0.08f))
                            .padding(12.dp)
                            .testTag("response-error-message"),
                    )
                }
                else -> {
                    Text(Strings.response, color = ReqLabColors.OnSurfaceDim, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        Strings.sendToSeeResponse,
                        color = ReqLabColors.OnSurfaceDim,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

// ── Status Bar ──────────────────────────────────────────────────

@Composable
private fun ResponseStatusBar(response: ResponseDefinition) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReqLabColors.Surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("response-status-bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Status code badge
        val statusColor = statusCodeColor(response.statusCode)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${response.statusCode}",
                color = statusColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            Text(
                text = response.statusText,
                color = statusColor,
                fontSize = 13.sp,
            )
        }

        // Response time
        MetricChip("${response.metrics.responseTimeMs} ms", ReqLabColors.Secondary)

        // Response size
        MetricChip(formatBytes(response.metrics.responseSizeBytes), ReqLabColors.Tertiary)

        Spacer(Modifier.weight(1f))
    }

    Box(Modifier.fillMaxWidth().height(1.dp).background(ReqLabColors.Border))
}

@Composable
private fun MetricChip(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// ── Tab Bar ─────────────────────────────────────────────────────

@Composable
private fun ResponseTabBar(selectedTab: ResponseTab, onTabSelected: (ResponseTab) -> Unit) {
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
            ResponseTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Tab(
                    selected = selected,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("response-tab-${tab.name.lowercase()}"),
                ) {
                    Text(
                        tab.label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) ReqLabColors.Primary else ReqLabColors.OnSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(ReqLabColors.Border)
        )
    }
}

// ── Body View ───────────────────────────────────────────────────

/**
 * Enhanced response body viewer powered by the shared [CodeEditor].
 * Provides syntax highlighting, code folding, search, formatting,
 * word wrap, copy, and download.
 */
@kotlinx.serialization.ExperimentalSerializationApi
@Composable
private fun ResponseBodyView(response: ResponseDefinition) {
    val language = detectLanguage(response.contentType)

    CodeEditor(
        text = response.bodyText,
        onTextChange = null,   // read-only
        language = language,
        modifier = Modifier.fillMaxSize(),
        showToolbar = true,
        enableFolding = true,
        enableSearch = true,
        enableFormat = true,
        enableWordWrap = true,
        enableCopy = true,
        enableDownload = true,
        onDownload = { saveResponseToFile(response) },
        testTagPrefix = "response",
    )
}

// ── Headers View ────────────────────────────────────────────────

@Composable
private fun ResponseHeadersView(response: ResponseDefinition) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            ) {
                Text(Strings.t("key_upper"), style = MaterialTheme.typography.labelSmall, color = ReqLabColors.OnSurfaceDim, modifier = Modifier.weight(0.4f))
                Text(Strings.t("value_upper"), style = MaterialTheme.typography.labelSmall, color = ReqLabColors.OnSurfaceDim, modifier = Modifier.weight(0.6f))
            }
        }
        items(response.headers) { header ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ReqLabColors.SurfaceContainer)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(header.key, color = ReqLabColors.Primary, fontSize = 12.sp, fontFamily = CodeFontFamily, modifier = Modifier.weight(0.4f))
                Text(header.value, color = ReqLabColors.OnSurface, fontSize = 12.sp, fontFamily = CodeFontFamily, modifier = Modifier.weight(0.6f))
            }
        }
    }
}

// ── Cookies View ────────────────────────────────────────────────

@Composable
private fun ResponseCookiesView(response: ResponseDefinition) {
    if (response.cookies.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(Strings.noCookies, color = ReqLabColors.OnSurfaceDim, fontSize = 13.sp)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            items(response.cookies) { cookie ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ReqLabColors.SurfaceContainer)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(cookie.key, color = ReqLabColors.Tertiary, fontSize = 12.sp, fontFamily = CodeFontFamily, modifier = Modifier.weight(0.4f))
                    Text(cookie.value, color = ReqLabColors.OnSurface, fontSize = 12.sp, fontFamily = CodeFontFamily, modifier = Modifier.weight(0.6f))
                }
            }
        }
    }
}

// ── Raw View ────────────────────────────────────────────────────

@Composable
private fun ResponseRawView(response: ResponseDefinition) {
    SelectionContainer {
        Text(
            text = response.bodyText,
            color = ReqLabColors.OnSurface,
            fontSize = 13.sp,
            fontFamily = CodeFontFamily,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        )
    }
}

// ── Timing View ─────────────────────────────────────────────────

@Composable
private fun ResponseTimingView(response: ResponseDefinition) {
    val m = response.metrics
    data class Phase(val label: String, val ms: Long, val color: Color)

    val phases = buildList {
        if (m.dnsMs >= 0) add(Phase("DNS Lookup", m.dnsMs, Color(0xFF42A5F5)))
        if (m.connectMs >= 0) add(Phase("TCP Connect", m.connectMs, Color(0xFFFFA726)))
        if (m.tlsMs >= 0) add(Phase("TLS Handshake", m.tlsMs, Color(0xFFAB47BC)))
        if (m.serverMs >= 0) add(Phase("Server Processing", m.serverMs, Color(0xFF66BB6A)))
        if (m.downloadMs >= 0) add(Phase("Content Download", m.downloadMs, Color(0xFFEF5350)))
    }

    val totalMs = m.responseTimeMs.coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("response-timing-view"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Request Timing Breakdown",
            color = ReqLabColors.OnSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )

        // Summary chip
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricChip("Total: ${m.responseTimeMs} ms", ReqLabColors.Secondary)
            MetricChip("Size: ${formatBytes(m.responseSizeBytes)}", ReqLabColors.Tertiary)
        }

        Spacer(Modifier.height(4.dp))

        if (phases.isEmpty()) {
            Text(
                "Detailed timing phases are not available for this response.",
                color = ReqLabColors.OnSurfaceDim,
                fontSize = 13.sp,
            )
        } else {
            // Waterfall bar chart
            phases.forEach { phase ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(phase.label, color = ReqLabColors.OnSurface, fontSize = 13.sp)
                        Text(
                            "${phase.ms} ms",
                            color = ReqLabColors.OnSurfaceVariant,
                            fontSize = 13.sp,
                            fontFamily = CodeFontFamily,
                        )
                    }
                    // Bar
                    val fraction = (phase.ms.toFloat() / totalMs).coerceIn(0.005f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ReqLabColors.SurfaceContainer),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(phase.color)
                                .testTag("timing-bar-${phase.label}"),
                        )
                    }
                }
            }
        }
    }
}

// ── Utilities ───────────────────────────────────────────────────

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024        -> "$bytes B"
    bytes < 1024 * 1024 -> "${((bytes / 1024.0 * 10).toLong() / 10.0)} KB"
    else                -> "${((bytes / (1024.0 * 1024.0) * 10).toLong() / 10.0)} MB"
}

private fun saveResponseToFile(response: ResponseDefinition) {
    val ext = fileExtensionForContentType(response.contentType)
    val defaultName = "response.$ext"
    saveFileForExport(response.bodyText, defaultName)
}
