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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.core.model.KeyValueEntry
import com.reqlab.core.model.McpConnectionState
import com.reqlab.core.model.McpHttpMode
import com.reqlab.core.model.McpLogEntry
import com.reqlab.core.model.McpLogEntryKind
import com.reqlab.core.model.McpPrompt
import com.reqlab.core.model.McpResource
import com.reqlab.core.model.McpTool
import com.reqlab.core.model.McpTransportType
import com.reqlab.ui.shared.i18n.Strings
import com.reqlab.ui.shared.mcp.mcpArgsGet
import com.reqlab.ui.shared.mcp.mcpArgsPut
import com.reqlab.ui.shared.mcp.mcpDefaultArgsJson
import com.reqlab.ui.shared.mcp.mcpMissingRequiredArgs
import com.reqlab.ui.shared.mcp.mcpParseScalar
import com.reqlab.ui.shared.mcp.mcpPrettyJson
import com.reqlab.ui.shared.mcp.mcpPrettyWireJson
import com.reqlab.ui.shared.mcp.mcpPromptSchema
import com.reqlab.ui.shared.mcp.mcpSchemaFields
import com.reqlab.ui.shared.mcp.mcpSchemaFormSupported
import com.reqlab.ui.shared.mcp.mcpToolHintChips
import com.reqlab.ui.shared.platform.PlatformColumnVerticalScrollbar
import com.reqlab.ui.shared.platform.PlatformLazyVerticalScrollbar
import com.reqlab.ui.shared.platform.copyToClipboard
import com.reqlab.ui.shared.platform.insetScrollbar
import com.reqlab.ui.shared.platform.formatTimestamp
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.MutableKeyValue
import com.reqlab.ui.shared.state.RequestTabState
import com.reqlab.ui.shared.state.ResponseTab
import com.reqlab.ui.shared.theme.CodeFontFamily
import com.reqlab.ui.shared.theme.ReqLabColors
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val SECTION_TOOLS = 0
private const val SECTION_RESOURCES = 1
private const val SECTION_PROMPTS = 2
private const val SECTION_AUTH = 3
private const val SECTION_HEADERS = 4
private const val SECTION_PARAMS = 5
private const val SECTION_ACTIVITY = 6
private const val SECTION_CLIENT = 7

