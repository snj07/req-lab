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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.core.model.MessageDirection
import com.reqlab.ui.shared.i18n.Strings
import com.reqlab.core.model.RealtimeMessage
import com.reqlab.core.model.RealtimeProtocol
import com.reqlab.core.network.ConnectionState
import com.reqlab.core.network.RealtimeClient
import com.reqlab.ui.shared.theme.CodeFontFamily
import com.reqlab.ui.shared.theme.ReqLabColors
import kotlinx.coroutines.launch

// ── Realtime Tab ────────────────────────────────────────────────

private enum class RealtimeTab(val label: String) {
    COMMUNICATION("Communication"),
    PROTOCOLS("Protocols"),
}

/**
 * Full realtime API testing panel.
 * Supports WebSocket, SSE, Socket.IO, and MQTT protocols.
 */
@Composable
fun RealtimePanel() {
    val scope = rememberCoroutineScope()
    val client = remember { RealtimeClient(scope) }

    var selectedProtocol by remember { mutableStateOf(RealtimeProtocol.WEBSOCKET) }
    var url by remember { mutableStateOf(selectedProtocol.defaultUrl) }
    var connectionState by remember { mutableStateOf(ConnectionState.DISCONNECTED) }
    var messageInput by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<RealtimeMessage>() }
    var selectedTab by remember { mutableStateOf(RealtimeTab.COMMUNICATION) }

    // Collect connection state
    LaunchedEffect(client) {
        client.connectionState.collect { state ->
            connectionState = state
        }
    }

    // Collect incoming messages
    LaunchedEffect(client) {
        client.messages.collect { message ->
            messages.add(0, message)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReqLabColors.Background)
            .testTag("realtime-panel"),
    ) {
        // ── Protocol selector & URL bar ──────────────────
        RealtimeUrlBar(
            protocol = selectedProtocol,
            onProtocolChange = {
                selectedProtocol = it
                url = it.defaultUrl
            },
            url = url,
            onUrlChange = { url = it },
            connectionState = connectionState,
            onConnect = {
                when (selectedProtocol) {
                    RealtimeProtocol.WEBSOCKET -> client.connectWebSocket(url)
                    RealtimeProtocol.SSE -> client.connectSSE(url)
                    RealtimeProtocol.SOCKETIO -> client.connectWebSocket(url) // simplified
                    RealtimeProtocol.MQTT -> client.connectWebSocket(url) // simplified
                }
            },
            onDisconnect = { client.disconnect() },
        )

        // ── Tabs ─────────────────────────────────────────
        RealtimeTabBar(selectedTab) { selectedTab = it }

        // ── Content ──────────────────────────────────────
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                RealtimeTab.COMMUNICATION -> CommunicationView(
                    messages = messages,
                    connectionState = connectionState,
                    messageInput = messageInput,
                    onMessageInputChange = { messageInput = it },
                    onSend = {
                        if (messageInput.isNotBlank()) {
                            client.sendMessage(messageInput)
                            messageInput = ""
                        }
                    },
                    onClear = { messages.clear() },
                    canSend = selectedProtocol != RealtimeProtocol.SSE, // SSE is receive-only
                )
                RealtimeTab.PROTOCOLS -> ProtocolsInfoView(selectedProtocol)
            }
        }
    }
}

// ── URL Bar ─────────────────────────────────────────────────────

