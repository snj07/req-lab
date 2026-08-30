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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.core.model.McpContent
import com.reqlab.core.model.McpCreateMessageResult
import com.reqlab.core.model.McpElicitRequest
import com.reqlab.ui.shared.i18n.Strings
import com.reqlab.ui.shared.mcp.McpPendingElicitation
import com.reqlab.ui.shared.mcp.McpPendingSampling
import com.reqlab.ui.shared.mcp.McpSessionState
import com.reqlab.ui.shared.mcp.mcpPrettyJson
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.RequestTabState
import com.reqlab.ui.shared.theme.CodeFontFamily
import com.reqlab.ui.shared.theme.ReqLabColors

@Composable
fun McpWorkspaceResponse(state: AppState, tab: RequestTabState) {
    val session = remember(tab.id) { state.getOrCreateMcpSession(tab.id) }
    val sampling by session.pendingSampling.collectAsState()
    val elicit by session.pendingElicitation.collectAsState()
    when {
        sampling != null -> McpSamplingCallbackPane(session, sampling!!)
        elicit != null -> McpElicitationCallbackPane(state, session, elicit!!)
        else -> ResponseViewer(tab)
    }
}

@Composable
internal fun McpSamplingCallbackPane(session: McpSessionState, pending: McpPendingSampling) {
    when (pending) {
        is McpPendingSampling.ReviewRequest -> McpSamplingRequestForm(
            requestJson = mcpPrettyJson.encodeToString(
                com.reqlab.core.model.McpCreateMessageRequest.serializer(),
                pending.request,
            ),
            onApproveGenerate = { session.approveSamplingGenerate() },
            onCancel = { session.cancelSampling() },
        )
        is McpPendingSampling.ReviewResult -> McpSamplingResultForm(
            initial = pending.draft,
            generateError = pending.generateError,
            generating = pending.generating,
            onApprove = { session.submitSamplingResult(it) },
            onCancel = { session.cancelSampling() },
        )
    }
}

@Composable
internal fun McpElicitationCallbackPane(
    state: AppState,
    session: McpSessionState,
    pending: McpPendingElicitation,
) {
    McpElicitationForm(
        state = state,
        request = pending.request,
        argsJson = pending.argsJson,
        onArgsChange = { session.updatePendingElicitArgs(it) },
        onAccept = { session.submitElicitation() },
        onDecline = { session.declineElicitation() },
    )
}

@Composable
fun McpSamplingRequestForm(
    requestJson: String,
    onApproveGenerate: () -> Unit,
    onCancel: () -> Unit,
) {
    CallbackPaneScaffold(
        title = Strings.t("mcp_sampling_review_request"),
        testTag = "mcp-sampling-request",
        actions = {
            OutlinedButton(onClick = onCancel, modifier = Modifier.testTag("mcp-sampling-cancel")) {
                Text(Strings.t("cancel"))
            }
            Button(onClick = onApproveGenerate, modifier = Modifier.testTag("mcp-sampling-approve-generate")) {
                Text(Strings.t("mcp_sampling_approve_generate"))
            }
        },
    ) {
        Text(
            requestJson,
            color = ReqLabColors.OnSurface,
            fontSize = 12.sp,
            fontFamily = CodeFontFamily,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ReqLabColors.SurfaceContainer)
                .padding(10.dp)
                .testTag("mcp-sampling-request-json"),
        )
    }
}