@Composable
fun McpPanel(state: AppState, tab: RequestTabState) {
    val session = remember(tab.id) { state.getOrCreateMcpSession(tab.id) }
    val connection by session.connectionState.collectAsState()
    val tools by session.tools.collectAsState()
    val resources by session.resources.collectAsState()
    val prompts by session.prompts.collectAsState()
    val logs by session.logs.collectAsState()
    val subscribed by session.subscribedUris.collectAsState()
    val lastOperation by session.lastOperation.collectAsState()
    val busy by session.busy.collectAsState()
    val error by session.error.collectAsState()

    var section by remember { mutableIntStateOf(0) }
    var toolArgs by remember { mutableStateOf("{}") }
    var selectedTool by remember { mutableStateOf<String?>(null) }
    var selectedResource by remember { mutableStateOf<String?>(null) }
    var selectedPrompt by remember { mutableStateOf<String?>(null) }
    var promptArgs by remember { mutableStateOf("{}") }
    var showStdioConfirm by remember { mutableStateOf(false) }
    val lastToolArgs = remember(tab.id) { mutableStateMapOf<String, String>() }
    val lastPromptArgs = remember(tab.id) { mutableStateMapOf<String, String>() }
    var listSplit by remember { mutableFloatStateOf(0.32f) }

    val headerRows = remember(tab.id) {
        mutableStateListOf<MutableKeyValue>().apply {
            addAll(tab.mcpConfig.headers.map { MutableKeyValue(it.key, it.value, it.enabled, it.secret) })
        }
    }
    fun persistHeaders() {
        tab.mcpConfig = tab.mcpConfig.copy(
            headers = headerRows.map { KeyValueEntry(it.key, it.value, it.enabled, it.secret) },
        )
        tab.markDirty()
    }

    fun liveConfig() = tab.mcpConfig.copy(
        url = tab.url.ifBlank { tab.mcpConfig.url },
        auth = buildAuthConfig(tab),
        headers = headerRows.map { KeyValueEntry(it.key, it.value, it.enabled, it.secret) },
    )

    val errorLabel = Strings.t("mcp_tool_error")
    val okLabel = Strings.t("mcp_tool_ok")
    LaunchedEffect(lastOperation) {
        val op = lastOperation ?: return@LaunchedEffect
        tab.response = op.toResponseDefinition(tab.id, okStatusText = okLabel, errorStatusText = errorLabel)
        tab.responseTab = ResponseTab.BODY
        tab.lastError = if (op.isError) error else null
    }
    LaunchedEffect(busy) { tab.isLoading = busy }
    LaunchedEffect(error) {
        if (lastOperation?.isError != true) tab.lastError = error
    }
    LaunchedEffect(connection) {
        if (connection == McpConnectionState.DISCONNECTED) {
            tab.response = null
            tab.lastError = null
            tab.isLoading = false
        }
    }

    LaunchedEffect(connection, tools) {
        if (connection != McpConnectionState.CONNECTED || tools.isEmpty()) return@LaunchedEffect
        val preferred = selectedTool?.takeIf { name -> tools.any { it.name == name } } ?: tools.first().name
        if (selectedTool != preferred) {
            selectedTool = preferred
            toolArgs = lastToolArgs[preferred] ?: mcpDefaultArgsJson(tools.first { it.name == preferred }.inputSchema)
        }
    }
    LaunchedEffect(connection, prompts) {
        if (connection != McpConnectionState.CONNECTED || prompts.isEmpty()) return@LaunchedEffect
        val preferred = selectedPrompt?.takeIf { name -> prompts.any { it.name == name } } ?: prompts.first().name
        if (selectedPrompt != preferred) {
            selectedPrompt = preferred
            promptArgs = lastPromptArgs[preferred] ?: defaultPromptArgsJson(prompts.first { it.name == preferred })
        }
    }

    fun connectNow() {
        syncUrlFromParams(tab)
        val url = tab.url.ifBlank { tab.mcpConfig.url }
        tab.url = url
        persistHeaders()
        tab.mcpConfig = tab.mcpConfig.copy(url = url, auth = buildAuthConfig(tab))
        val cfg = tab.mcpConfig
        state.appScope.launch { runCatching { session.connect(cfg, state.activeVariableLayers()) } }
    }

    fun requestConnect() {
        if (tab.mcpConfig.transport == McpTransportType.STDIO && !session.confirmStdio) {
            showStdioConfirm = true
        } else {
            connectNow()
        }
    }

    val selectedToolSchema = tools.firstOrNull { it.name == selectedTool }?.inputSchema
    val selectedPromptModel = prompts.firstOrNull { it.name == selectedPrompt }
    LaunchedEffect(section, selectedTool, selectedPrompt, selectedResource, toolArgs, promptArgs, busy, connection) {
        session.pendingShortcut = {
            when {
                busy -> session.cancelCall()
                connection != McpConnectionState.CONNECTED -> Unit
                section == SECTION_TOOLS && selectedTool != null &&
                    mcpMissingRequiredArgs(selectedToolSchema ?: JsonObject(emptyMap()), toolArgs).isEmpty() -> {
                    val args = runCatching { mcpPrettyJson.parseToJsonElement(toolArgs) }.getOrNull() as? JsonObject
                    session.launchCall { session.callSelectedTool(selectedTool!!, args) }
                }
                section == SECTION_RESOURCES && selectedResource != null -> {
                    session.launchCall { session.readSelectedResource(selectedResource!!) }
                }
                section == SECTION_PROMPTS && selectedPrompt != null && selectedPromptModel != null &&
                    mcpMissingRequiredArgs(mcpPromptSchema(selectedPromptModel), promptArgs).isEmpty() -> {
                    val parsed = runCatching { mcpPrettyJson.parseToJsonElement(promptArgs) }.getOrNull() as? JsonObject
                    val map = parsed?.mapNotNull { (k, v) ->
                        (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
                    }?.toMap().orEmpty()
                    session.launchCall { session.getSelectedPrompt(selectedPrompt!!, map) }
                }
            }
        }
    }

    val reconnectNeeded = session.isReconnectNeeded(liveConfig())
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReqLabColors.Background)
            .padding(12.dp)
            .testTag("mcp-panel"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ConnectionBar(
            tab = tab,
            state = state,
            session = session,
            connection = connection,
            reconnectNeeded = reconnectNeeded,
            showStdioConfirm = showStdioConfirm,
            onShowStdioConfirm = { showStdioConfirm = it },
            onConnect = { requestConnect() },
            onDisconnect = { state.appScope.launch { session.disconnect() } },
            onReconnect = {
                state.appScope.launch {
                    session.disconnect()
                    requestConnect()
                }
            },
            onConfirmStdio = {
                session.confirmStdio = true
                showStdioConfirm = false
                connectNow()
            },
        )
        error?.let { Text(it, color = ReqLabColors.Error, fontSize = 12.sp, modifier = Modifier.testTag("mcp-error")) }

        val tabLabels = listOf(
            "${Strings.t("mcp_tools")}${countSuffix(tools.size)}",
            "${Strings.t("mcp_resources")}${countSuffix(resources.size)}",
            "${Strings.t("mcp_prompts")}${countSuffix(prompts.size)}",
            Strings.t("auth"),
            "${Strings.t("headers")}${countSuffix(headerRows.size)}",
            "${Strings.t("params")}${countSuffix(tab.params.size)}",
            Strings.t("mcp_activity"),
            Strings.t("mcp_client"),
        )
        TabRow(
            selectedTabIndex = section,
            modifier = Modifier.fillMaxWidth().testTag("mcp-tabs"),
            containerColor = ReqLabColors.Background,
        ) {
            tabLabels.forEachIndexed { index, title ->
                Tab(
                    selected = section == index,
                    onClick = { section = index },
                    text = {
                        Text(title, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val connected = connection == McpConnectionState.CONNECTED
            when (section) {
                SECTION_TOOLS -> ToolsSection(
                    state = state,
                    connected = connected,
                    tools = tools,
                    selected = selectedTool,
                    args = toolArgs,
                    busy = busy,
                    listSplit = listSplit,
                    onListSplitChanged = { listSplit = it },
                    onSelect = { tool ->
                        selectedTool?.let { lastToolArgs[it] = toolArgs }
                        selectedTool = tool.name
                        toolArgs = lastToolArgs[tool.name] ?: mcpDefaultArgsJson(tool.inputSchema)
                    },
                    onArgsChange = { next ->
                        toolArgs = next
                        selectedTool?.let { lastToolArgs[it] = next }
                    },
                    onRun = { name ->
                        val args = runCatching { mcpPrettyJson.parseToJsonElement(toolArgs) }.getOrNull() as? JsonObject
                        session.launchCall { session.callSelectedTool(name, args) }
                    },
                    onStop = { session.cancelCall() },
                )
                SECTION_RESOURCES -> ResourcesSection(
                    connected = connected,
                    resources = resources,
                    selected = selectedResource,
                    subscribed = subscribed,
                    supportsSubscribe = session.supportsSubscribe(),
                    busy = busy,
                    listSplit = listSplit,
                    onListSplitChanged = { listSplit = it },
                    onSelect = { selectedResource = it.uri },
                    onRead = { uri -> session.launchCall { session.readSelectedResource(uri) } },
                    onSubscribe = { uri -> session.launchCall { session.subscribeResource(uri) } },
                    onUnsubscribe = { uri -> session.launchCall { session.unsubscribeResource(uri) } },
                    onStop = { session.cancelCall() },
                )
                SECTION_PROMPTS -> PromptsSection(
                    state = state,
                    connected = connected,
                    prompts = prompts,
                    selected = selectedPrompt,
                    args = promptArgs,
                    busy = busy,
                    listSplit = listSplit,
                    onListSplitChanged = { listSplit = it },
                    onSelect = { prompt ->
                        selectedPrompt?.let { lastPromptArgs[it] = promptArgs }
                        selectedPrompt = prompt.name
                        promptArgs = lastPromptArgs[prompt.name] ?: defaultPromptArgsJson(prompt)
                    },
                    onArgsChange = { next ->
                        promptArgs = next
                        selectedPrompt?.let { lastPromptArgs[it] = next }
                    },
                    onGet = { name ->
                        val parsed = runCatching { mcpPrettyJson.parseToJsonElement(promptArgs) }.getOrNull() as? JsonObject
                        val map = parsed?.mapNotNull { (k, v) ->
                            (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
                        }?.toMap().orEmpty()
                        session.launchCall { session.getSelectedPrompt(name, map) }
                    },
                    onStop = { session.cancelCall() },
                )
                SECTION_AUTH -> AuthEditor(tab, state) {
                    tab.mcpConfig = tab.mcpConfig.copy(auth = buildAuthConfig(tab))
                    tab.markDirty()
                }
                SECTION_HEADERS -> KeyValueEditor(headerRows, "header", state) { persistHeaders() }
                SECTION_PARAMS -> KeyValueEditor(tab.params, "param", state) {
                    syncUrlFromParams(tab)
                    tab.mcpConfig = tab.mcpConfig.copy(url = tab.url)
                    tab.markDirty()
                }
                SECTION_ACTIVITY -> ActivitySection(logs)
                else -> ClientSection(tab, session)
            }
        }
    }
}

@Composable
private fun ConnectionBar(
    tab: RequestTabState,
    state: AppState,
    session: com.reqlab.ui.shared.mcp.McpSessionState,
    connection: McpConnectionState,
    reconnectNeeded: Boolean,
    showStdioConfirm: Boolean,
    onShowStdioConfirm: (Boolean) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    onConfirmStdio: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusDot(connection)
            Text(
                when (connection) {
                    McpConnectionState.CONNECTED -> Strings.t("mcp_connected")
                    McpConnectionState.CONNECTING -> Strings.t("mcp_connecting")
                    McpConnectionState.ERROR -> Strings.t("mcp_status_error")
                    McpConnectionState.DISCONNECTED -> Strings.t("mcp_disconnected")
                },
                color = ReqLabColors.OnSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            val negotiated = session.negotiatedLabel()
            if (negotiated.isNotBlank()) {
                Text(negotiated, color = ReqLabColors.OnSurfaceDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
            session.sessionId()?.let { sid ->
                val short = if (sid.length > 16) sid.take(10) + "…" else sid
                SelectionContainer {
                    Text(
                        "${Strings.t("mcp_session_id")} $short",
                        color = ReqLabColors.OnSurfaceDim,
                        fontSize = 11.sp,
                        fontFamily = CodeFontFamily,
                        maxLines = 1,
                        modifier = Modifier.testTag("mcp-session-id"),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (tab.mcpConfig.transport == McpTransportType.STDIO) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ReqLabColors.Surface)
                        .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    VariableAwareTextField(
                        value = tab.mcpConfig.command,
                        onValueChange = { tab.mcpConfig = tab.mcpConfig.copy(command = it); tab.markDirty() },
                        placeholder = Strings.t("mcp_command"),
                        state = state,
                        modifier = Modifier.fillMaxWidth().testTag("mcp-command"),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ReqLabColors.Surface)
                        .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    VariableAwareTextField(
                        value = tab.mcpConfig.url.ifBlank { tab.url },
                        onValueChange = {
                            tab.mcpConfig = tab.mcpConfig.copy(url = it)
                            tab.url = it
                            syncParamsFromUrl(tab, it)
                            tab.markDirty()
                        },
                        placeholder = Strings.t("mcp_url"),
                        state = state,
                        undoStack = tab.urlUndoStack,
                        modifier = Modifier.fillMaxWidth().testTag("mcp-url"),
                    )
                }
            }
            ConnectionActionButton(
                connection = connection,
                reconnectNeeded = reconnectNeeded,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
                onReconnect = onReconnect,
            )
        }
        if (showStdioConfirm) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Strings.t("mcp_stdio_confirm"), color = ReqLabColors.OnSurface, modifier = Modifier.weight(1f))
                TextButton(onClick = onConfirmStdio) { Text(Strings.confirm) }
                TextButton(onClick = { onShowStdioConfirm(false) }) { Text(Strings.cancel) }
            }
        }
    }
}

@Composable
private fun ConnectionActionButton(
    connection: McpConnectionState,
    reconnectNeeded: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
) {
    when {
        connection == McpConnectionState.CONNECTING -> {
            Button(onClick = {}, enabled = false, modifier = Modifier.testTag("mcp-connect")) {
                Text(Strings.t("mcp_connecting"))
            }
        }
        connection == McpConnectionState.CONNECTED && !reconnectNeeded -> {
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.testTag("mcp-disconnect")) {
                Text(Strings.disconnect)
            }
        }
        connection == McpConnectionState.ERROR || reconnectNeeded -> {
            Button(onClick = onReconnect, modifier = Modifier.testTag("mcp-reconnect")) {
                Text(Strings.t("mcp_reconnect"))
            }
        }
        else -> {
            Button(onClick = onConnect, modifier = Modifier.testTag("mcp-connect")) {
                Text(Strings.connect)
            }
        }
    }
}

@Composable
private fun ToolsSection(
    state: AppState,
    connected: Boolean,
    tools: List<McpTool>,
    selected: String?,
    args: String,
    busy: Boolean,
    listSplit: Float,
    onListSplitChanged: (Float) -> Unit,
    onSelect: (McpTool) -> Unit,
    onArgsChange: (String) -> Unit,
    onRun: (String) -> Unit,
    onStop: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!connected) {
            EmptyState(Strings.t("mcp_connect_hint"))
            return@Column
        }
        if (tools.isEmpty()) {
            EmptyState(Strings.t("mcp_no_tools"))
            return@Column
        }
        var query by remember { mutableStateOf("") }
        val filtered = remember(tools, query) {
            val q = query.trim()
            if (q.isEmpty()) tools
            else tools.filter { it.name.contains(q, ignoreCase = true) || it.description.orEmpty().contains(q, ignoreCase = true) }
        }
        SplitCard(Modifier.weight(1f).fillMaxWidth()) {
        HorizontalSplitPane(
            modifier = Modifier.fillMaxSize(),
            splitFraction = listSplit,
            onSplitChanged = onListSplitChanged,
            minFraction = 0.18f,
            maxFraction = 0.55f,
            dividerTag = "mcp-tools-split",
            hairline = true,
            first = {
                SplitColumn {
                    SearchField(query, { query = it }, Strings.t("mcp_search_tools"), "mcp-search-tools")
                    ScrollableLazyColumn(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        listTestTag = "mcp-tool-list",
                        scrollbarTag = "mcp-tool-list-vscrollbar",
                    ) {
                        items(filtered, key = { it.name }) { tool ->
                            ListRow(
                                title = tool.name,
                                subtitle = tool.description,
                                active = tool.name == selected,
                                onClick = { onSelect(tool) },
                            )
                        }
                    }
                }
            },
            second = {
                SplitColumn {
                    val tool = tools.firstOrNull { it.name == selected }
                    if (tool == null) {
                        EmptyState(Strings.t("mcp_select_tool"))
                        return@SplitColumn
                    }
                    val missing = mcpMissingRequiredArgs(tool.inputSchema, args)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(tool.name, color = ReqLabColors.OnSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        mcpToolHintChips(tool.annotations).forEach { chip ->
                            val label = if (chip == "readOnly") Strings.t("mcp_readonly") else Strings.t("mcp_destructive")
                            AnnotationChip(label, destructive = chip == "destructive")
                        }
                        RunButton(
                            busy = busy,
                            enabled = missing.isEmpty(),
                            label = Strings.t("mcp_run"),
                            testTag = "mcp-run",
                            onClick = { onRun(tool.name) },
                            onStop = onStop,
                        )
                    }
                    SchemaArgsEditor(
                        state = state,
                        schema = tool.inputSchema,
                        args = args,
                        onArgsChange = onArgsChange,
                        testTagPrefix = "mcp-tool-args",
                    )
                }
            },
        )
        }
    }
}