@Composable
private fun RealtimeUrlBar(
    protocol: RealtimeProtocol,
    onProtocolChange: (RealtimeProtocol) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReqLabColors.Surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Protocol selector
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(ReqLabColors.SurfaceContainer),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            RealtimeProtocol.entries.forEach { proto ->
                val selected = proto == protocol
                Text(
                    text = proto.label,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) ReqLabColors.Primary else ReqLabColors.OnSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) ReqLabColors.Primary.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onProtocolChange(proto) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("protocol-${proto.name.lowercase()}"),
                )
            }
        }

        // URL input
        BasicTextField(
            value = url,
            onValueChange = onUrlChange,
            singleLine = true,
            textStyle = TextStyle(
                color = ReqLabColors.OnSurface,
                fontSize = 13.sp,
                fontFamily = CodeFontFamily,
            ),
            cursorBrush = SolidColor(ReqLabColors.Primary),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(ReqLabColors.Background)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("realtime-url-input"),
            decorationBox = { inner ->
                Box {
                    if (url.isEmpty()) {
                        Text("${Strings.enterUrl}…", color = ReqLabColors.OnSurfaceDim, fontSize = 13.sp, fontFamily = CodeFontFamily)
                    }
                    inner()
                }
            },
        )

        // Connect/Disconnect button
        val isConnected = connectionState == ConnectionState.CONNECTED
        val isConnecting = connectionState == ConnectionState.CONNECTING
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isConnected) ReqLabColors.Error.copy(alpha = 0.12f)
                    else ReqLabColors.Primary.copy(alpha = 0.12f)
                )
                .clickable(enabled = !isConnecting) {
                    if (isConnected) onDisconnect() else onConnect()
                }
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag("realtime-connect-button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                if (isConnected) Icons.Default.LinkOff else Icons.Default.Link,
                contentDescription = null,
                tint = if (isConnected) ReqLabColors.Error else ReqLabColors.Primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = when {
                    isConnecting -> Strings.t("connecting")
                    isConnected -> Strings.disconnect
                    else -> Strings.connect
                },
                color = if (isConnected) ReqLabColors.Error else ReqLabColors.Primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Connection status indicator
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    when (connectionState) {
                        ConnectionState.CONNECTED -> Color(0xFF4EC9B0)
                        ConnectionState.CONNECTING -> Color(0xFFE5C07B)
                        ConnectionState.ERROR -> Color(0xFFE06C75)
                        ConnectionState.DISCONNECTED -> Color(0xFF6C6C85)
                    }
                )
                .testTag("connection-indicator"),
        )
    }

    Box(Modifier.fillMaxWidth().height(1.dp).background(ReqLabColors.Border))
}

// ── Tab Bar ─────────────────────────────────────────────────────

@Composable
private fun RealtimeTabBar(selectedTab: RealtimeTab, onTabSelected: (RealtimeTab) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReqLabColors.Surface),
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color.Transparent,
            contentColor = ReqLabColors.OnSurface,
            edgePadding = 0.dp,
            divider = {},
            indicator = {},
        ) {
            RealtimeTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Tab(
                    selected = selected,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.height(34.dp),
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
                .background(ReqLabColors.Border),
        )
    }
}

// ── Communication View ──────────────────────────────────────────

@Composable
private fun CommunicationView(
    messages: List<RealtimeMessage>,
    connectionState: ConnectionState,
    messageInput: String,
    onMessageInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onClear: () -> Unit,
    canSend: Boolean,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Message input (only for bidirectional protocols)
        if (canSend) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ReqLabColors.Surface)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BasicTextField(
                    value = messageInput,
                    onValueChange = onMessageInputChange,
                    textStyle = TextStyle(
                        color = ReqLabColors.OnSurface,
                        fontSize = 13.sp,
                        fontFamily = CodeFontFamily,
                    ),
                    cursorBrush = SolidColor(ReqLabColors.Primary),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ReqLabColors.Background)
                        .border(1.dp, ReqLabColors.Border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("message-input"),
                    decorationBox = { inner ->
                        Box {
                            if (messageInput.isEmpty()) {
                                Text(Strings.t("enter_message"), color = ReqLabColors.OnSurfaceDim, fontSize = 13.sp, fontFamily = CodeFontFamily)
                            }
                            inner()
                        }
                    },
                )

                // Send button
                IconButton(
                    onClick = onSend,
                    enabled = connectionState == ConnectionState.CONNECTED && messageInput.isNotBlank(),
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ReqLabColors.Primary.copy(alpha = 0.12f))
                        .testTag("send-message-button"),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = Strings.sendMessage,
                        tint = if (connectionState == ConnectionState.CONNECTED) ReqLabColors.Primary else ReqLabColors.OnSurfaceDim,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(ReqLabColors.Border))
        }

        // Messages list header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ReqLabColors.SurfaceContainer)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${Strings.t("messages")} (${messages.size})",
                color = ReqLabColors.OnSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Clear, "Clear messages", tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(14.dp))
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(ReqLabColors.Border))

        // Messages list
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (connectionState == ConnectionState.CONNECTED) "Waiting for messages…"
                        else "Connect to start receiving messages",
                        color = ReqLabColors.OnSurfaceDim,
                        fontSize = 13.sp,
                    )
                }
            }
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("messages-list"),
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageRow(message)
                }
            }
        }
    }
}