@Composable
fun McpSamplingResultForm(
    initial: McpCreateMessageResult,
    generateError: String? = null,
    generating: Boolean = false,
    onApprove: (McpCreateMessageResult) -> Unit,
    onCancel: () -> Unit,
) {
    var content by remember(initial) { mutableStateOf(initial.content.text.orEmpty()) }
    var role by remember(initial) { mutableStateOf(coerceSamplingRole(initial.role)) }
    var model by remember(initial) { mutableStateOf(initial.model) }
    var stopReason by remember(initial) { mutableStateOf(coerceSamplingStopReason(initial.stopReason)) }
    CallbackPaneScaffold(
        title = Strings.t("mcp_sampling_review_result"),
        testTag = "mcp-sampling-result",
        actions = {
            OutlinedButton(onClick = onCancel, modifier = Modifier.testTag("mcp-sampling-cancel")) {
                Text(Strings.t("cancel"))
            }
            Button(
                onClick = {
                    onApprove(
                        McpCreateMessageResult(
                            role = role.ifBlank { "assistant" },
                            content = McpContent(type = "text", text = content),
                            model = model.ifBlank { "mock" },
                            stopReason = stopReason.ifBlank { "endTurn" },
                        ),
                    )
                },
                enabled = !generating,
                modifier = Modifier.testTag("mcp-sampling-approve-send"),
            ) {
                Text(Strings.t("mcp_sampling_approve_send"))
            }
        },
    ) {
        if (generating) {
            Text(Strings.t("mcp_sampling_generating"), color = ReqLabColors.OnSurfaceDim, fontSize = 12.sp)
        }
        if (!generateError.isNullOrBlank()) {
            Text(
                "${Strings.t("mcp_sampling_generate_error")}: $generateError",
                color = ReqLabColors.Error,
                fontSize = 12.sp,
                modifier = Modifier.testTag("mcp-sampling-generate-error"),
            )
        }
        CallbackField(Strings.t("mcp_sampling_content"), required = true) {
            CallbackTextArea(content, { content = it }, "mcp-sampling-content")
        }
        CallbackField(Strings.t("mcp_sampling_role"), required = true) {
            CallbackDropdown(
                value = role,
                options = SamplingRoles,
                onSelect = { role = it },
                testTag = "mcp-sampling-role",
            )
        }
        CallbackField(Strings.t("mcp_sampling_model"), required = true) {
            CallbackTextLine(model, { model = it }, "mcp-sampling-model")
        }
        CallbackField(Strings.t("mcp_sampling_stop_reason")) {
            CallbackDropdown(
                value = stopReason,
                options = SamplingStopReasons,
                onSelect = { stopReason = it },
                testTag = "mcp-sampling-stop-reason",
            )
        }
    }
}

@Composable
fun McpElicitationForm(
    state: AppState,
    request: McpElicitRequest,
    argsJson: String,
    onArgsChange: (String) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    CallbackPaneScaffold(
        title = Strings.t("mcp_elicit_form"),
        testTag = "mcp-elicit-form",
        scrollContent = false,
        actions = {
            OutlinedButton(onClick = onDecline, modifier = Modifier.testTag("mcp-elicit-decline")) {
                Text(Strings.t("mcp_elicit_decline"))
            }
            Button(onClick = onAccept, modifier = Modifier.testTag("mcp-elicit-accept")) {
                Text(Strings.t("mcp_elicit_accept"))
            }
        },
    ) {
        Text(
            request.message,
            color = ReqLabColors.OnSurface,
            fontSize = 13.sp,
            modifier = Modifier.testTag("mcp-elicit-message"),
        )
        SchemaArgsEditor(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = state,
            schema = request.requestedSchema,
            args = argsJson,
            onArgsChange = onArgsChange,
            testTagPrefix = "mcp-elicit-args",
        )
    }
}

@Composable
private fun CallbackPaneScaffold(
    title: String,
    testTag: String,
    actions: @Composable () -> Unit,
    scrollContent: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(ReqLabColors.Background)
            .padding(12.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                color = ReqLabColors.OnSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            actions()
        }
        if (scrollContent) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        } else {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun CallbackField(label: String, required: Boolean = false, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (required) "$label *" else label,
            color = ReqLabColors.OnSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

private val SamplingRoles = listOf("assistant", "user")
private val SamplingStopReasons = listOf("endTurn", "stopSequence", "maxTokens")

private fun coerceSamplingRole(raw: String): String =
    if (raw in SamplingRoles) raw else "assistant"

private fun coerceSamplingStopReason(raw: String?): String =
    if (raw in SamplingStopReasons) raw!! else "endTurn"

@Composable
private fun CallbackDropdown(
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    testTag: String,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ReqLabColors.Surface)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag(testTag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value,
                color = ReqLabColors.OnSurface,
                fontSize = 13.sp,
                fontFamily = CodeFontFamily,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = ReqLabColors.OnSurfaceDim,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontFamily = CodeFontFamily, fontSize = 13.sp) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    modifier = Modifier.testTag("$testTag-$option"),
                )
            }
        }
    }
}

@Composable
private fun CallbackTextLine(value: String, onValueChange: (String) -> Unit, testTag: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ReqLabColors.Surface)
            .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = ReqLabColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
            cursorBrush = SolidColor(ReqLabColors.Primary),
            modifier = Modifier.fillMaxWidth().testTag(testTag),
        )
    }
}

@Composable
private fun CallbackTextArea(value: String, onValueChange: (String) -> Unit, testTag: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ReqLabColors.Surface)
            .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = ReqLabColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
            cursorBrush = SolidColor(ReqLabColors.Primary),
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).testTag(testTag),
        )
    }
}
