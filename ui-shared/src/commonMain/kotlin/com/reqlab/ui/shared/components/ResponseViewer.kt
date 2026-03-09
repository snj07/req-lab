package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.core.model.ResponseDefinition
import com.reqlab.ui.shared.state.RequestTabState
import com.reqlab.ui.shared.state.ResponseTab
import com.reqlab.ui.shared.theme.CodeFontFamily
import com.reqlab.ui.shared.theme.ReqLabColors
import com.reqlab.ui.shared.theme.statusCodeColor
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import com.reqlab.ui.shared.platform.copyToClipboard as platformCopyToClipboard
import com.reqlab.ui.shared.platform.saveFileForExport

@OptIn(ExperimentalSerializationApi::class)
private val prettyJson = Json { prettyPrint = true; prettyPrintIndent = "  " }

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
                    Text("Sending request…", color = ReqLabColors.OnSurfaceDim, fontSize = 13.sp)
                }
                error != null -> {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = ReqLabColors.Error,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Request failed", color = ReqLabColors.Error, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
                    Text("Response", color = ReqLabColors.OnSurfaceDim, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Enter a URL and click Send to see the response here",
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
 * Enhanced response body viewer with:
 * - Syntax highlighting (JSON, XML, HTML, GraphQL)
 * - Line numbers gutter
 * - Word wrap toggle
 * - Search with match highlighting and navigation
 * - Format / beautify button
 * - Virtualised LazyColumn for large responses (H-3)
 */
private const val LARGE_BODY_LINE_THRESHOLD = 200

@Composable
private fun ResponseBodyView(response: ResponseDefinition) {
    val body = response.bodyText
    val language = detectLanguage(response.contentType)

    // Formatting state
    var isFormatted by remember { mutableStateOf(true) }
    val formatted = remember(body, isFormatted, language) {
        if (isFormatted) autoFormat(body, language) else body
    }

    // Word wrap state
    var wordWrap by remember { mutableStateOf(true) }

    // Search state
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var activeMatchIndex by remember { mutableIntStateOf(0) }

    val lines = remember(formatted) { formatted.split('\n') }
    val searchMatches = remember(lines, searchQuery) {
        findSearchMatches(lines, searchQuery)
    }

    // Keep active match in bounds
    LaunchedEffect(searchMatches.size) {
        if (searchMatches.isNotEmpty()) {
            activeMatchIndex = activeMatchIndex.coerceIn(0, searchMatches.size - 1)
        } else {
            activeMatchIndex = 0
        }
    }

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Body toolbar ─────────────────────────────
        ResponseBodyToolbar(
            language = language,
            wordWrap = wordWrap,
            onToggleWordWrap = { wordWrap = !wordWrap },
            isFormatted = isFormatted,
            onToggleFormat = { isFormatted = !isFormatted },
            showSearch = showSearch,
            onToggleSearch = {
                showSearch = !showSearch
                if (!showSearch) searchQuery = ""
            },
            onCopyBody = { copyToClipboard(formatted) },
            onCopyFormatted = { copyToClipboard(autoFormat(body, language)) },
            onDownload = { saveResponseToFile(response) },
        )

        // ── Search bar ───────────────────────────────
        if (showSearch) {
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it; activeMatchIndex = 0 },
                matchCount = searchMatches.size,
                activeIndex = activeMatchIndex,
                onNext = {
                    if (searchMatches.isNotEmpty()) {
                        activeMatchIndex = (activeMatchIndex + 1) % searchMatches.size
                        coroutineScope.launch {
                            lazyListState.animateScrollToItem(searchMatches[activeMatchIndex].lineIndex)
                        }
                    }
                },
                onPrev = {
                    if (searchMatches.isNotEmpty()) {
                        activeMatchIndex = (activeMatchIndex - 1 + searchMatches.size) % searchMatches.size
                        coroutineScope.launch {
                            lazyListState.animateScrollToItem(searchMatches[activeMatchIndex].lineIndex)
                        }
                    }
                },
                onClose = { showSearch = false; searchQuery = "" },
            )
        }

        // ── Code view with line numbers ──────────────
        if (lines.size <= LARGE_BODY_LINE_THRESHOLD) {
            SmallResponseBodyView(lines, language, wordWrap, searchMatches, activeMatchIndex)
        } else {
            LargeResponseBodyView(lines, language, wordWrap, searchMatches, activeMatchIndex, lazyListState)
        }
    }
}

// ── Body Toolbar ────────────────────────────────────────────────

