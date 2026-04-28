package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.reqlab.core.model.BodyType
import com.reqlab.ui.shared.i18n.Strings
import com.reqlab.core.model.GraphQlBody
import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.model.KeyValueEntry
import com.reqlab.core.model.RequestBody
import com.reqlab.core.model.RequestDefinition
import com.reqlab.core.network.NetworkEvent
import com.reqlab.core.network.NetworkLogger
import com.reqlab.core.network.RetryPolicy
import com.reqlab.ui.shared.network.NetworkClientFactory
import com.reqlab.ui.shared.platform.currentTimeMillis
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.LogLevel
import com.reqlab.ui.shared.theme.CodeFontFamily
import com.reqlab.ui.shared.theme.ReqLabColors
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

// ── GraphQL Tab Sections ────────────────────────────────────────

private enum class GraphQLTab(val label: String) {
    QUERY("Query"),
    VARIABLES("Variables"),
    HEADERS("Headers"),
}

private val prettyJson = Json { prettyPrint = true }

/**
 * Full GraphQL API testing panel with query editor,
 * variables panel, headers, and response viewer.
 */
@Composable
fun GraphQLPanel(state: AppState) {
    val scope = rememberCoroutineScope()

    var endpoint by remember { mutableStateOf("https://api.example.com/graphql") }
    var query by remember {
        mutableStateOf(
            """query {
  __schema {
    types {
      name
    }
  }
}"""
        )
    }
    var variables by remember { mutableStateOf("{}") }
    var headers by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(GraphQLTab.QUERY) }
    var isLoading by remember { mutableStateOf(false) }
    var responseBody by remember { mutableStateOf<String?>(null) }
    var responseStatus by remember { mutableStateOf<Int?>(null) }
    var responseTimeMs by remember { mutableStateOf<Long?>(null) }
    var lastError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReqLabColors.Background)
            .testTag("graphql-panel"),
    ) {
        // ── Endpoint URL bar ──────────────────────────────
        GraphQLUrlBar(
            url = endpoint,
            onUrlChange = { endpoint = it },
            isLoading = isLoading,
            onExecute = {
                if (endpoint.isBlank()) return@GraphQLUrlBar
                isLoading = true
                responseBody = null
                responseStatus = null
                responseTimeMs = null
                lastError = null

                scope.launch {
                    try {
                        // Build GraphQL JSON body
                        val graphQlBody = GraphQlBody(
                            query = query,
                            operationName = null,
                            variablesJson = variables.takeIf { it.isNotBlank() && it != "{}" },
                        )
                        val requestBody = RequestBody(
                            type = BodyType.GRAPHQL,
                            content = null,
                            graphQl = graphQlBody,
                        )

                        // Parse custom headers
                        val headerList = headers.lines()
                            .filter { it.contains(":") }
                            .map {
                                val (k, v) = it.split(":", limit = 2)
                                KeyValueEntry(k.trim(), v.trim())
                            }

                        val request = RequestDefinition(
                            id = "graphql-panel",
                            name = "GraphQL Query",
                            method = HttpMethodType.POST,
                            url = endpoint,
                            headers = headerList,
                            body = requestBody,
                            createdAtEpochMillis = currentTimeMillis(),
                            updatedAtEpochMillis = currentTimeMillis(),
                        )

                        val logger = object : NetworkLogger {
                            override fun debug(msg: String) { state.log(msg) }
                            override fun info(msg: String) { state.log(msg) }
                            override fun error(msg: String, throwable: Throwable?) {
                                state.log(msg, LogLevel.ERROR)
                            }
                        }

                        val client = NetworkClientFactory.build(
                            settings = state.settings,
                            logger = logger,
                            retryPolicy = RetryPolicy(maxAttempts = 1),
                        )

                        client.execute(request, state.activeVariableLayers()).collect { event ->
                            when (event) {
                                is NetworkEvent.Success -> {
                                    responseBody = formatJsonSafe(event.response.bodyText)
                                    responseStatus = event.response.statusCode
                                    responseTimeMs = event.response.metrics.responseTimeMs
                                }
                                is NetworkEvent.Failure -> {
                                    lastError = event.error.message ?: "Unknown error"
                                }
                                else -> { /* ignore Started / RetryScheduled */ }
                            }
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        lastError = e.message ?: "Unknown error"
                    } finally {
                        isLoading = false
                    }
                }
            },
        )

        // ── Editor / Response split ──────────────────────
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Left: Query editor + tabs
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(end = 1.dp),
            ) {
                // Tab bar
                GraphQLTabBar(
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                )

                // Tab content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(ReqLabColors.Surface),
                ) {
                    when (selectedTab) {
                        GraphQLTab.QUERY -> GraphQLQueryEditor(
                            query = query,
                            onQueryChange = { query = it },
                        )
                        GraphQLTab.VARIABLES -> GraphQLVariablesEditor(
                            variables = variables,
                            onVariablesChange = { variables = it },
                        )
                        GraphQLTab.HEADERS -> GraphQLHeadersEditor(
                            headers = headers,
                            onHeadersChange = { headers = it },
                        )
                    }
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxSize()
                    .background(ReqLabColors.Border),
            )

            // Right: Response viewer
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(start = 1.dp),
            ) {
                GraphQLResponseViewer(
                    responseBody = responseBody,
                    responseStatus = responseStatus,
                    responseTimeMs = responseTimeMs,
                    lastError = lastError,
                    isLoading = isLoading,
                )
            }
        }
    }
}