@Composable
private fun ResourcesSection(
    connected: Boolean,
    resources: List<McpResource>,
    selected: String?,
    subscribed: Set<String>,
    supportsSubscribe: Boolean,
    busy: Boolean,
    listSplit: Float,
    onListSplitChanged: (Float) -> Unit,
    onSelect: (McpResource) -> Unit,
    onRead: (String) -> Unit,
    onSubscribe: (String) -> Unit,
    onUnsubscribe: (String) -> Unit,
    onStop: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!connected) {
            EmptyState(Strings.t("mcp_connect_hint"))
            return@Column
        }
        if (resources.isEmpty()) {
            EmptyState(Strings.t("mcp_no_resources"))
            return@Column
        }
        var query by remember { mutableStateOf("") }
        val filtered = remember(resources, query) {
            val q = query.trim()
            if (q.isEmpty()) resources
            else resources.filter { it.name.contains(q, ignoreCase = true) || it.uri.contains(q, ignoreCase = true) }
        }
        SplitCard(Modifier.weight(1f).fillMaxWidth()) {
        HorizontalSplitPane(
            modifier = Modifier.fillMaxSize(),
            splitFraction = listSplit,
            onSplitChanged = onListSplitChanged,
            minFraction = 0.18f,
            maxFraction = 0.55f,
            dividerTag = "mcp-resources-split",
            hairline = true,
            first = {
                SplitColumn {
                    SearchField(query, { query = it }, Strings.t("mcp_search_resources"), "mcp-search-resources")
                    ScrollableLazyColumn(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        listTestTag = "mcp-resource-list",
                        scrollbarTag = "mcp-resource-list-vscrollbar",
                    ) {
                        items(filtered, key = { it.uri }) { res ->
                            ListRow(
                                title = res.name,
                                subtitle = res.uri,
                                active = res.uri == selected,
                                trailing = if (res.uri in subscribed) Strings.t("mcp_subscribed") else null,
                                onClick = { onSelect(res) },
                            )
                        }
                    }
                }
            },
            second = {
                SplitColumn {
                    val res = resources.firstOrNull { it.uri == selected }
                    if (res == null) {
                        EmptyState(Strings.t("mcp_resource_empty"))
                        return@SplitColumn
                    }
                    Text(res.name, color = ReqLabColors.OnSurface, fontWeight = FontWeight.SemiBold)
                    Text(res.uri, color = ReqLabColors.OnSurfaceDim, fontSize = 11.sp, fontFamily = CodeFontFamily, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RunButton(
                            busy = busy,
                            enabled = true,
                            label = Strings.t("mcp_read_resource"),
                            testTag = "mcp-read",
                            onClick = { onRead(res.uri) },
                            onStop = onStop,
                        )
                        if (supportsSubscribe) {
                            if (res.uri in subscribed) {
                                OutlinedButton(onClick = { onUnsubscribe(res.uri) }, enabled = !busy, modifier = Modifier.testTag("mcp-unsubscribe")) {
                                    Text(Strings.t("mcp_unsubscribe"))
                                }
                            } else {
                                OutlinedButton(onClick = { onSubscribe(res.uri) }, enabled = !busy, modifier = Modifier.testTag("mcp-subscribe")) {
                                    Text(Strings.t("mcp_subscribe"))
                                }
                            }
                        }
                    }
                }
            },
        )
        }
    }
}