@Composable
private fun ResponseBodyToolbar(
    language: SyntaxLanguage,
    wordWrap: Boolean,
    onToggleWordWrap: () -> Unit,
    isFormatted: Boolean,
    onToggleFormat: () -> Unit,
    showSearch: Boolean,
    onToggleSearch: () -> Unit,
    onCopyBody: () -> Unit,
    onCopyFormatted: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReqLabColors.SurfaceContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("response-body-toolbar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Language badge
        Text(
            text = language.name,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = ReqLabColors.Primary,
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(ReqLabColors.Primary.copy(alpha = 0.10f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )

        Spacer(Modifier.weight(1f))

        // Format / beautify toggle
        ToolbarIconButton(
            icon = Icons.Default.FormatAlignLeft,
            contentDescription = if (isFormatted) "Raw view" else "Format",
            active = isFormatted,
            onClick = onToggleFormat,
            testTag = "format-toggle",
        )

        // Word wrap toggle
        ToolbarIconButton(
            icon = Icons.Default.WrapText,
            contentDescription = if (wordWrap) "Disable word wrap" else "Enable word wrap",
            active = wordWrap,
            onClick = onToggleWordWrap,
            testTag = "word-wrap-toggle",
        )

        // Search toggle
        ToolbarIconButton(
            icon = Icons.Default.Search,
            contentDescription = "Search",
            active = showSearch,
            onClick = onToggleSearch,
            testTag = "search-toggle",
        )

        Box(
            Modifier
                .width(1.dp)
                .height(20.dp)
                .background(ReqLabColors.Border)
        )

        // Copy body
        ToolbarIconButton(
            icon = Icons.Default.ContentCopy,
            contentDescription = "Copy body",
            onClick = onCopyBody,
            testTag = "copy-body-button",
        )

        // Download
        ToolbarIconButton(
            icon = Icons.Default.Download,
            contentDescription = "Download response",
            onClick = onDownload,
            testTag = "download-body-button",
        )
    }

    Box(Modifier.fillMaxWidth().height(1.dp).background(ReqLabColors.Border))
}

@Composable
private fun ToolbarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean = false,
    onClick: () -> Unit,
    testTag: String = "",
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(28.dp)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (active) ReqLabColors.Primary else ReqLabColors.OnSurfaceDim,
            modifier = Modifier.size(16.dp),
        )
    }
}

// ── Search Bar ──────────────────────────────────────────────────

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    activeIndex: Int,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReqLabColors.SurfaceContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("response-search-bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Search input
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
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
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("search-input"),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            "Search in response…",
                            color = ReqLabColors.OnSurfaceDim,
                            fontSize = 12.sp,
                            fontFamily = CodeFontFamily,
                        )
                    }
                    inner()
                }
            }
        )

        // Match count
        if (query.isNotEmpty()) {
            Text(
                text = if (matchCount > 0) "${activeIndex + 1}/$matchCount" else "0 results",
                color = if (matchCount > 0) ReqLabColors.OnSurfaceVariant else ReqLabColors.OnSurfaceDim,
                fontSize = 11.sp,
                modifier = Modifier.widthIn(min = 60.dp),
            )
        }

        // Navigate prev/next
        IconButton(onClick = onPrev, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.ArrowUpward, "Previous match", tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(14.dp))
        }
        IconButton(onClick = onNext, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.ArrowDownward, "Next match", tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(14.dp))
        }

        // Close
        IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, "Close search", tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(14.dp))
        }
    }

    Box(Modifier.fillMaxWidth().height(1.dp).background(ReqLabColors.Border))
}

// ── Small Response (full text selection support) ────────────────