// ── URL Bar ─────────────────────────────────────────────────────

@Composable
private fun GraphQLUrlBar(
    url: String,
    onUrlChange: (String) -> Unit,
    isLoading: Boolean,
    onExecute: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReqLabColors.Surface)
            .border(width = 1.dp, color = ReqLabColors.Border)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // POST badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF49CC90).copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = "POST",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF49CC90),
                fontFamily = CodeFontFamily,
            )
        }

        // URL input
        BasicTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(ReqLabColors.Background)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("graphql-url-input"),
            textStyle = TextStyle(
                fontSize = 13.sp,
                fontFamily = CodeFontFamily,
                color = ReqLabColors.OnSurface,
            ),
            cursorBrush = SolidColor(ReqLabColors.Primary),
            singleLine = true,
            decorationBox = { inner ->
                if (url.isEmpty()) {
                    Text(
                        text = "Enter GraphQL endpoint URL…",
                        fontSize = 13.sp,
                        fontFamily = CodeFontFamily,
                        color = ReqLabColors.OnSurfaceDim,
                    )
                }
                inner()
            },
        )

        // Execute button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isLoading) ReqLabColors.OnSurfaceDim else ReqLabColors.Primary)
                .clickable(enabled = !isLoading) { onExecute() }
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("graphql-execute-button"),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = Strings.t("execute"),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = if (isLoading) Strings.t("running") else Strings.t("execute"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
    }
}

// ── Tab Bar ─────────────────────────────────────────────────────

@Composable
private fun GraphQLTabBar(
    selected: GraphQLTab,
    onSelect: (GraphQLTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReqLabColors.Surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        GraphQLTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) ReqLabColors.Primary.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = tab.label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) ReqLabColors.Primary else ReqLabColors.OnSurface,
                )
            }
        }
    }
}

// ── Query Editor ───────────────────────────────────────────────

@Composable
private fun GraphQLQueryEditor(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ReqLabColors.Surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "GRAPHQL",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE535AB),
                fontFamily = CodeFontFamily,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE535AB).copy(alpha = 0.1f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { onQueryChange(formatGraphQL(query)) },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.FormatAlignLeft,
                    contentDescription = Strings.format,
                    tint = ReqLabColors.OnSurfaceDim,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(ReqLabColors.Background),
        ) {
            // Line numbers + editor
            Row(modifier = Modifier.fillMaxSize()) {
                val lines = query.lines()
                // Line numbers gutter
                Column(
                    modifier = Modifier
                        .width(40.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    lines.forEachIndexed { idx, _ ->
                        Text(
                            text = "${idx + 1}",
                            fontSize = 12.sp,
                            fontFamily = CodeFontFamily,
                            color = ReqLabColors.OnSurfaceDim.copy(alpha = 0.5f),
                            modifier = Modifier.padding(end = 8.dp),
                            lineHeight = 18.sp,
                        )
                    }
                }

                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxSize()
                        .background(ReqLabColors.Border),
                )

                // Text editor
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(8.dp)
                        .testTag("graphql-query-editor"),
                    textStyle = TextStyle(
                        fontSize = 13.sp,
                        fontFamily = CodeFontFamily,
                        color = ReqLabColors.OnSurface,
                        lineHeight = 18.sp,
                    ),
                    cursorBrush = SolidColor(ReqLabColors.Primary),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                text = "# Enter your GraphQL query\nquery {\n  \n}",
                                fontSize = 13.sp,
                                fontFamily = CodeFontFamily,
                                color = ReqLabColors.OnSurfaceDim.copy(alpha = 0.5f),
                                lineHeight = 18.sp,
                            )
                        }
                        inner()
                    },
                )
            }
        }
    }
}

// ── Variables Editor ───────────────────────────────────────────

@Composable
private fun GraphQLVariablesEditor(
    variables: String,
    onVariablesChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ReqLabColors.Surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "JSON",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFA500),
                fontFamily = CodeFontFamily,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFFFA500).copy(alpha = 0.1f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Query variables as JSON object",
                fontSize = 11.sp,
                color = ReqLabColors.OnSurfaceDim,
            )
        }

        BasicTextField(
            value = variables,
            onValueChange = onVariablesChange,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(ReqLabColors.Background)
                .padding(12.dp)
                .testTag("graphql-variables-editor"),
            textStyle = TextStyle(
                fontSize = 13.sp,
                fontFamily = CodeFontFamily,
                color = ReqLabColors.OnSurface,
                lineHeight = 18.sp,
            ),
            cursorBrush = SolidColor(ReqLabColors.Primary),
            decorationBox = { inner ->
                if (variables.isEmpty()) {
                    Text(
                        text = "{\n  \"key\": \"value\"\n}",
                        fontSize = 13.sp,
                        fontFamily = CodeFontFamily,
                        color = ReqLabColors.OnSurfaceDim.copy(alpha = 0.5f),
                        lineHeight = 18.sp,
                    )
                }
                inner()
            },
        )
    }
}