@Composable
private fun PromptsSection(
    state: AppState,
    connected: Boolean,
    prompts: List<McpPrompt>,
    selected: String?,
    args: String,
    busy: Boolean,
    listSplit: Float,
    onListSplitChanged: (Float) -> Unit,
    onSelect: (McpPrompt) -> Unit,
    onArgsChange: (String) -> Unit,
    onGet: (String) -> Unit,
    onStop: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!connected) {
            EmptyState(Strings.t("mcp_connect_hint"))
            return@Column
        }
        if (prompts.isEmpty()) {
            EmptyState(Strings.t("mcp_no_prompts"))
            return@Column
        }
        var query by remember { mutableStateOf("") }
        val filtered = remember(prompts, query) {
            val q = query.trim()
            if (q.isEmpty()) prompts
            else prompts.filter { it.name.contains(q, ignoreCase = true) || it.description.orEmpty().contains(q, ignoreCase = true) }
        }
        SplitCard(Modifier.weight(1f).fillMaxWidth()) {
        HorizontalSplitPane(
            modifier = Modifier.fillMaxSize(),
            splitFraction = listSplit,
            onSplitChanged = onListSplitChanged,
            minFraction = 0.18f,
            maxFraction = 0.55f,
            dividerTag = "mcp-prompts-split",
            hairline = true,
            first = {
                SplitColumn {
                    SearchField(query, { query = it }, Strings.t("mcp_search_prompts"), "mcp-search-prompts")
                    ScrollableLazyColumn(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        listTestTag = "mcp-prompt-list",
                        scrollbarTag = "mcp-prompt-list-vscrollbar",
                    ) {
                        items(filtered, key = { it.name }) { prompt ->
                            ListRow(
                                title = prompt.name,
                                subtitle = prompt.description,
                                active = prompt.name == selected,
                                onClick = { onSelect(prompt) },
                            )
                        }
                    }
                }
            },
            second = {
                SplitColumn {
                    val prompt = prompts.firstOrNull { it.name == selected }
                    if (prompt == null) {
                        EmptyState(Strings.t("mcp_prompt_empty"))
                        return@SplitColumn
                    }
                    val schema = mcpPromptSchema(prompt)
                    val missing = mcpMissingRequiredArgs(schema, args)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(prompt.name, color = ReqLabColors.OnSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        RunButton(
                            busy = busy,
                            enabled = missing.isEmpty(),
                            label = Strings.t("mcp_get_prompt"),
                            testTag = "mcp-get-prompt",
                            onClick = { onGet(prompt.name) },
                            onStop = onStop,
                        )
                    }
                    SchemaArgsEditor(
                        state = state,
                        schema = schema,
                        args = args,
                        onArgsChange = onArgsChange,
                        testTagPrefix = "mcp-prompt-args",
                    )
                }
            },
        )
        }
    }
}