@Composable
private fun MessageRow(message: RealtimeMessage) {
    val isSent = message.direction == MessageDirection.SENT
    val directionColor = if (isSent) Color(0xFF4EC9B0) else Color(0xFF7B8DEF)
    val directionLabel = if (isSent) "↑ SENT" else "↓ RECEIVED"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(ReqLabColors.SurfaceContainer)
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Direction badge
                Text(
                    directionLabel,
                    color = directionColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(directionColor.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )

                // Event type (if present)
                message.eventType?.let { eventType ->
                    Text(
                        eventType,
                        color = ReqLabColors.Tertiary,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(ReqLabColors.Tertiary.copy(alpha = 0.10f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            // Timestamp
            Text(
                formatTimestampShort(message.timestamp),
                color = ReqLabColors.OnSurfaceDim,
                fontSize = 10.sp,
                fontFamily = CodeFontFamily,
            )
        }

        Spacer(Modifier.height(4.dp))

        // Message content
        Text(
            text = message.content,
            color = ReqLabColors.OnSurface,
            fontSize = 12.sp,
            fontFamily = CodeFontFamily,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        )
    }
}

// ── Protocols Info View ─────────────────────────────────────────

@Composable
private fun ProtocolsInfoView(protocol: RealtimeProtocol) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "${protocol.label} Protocol",
            color = ReqLabColors.OnSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )

        val description = when (protocol) {
            RealtimeProtocol.WEBSOCKET -> "WebSocket provides full-duplex communication channels over a single TCP connection. " +
                    "It enables real-time data flow between client and server.\n\n" +
                    "• Bidirectional communication\n" +
                    "• Low latency\n" +
                    "• Uses ws:// or wss:// protocol\n" +
                    "• Persistent connection"

            RealtimeProtocol.SSE -> "Server-Sent Events (SSE) enables servers to push data to clients over HTTP. " +
                    "It's a one-way communication channel from server to client.\n\n" +
                    "• Server-to-client only\n" +
                    "• Uses standard HTTP\n" +
                    "• Auto-reconnection built-in\n" +
                    "• Event types and IDs"

            RealtimeProtocol.SOCKETIO -> "Socket.IO enables real-time, bidirectional communication between web clients and servers. " +
                    "It uses WebSocket when possible and falls back to HTTP long-polling.\n\n" +
                    "• Event-driven communication\n" +
                    "• Automatic reconnection\n" +
                    "• Room/namespace support\n" +
                    "• Binary data support"

            RealtimeProtocol.MQTT -> "MQTT (Message Queuing Telemetry Transport) is a lightweight messaging protocol. " +
                    "It's designed for constrained devices and low-bandwidth, high-latency networks.\n\n" +
                    "• Publish/Subscribe model\n" +
                    "• Quality of Service levels (0, 1, 2)\n" +
                    "• Topic-based routing\n" +
                    "• Retained messages"
        }

        Text(
            description,
            color = ReqLabColors.OnSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
    }
}

// ── Utilities ───────────────────────────────────────────────────

private fun formatTimestampShort(epochMillis: Long): String {
    // Simple HH:mm:ss format approximation
    val totalSeconds = epochMillis / 1000
    val hours = (totalSeconds / 3600) % 24
    val minutes = (totalSeconds / 60) % 60
    val seconds = totalSeconds % 60
    return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}