// ── Headers Editor ─────────────────────────────────────────────

@Composable
private fun GraphQLHeadersEditor(
    headers: String,
    onHeadersChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ReqLabColors.Surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "HEADERS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ReqLabColors.OnSurfaceDim,
                fontFamily = CodeFontFamily,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Key: Value (one per line)",
                fontSize = 11.sp,
                color = ReqLabColors.OnSurfaceDim,
            )
        }

        BasicTextField(
            value = headers,
            onValueChange = onHeadersChange,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(ReqLabColors.Background)
                .padding(12.dp)
                .testTag("graphql-headers-editor"),
            textStyle = TextStyle(
                fontSize = 13.sp,
                fontFamily = CodeFontFamily,
                color = ReqLabColors.OnSurface,
                lineHeight = 18.sp,
            ),
            cursorBrush = SolidColor(ReqLabColors.Primary),
            decorationBox = { inner ->
                if (headers.isEmpty()) {
                    Text(
                        text = "Authorization: Bearer token\nContent-Type: application/json",
                        fontSize = 13.sp,
                        fontFamily = CodeFontFamily,
                        color = ReqLabColors.OnSurfaceDim.copy(alpha = 0.5f),
                        lineHeight = 18.sp,
                    )
                }
                inner()
            },
        )
    }
}

// ── Response Viewer ────────────────────────────────────────────

@Composable
private fun GraphQLResponseViewer(
    responseBody: String?,
    responseStatus: Int?,
    responseTimeMs: Long?,
    lastError: String?,
    isLoading: Boolean,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Status bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ReqLabColors.Surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Response",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ReqLabColors.OnSurface,
            )
            Spacer(Modifier.weight(1f))
            if (responseStatus != null) {
                val statusColor = when {
                    responseStatus in 200..299 -> Color(0xFF49CC90)
                    responseStatus in 300..399 -> Color(0xFFFFA500)
                    else -> Color(0xFFFF6B6B)
                }
                Text(
                    text = "$responseStatus",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CodeFontFamily,
                    color = statusColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            if (responseTimeMs != null) {
                Text(
                    text = "${responseTimeMs}ms",
                    fontSize = 11.sp,
                    fontFamily = CodeFontFamily,
                    color = ReqLabColors.OnSurfaceDim,
                )
            }
        }

        // Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ReqLabColors.Border),
        )

        // Body
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(ReqLabColors.Background),
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Executing query…",
                            fontSize = 13.sp,
                            color = ReqLabColors.OnSurfaceDim,
                        )
                    }
                }
                lastError != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = "Error",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFF6B6B),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = lastError,
                            fontSize = 12.sp,
                            fontFamily = CodeFontFamily,
                            color = Color(0xFFFF6B6B).copy(alpha = 0.8f),
                            lineHeight = 18.sp,
                        )
                    }
                }
                responseBody != null -> {
                    val highlighted = remember(responseBody) {
                        highlightJson(responseBody)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Line numbers
                            val lines = responseBody.lines()
                            Column(
                                modifier = Modifier
                                    .width(40.dp)
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.End,
                            ) {
                                lines.forEachIndexed { idx, _ ->
                                    Text(
                                        text = "${idx + 1}",
                                        fontSize = 12.sp,
                                        fontFamily = CodeFontFamily,
                                        color = ReqLabColors.OnSurfaceDim.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(end = 8.dp),
                                        lineHeight = 18.sp,
                                    )
                                }
                            }

                            // Divider
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxSize()
                                    .background(ReqLabColors.Border),
                            )

                            // Highlighted body
                            Text(
                                text = highlighted,
                                fontSize = 12.sp,
                                fontFamily = CodeFontFamily,
                                lineHeight = 18.sp,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(8.dp)
                                    .testTag("graphql-response-body"),
                            )
                        }
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "No response yet",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ReqLabColors.OnSurfaceDim,
                            )
                            Text(
                                text = "Enter a query and click Execute to see results.",
                                fontSize = 12.sp,
                                color = ReqLabColors.OnSurfaceDim.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────

/** Best-effort JSON pretty-print. Returns original on failure. */
private fun formatJsonSafe(text: String): String {
    return try {
        val element = Json.parseToJsonElement(text)
        prettyJson.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), element)
    } catch (_: Exception) {
        text
    }
}

/** Simple GraphQL formatter — normalises indentation. */
private fun formatGraphQL(query: String): String {
    val sb = StringBuilder()
    var indent = 0
    val tokens = query.replace("{", " {\n").replace("}", "\n}\n")
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    for (token in tokens) {
        if (token.startsWith("}")) indent = (indent - 1).coerceAtLeast(0)
        sb.appendLine("  ".repeat(indent) + token)
        if (token.endsWith("{")) indent++
    }
    return sb.toString().trimEnd() + "\n"
}