@Composable
private fun SchemaArgsEditor(
    state: AppState,
    schema: JsonObject,
    args: String,
    onArgsChange: (String) -> Unit,
    testTagPrefix: String,
) {
    val formSupported = mcpSchemaFormSupported(schema)
    var preferForm by remember { mutableStateOf(true) }
    val showForm = formSupported && preferForm
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (formSupported) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TransportChip(Strings.t("mcp_form"), showForm) { preferForm = true }
                TransportChip(Strings.t("mcp_json"), !showForm) { preferForm = false }
            }
        }
        if (showForm) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                mcpSchemaFields(schema).forEach { field ->
                    val label = buildString {
                        append(field.title ?: field.name)
                        if (field.required) append(" *")
                    }
                    Text(label, color = ReqLabColors.OnSurfaceDim, fontSize = 11.sp)
                    when {
                        field.enumValues.isNotEmpty() -> {
                            val current = (mcpArgsGet(args, field.name) as? JsonPrimitive)?.content.orEmpty()
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                field.enumValues.forEach { opt ->
                                    TransportChip(opt, current == opt) {
                                        onArgsChange(mcpArgsPut(args, field.name, JsonPrimitive(opt)))
                                    }
                                }
                            }
                        }
                        field.type == "boolean" -> {
                            val checked = (mcpArgsGet(args, field.name) as? JsonPrimitive)?.content == "true"
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { onArgsChange(mcpArgsPut(args, field.name, JsonPrimitive(it))) },
                                )
                                Text(field.name, color = ReqLabColors.OnSurface, fontSize = 13.sp)
                            }
                        }
                        else -> {
                            val current = (mcpArgsGet(args, field.name) as? JsonPrimitive)?.content.orEmpty()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ReqLabColors.Surface)
                                    .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                VariableAwareTextField(
                                    value = current,
                                    onValueChange = {
                                        onArgsChange(mcpArgsPut(args, field.name, mcpParseScalar(it, field.type)))
                                    },
                                    placeholder = field.name,
                                    state = state,
                                    modifier = Modifier.fillMaxWidth().testTag("$testTagPrefix-${field.name}"),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp))) {
                CodeEditor(
                    text = args,
                    onTextChange = onArgsChange,
                    language = SyntaxLanguage.JSON,
                    modifier = Modifier.fillMaxSize(),
                    enableDownload = false,
                    testTagPrefix = testTagPrefix,
                )
            }
        }
    }
}