@Composable
private fun SmallResponseBodyView(
    lines: List<String>,
    language: SyntaxLanguage,
    wordWrap: Boolean,
    searchMatches: List<SearchMatch>,
    activeMatchIndex: Int,
) {
    val scrollState = rememberScrollState()
    val hScrollState = rememberScrollState()

    SelectionContainer {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .then(if (!wordWrap) Modifier.horizontalScroll(hScrollState) else Modifier)
                .testTag("response-body"),
        ) {
            // Line numbers gutter
            LineNumbersGutter(lines.size)

            // Code content
            Column(modifier = Modifier.padding(end = 12.dp, top = 8.dp, bottom = 8.dp)) {
                lines.forEachIndexed { index, line ->
                    val highlighted = remember(line, language) { highlightLine(line.ifEmpty { " " }, language) }
                    val lineMatches = searchMatches.filter { it.lineIndex == index }
                    val globalStart = searchMatches.indexOfFirst { it.lineIndex == index && it.startOffset == lineMatches.firstOrNull()?.startOffset }
                    val withSearch = if (lineMatches.isNotEmpty() && globalStart >= 0) {
                        applySearchHighlights(highlighted, lineMatches, activeMatchIndex, globalStart)
                    } else {
                        highlighted
                    }
                    Text(
                        text = withSearch,
                        fontSize = 13.sp,
                        fontFamily = CodeFontFamily,
                        softWrap = wordWrap,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}

// ── Large Response (virtualised) ────────────────────────────────

@Composable
private fun LargeResponseBodyView(
    lines: List<String>,
    language: SyntaxLanguage,
    wordWrap: Boolean,
    searchMatches: List<SearchMatch>,
    activeMatchIndex: Int,
    lazyListState: LazyListState,
) {
    val hScrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .then(if (!wordWrap) Modifier.horizontalScroll(hScrollState) else Modifier)
            .testTag("response-body"),
    ) {
        // Line numbers gutter (virtualised with same LazyColumn state)
        val lineNumWidth = (lines.size.toString().length * 9 + 16).dp

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(lines.size) { index ->
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    // Line number
                    Box(
                        modifier = Modifier
                            .width(lineNumWidth)
                            .fillMaxHeight()
                            .background(ReqLabColors.SurfaceContainer)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = ReqLabColors.OnSurfaceDim,
                            fontSize = 12.sp,
                            fontFamily = CodeFontFamily,
                            lineHeight = 20.sp,
                        )
                    }

                    // Code line
                    val line = lines[index]
                    val highlighted = remember(line, language) { highlightLine(line.ifEmpty { " " }, language) }
                    val lineMatches = searchMatches.filter { it.lineIndex == index }
                    val globalStart = searchMatches.indexOfFirst { it.lineIndex == index && it.startOffset == lineMatches.firstOrNull()?.startOffset }
                    val withSearch = if (lineMatches.isNotEmpty() && globalStart >= 0) {
                        applySearchHighlights(highlighted, lineMatches, activeMatchIndex, globalStart)
                    } else {
                        highlighted
                    }
                    Text(
                        text = withSearch,
                        fontSize = 13.sp,
                        fontFamily = CodeFontFamily,
                        softWrap = wordWrap,
                        lineHeight = 20.sp,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .then(if (index == 0) Modifier.padding(top = 8.dp) else Modifier)
                            .then(if (index == lines.lastIndex) Modifier.padding(bottom = 8.dp) else Modifier),
                    )
                }
            }
        }
    }
}

// ── Line Numbers Gutter (for small response) ────────────────────

@Composable
private fun LineNumbersGutter(lineCount: Int) {
    val gutterWidth = (lineCount.toString().length * 9 + 16).dp
    Column(
        modifier = Modifier
            .width(gutterWidth)
            .background(ReqLabColors.SurfaceContainer)
            .padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        for (i in 1..lineCount) {
            Text(
                text = "$i",
                color = ReqLabColors.OnSurfaceDim,
                fontSize = 12.sp,
                fontFamily = CodeFontFamily,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

// ── Headers View ────────────────────────────────────────────────

@Composable
private fun ResponseHeadersView(response: ResponseDefinition) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            ) {
                Text("Key", style = MaterialTheme.typography.labelSmall, color = ReqLabColors.OnSurfaceDim, modifier = Modifier.weight(0.4f))
                Text("Value", style = MaterialTheme.typography.labelSmall, color = ReqLabColors.OnSurfaceDim, modifier = Modifier.weight(0.6f))
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
            Text("No cookies", color = ReqLabColors.OnSurfaceDim, fontSize = 13.sp)
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

private fun tryPrettyPrint(raw: String): String = try {
    val element = prettyJson.decodeFromString(JsonElement.serializer(), raw)
    prettyJson.encodeToString(JsonElement.serializer(), element)
} catch (_: Exception) {
    raw
}

/**
 * Auto-format response body based on detected language.
 */
private fun autoFormat(raw: String, language: SyntaxLanguage): String = when (language) {
    SyntaxLanguage.JSON    -> tryPrettyPrint(raw)
    SyntaxLanguage.XML     -> formatXml(raw)
    SyntaxLanguage.HTML    -> formatXml(raw)  // reuse XML formatter for HTML
    SyntaxLanguage.GRAPHQL -> raw  // GraphQL is typically already formatted
    SyntaxLanguage.PLAIN   -> raw
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024        -> "$bytes B"
    bytes < 1024 * 1024 -> "${((bytes / 1024.0 * 10).toLong() / 10.0)} KB"
    else                -> "${((bytes / (1024.0 * 1024.0) * 10).toLong() / 10.0)} MB"
}

private fun copyToClipboard(text: String) {
    platformCopyToClipboard(text)
}

private fun saveResponseToFile(response: ResponseDefinition) {
    val ext = fileExtensionForContentType(response.contentType)
    val defaultName = "response.$ext"
    saveFileForExport(response.bodyText, defaultName)
}