@Composable
private fun ActivitySection(logs: List<McpLogEntry>) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (logs.isEmpty()) {
            EmptyState(Strings.t("mcp_activity_empty"))
            return@Column
        }
        ScrollableLazyColumn(
            modifier = Modifier.weight(1f),
            listTestTag = "mcp-log",
            scrollbarTag = "mcp-activity-vscrollbar",
        ) {
            items(logs.size) { i ->
                ActivityRow(logs[logs.size - 1 - i])
                HorizontalDivider(color = ReqLabColors.BorderLight)
            }
        }
    }
}

@Composable
private fun ActivityRow(entry: McpLogEntry) {
    var expanded by remember(entry) { mutableStateOf(false) }
    val hasPayload = !entry.payload.isNullOrBlank()
    val pretty = if (hasPayload) prettyPayload(entry.payload.orEmpty()) else ""
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = hasPayload) { expanded = !expanded },
        ) {
            Text(formatTimestamp(entry.timestampEpochMillis), color = ReqLabColors.OnSurfaceDim, fontSize = 11.sp, fontFamily = CodeFontFamily)
            Text(
                entry.kind.name,
                color = activityColor(entry.kind),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, activityColor(entry.kind), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
            Text(entry.summary, color = ReqLabColors.OnSurface, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            entry.id?.let { Text("#$it", color = ReqLabColors.OnSurfaceDim, fontSize = 10.sp, fontFamily = CodeFontFamily) }
            if (hasPayload) {
                IconButton(
                    onClick = { copyToClipboard(pretty) },
                    modifier = Modifier.size(28.dp).testTag("mcp-activity-copy"),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = Strings.copy,
                        tint = ReqLabColors.OnSurfaceDim,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        if (expanded && hasPayload) {
            SelectionContainer {
                Text(
                    pretty,
                    color = ReqLabColors.OnSurfaceDim,
                    fontSize = 11.sp,
                    fontFamily = CodeFontFamily,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(ReqLabColors.SurfaceContainer)
                        .padding(8.dp)
                        .testTag("mcp-activity-payload"),
                )
            }
        } else if (hasPayload) {
            Text(Strings.t("mcp_activity_expand"), color = ReqLabColors.OnSurfaceDim, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ClientSection(tab: RequestTabState, session: com.reqlab.ui.shared.mcp.McpSessionState) {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(Strings.t("mcp_transport"), color = ReqLabColors.OnSurface, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportChip("HTTP", tab.mcpConfig.transport == McpTransportType.STREAMABLE_HTTP) {
                tab.mcpConfig = tab.mcpConfig.copy(transport = McpTransportType.STREAMABLE_HTTP)
                tab.markDirty()
            }
            if (session.stdioAvailable()) {
                TransportChip("stdio", tab.mcpConfig.transport == McpTransportType.STDIO) {
                    tab.mcpConfig = tab.mcpConfig.copy(transport = McpTransportType.STDIO)
                    tab.markDirty()
                }
            }
        }
        Text(Strings.t("mcp_http_mode"), color = ReqLabColors.OnSurface, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportChip("Auto", tab.mcpConfig.httpMode == McpHttpMode.AUTO) {
                tab.mcpConfig = tab.mcpConfig.copy(httpMode = McpHttpMode.AUTO); tab.markDirty()
            }
            TransportChip("2025-06-18", tab.mcpConfig.httpMode == McpHttpMode.STREAMABLE_2025_06_18) {
                tab.mcpConfig = tab.mcpConfig.copy(httpMode = McpHttpMode.STREAMABLE_2025_06_18); tab.markDirty()
            }
            TransportChip("Legacy", tab.mcpConfig.httpMode == McpHttpMode.LEGACY_2024_11_05) {
                tab.mcpConfig = tab.mcpConfig.copy(httpMode = McpHttpMode.LEGACY_2024_11_05); tab.markDirty()
            }
        }
        Text(Strings.t("mcp_sampling_mode"), color = ReqLabColors.OnSurface, fontWeight = FontWeight.SemiBold)
        Text("${tab.mcpConfig.samplingMode}", color = ReqLabColors.OnSurface, fontFamily = CodeFontFamily, fontSize = 13.sp)
        Text(Strings.t("mcp_roots"), color = ReqLabColors.OnSurface, fontWeight = FontWeight.SemiBold)
        if (tab.mcpConfig.roots.isEmpty()) {
            Text("\u2014", color = ReqLabColors.OnSurfaceDim)
        } else {
            tab.mcpConfig.roots.forEach { root ->
                Text(root.uri, color = ReqLabColors.OnSurfaceDim, fontFamily = CodeFontFamily, fontSize = 12.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = tab.mcpConfig.autoRespondElicitation, onCheckedChange = {
                tab.mcpConfig = tab.mcpConfig.copy(autoRespondElicitation = it)
                tab.markDirty()
            })
            Text(Strings.t("mcp_auto_elicit"), color = ReqLabColors.OnSurface)
        }
    }
        PlatformColumnVerticalScrollbar(
            scrollState = scroll,
            modifier = Modifier.align(Alignment.CenterEnd).insetScrollbar(),
            testTag = "mcp-client-vscrollbar",
        )
    }
}

@Composable
private fun ScrollableLazyColumn(
    modifier: Modifier,
    listTestTag: String,
    scrollbarTag: String,
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    Box(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(end = 10.dp).testTag(listTestTag),
            content = content,
        )
        PlatformLazyVerticalScrollbar(
            listState = listState,
            modifier = Modifier.align(Alignment.CenterEnd).insetScrollbar(),
            testTag = scrollbarTag,
        )
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, testTag: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(ReqLabColors.Background)
            .border(1.dp, ReqLabColors.Border, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        VariableAwareTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            modifier = Modifier.fillMaxWidth().testTag(testTag),
        )
    }
}

@Composable
private fun ListRow(
    title: String,
    subtitle: String?,
    active: Boolean,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) ReqLabColors.Primary.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = ReqLabColors.OnSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            trailing?.let {
                Text(it, color = Color(0xFF22C55E), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                color = ReqLabColors.OnSurfaceDim,
                fontSize = 11.sp,
                fontFamily = CodeFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RunButton(
    busy: Boolean,
    enabled: Boolean,
    label: String,
    testTag: String,
    onClick: () -> Unit,
    onStop: () -> Unit,
) {
    if (busy) {
        OutlinedButton(onClick = onStop, modifier = Modifier.testTag("$testTag-stop")) {
            Text(Strings.t("stop"))
        }
    } else {
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.testTag(testTag)) {
            Text(label)
        }
    }
}

@Composable
private fun AnnotationChip(label: String, destructive: Boolean) {
    Text(
        label,
        color = if (destructive) ReqLabColors.Error else ReqLabColors.OnSurfaceDim,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, if (destructive) ReqLabColors.Error else ReqLabColors.Border, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun EmptyState(text: String) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp)).padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = ReqLabColors.OnSurfaceDim, fontSize = 13.sp)
    }
}

@Composable
private fun SplitCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ReqLabColors.Surface)
            .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp)),
    ) {
        content()
    }
}

@Composable
private fun SplitColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun StatusDot(state: McpConnectionState) {
    val color = when (state) {
        McpConnectionState.CONNECTED -> Color(0xFF22C55E)
        McpConnectionState.CONNECTING -> Color(0xFFEAB308)
        McpConnectionState.ERROR -> ReqLabColors.Error
        McpConnectionState.DISCONNECTED -> ReqLabColors.OnSurfaceDim
    }
    Box(Modifier.size(10.dp).clip(CircleShape).background(color).testTag("mcp-status-${state.name}"))
}

@Composable
private fun TransportChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) ReqLabColors.Primary else ReqLabColors.OnSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (selected) ReqLabColors.Primary else ReqLabColors.OnSurfaceDim, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        fontSize = 12.sp,
    )
}

@Composable
private fun activityColor(kind: McpLogEntryKind): Color = when (kind) {
    McpLogEntryKind.SENT -> ReqLabColors.Primary
    McpLogEntryKind.RECEIVED -> Color(0xFF22C55E)
    McpLogEntryKind.NOTIFICATION -> ReqLabColors.Tertiary
    McpLogEntryKind.ERROR -> ReqLabColors.Error
    McpLogEntryKind.STATE, McpLogEntryKind.OAUTH -> ReqLabColors.OnSurfaceDim
}

private fun prettyPayload(payload: String): String = mcpPrettyWireJson(payload)

private fun countSuffix(n: Int): String = if (n > 0) " ($n)" else ""

private fun defaultPromptArgsJson(prompt: McpPrompt): String {
    if (prompt.arguments.isEmpty()) return "{}"
    return mcpDefaultArgsJson(mcpPromptSchema(prompt))
}
