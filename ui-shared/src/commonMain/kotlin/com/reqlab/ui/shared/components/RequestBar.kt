package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.core.model.HttpMethodType
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.theme.CodeFontFamily
import com.reqlab.ui.shared.theme.ReqLabColors
import com.reqlab.ui.shared.theme.httpMethodColor

/**
 * The top bar of the request editor: method selector, URL field, Send, Save,
 * Retry, and Copy cURL buttons.
 */
@Composable
fun RequestBar(
    method: HttpMethodType,
    onMethodChanged: (HttpMethodType) -> Unit,
    url: String,
    onUrlChanged: (String) -> Unit,
    isLoading: Boolean,
    onSend: () -> Unit,
    /** Called when the user clicks the Stop button while a request is in-flight (H-2). */
    onCancel: () -> Unit = {},
    onSave: () -> Unit,
    /** Each pair is a label (e.g. "cURL (resolved)") and the action to copy that format. */
    copyFormats: List<Pair<String, () -> Unit>> = emptyList(),
    retryCount: Int,
    retryDelayMs: Long,
    onRetryCountChanged: (Int) -> Unit,
    onRetryDelayChanged: (Long) -> Unit,
    state: AppState? = null,
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
        MethodDropdown(method, onMethodChanged)

        // URL input (variable-aware: highlights {{token}} in orange)
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(ReqLabColors.SurfaceContainer)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            VariableAwareTextField(
                value = url,
                onValueChange = onUrlChanged,
                placeholder = "Enter request URL…",
                textStyle = TextStyle(
                    color = ReqLabColors.OnSurface,
                    fontSize = 14.sp,
                    fontFamily = CodeFontFamily,
                ),
                state = state,
                modifier = Modifier.fillMaxWidth().testTag("url-input"),
            )
        }

        SendButton(isLoading, onSend, onCancel)
        SaveButton(isLoading = isLoading, onClick = onSave)
        RetryControlsButton(
            retryCount = retryCount,
            retryDelayMs = retryDelayMs,
            isLoading = isLoading,
            onRetryCountChanged = onRetryCountChanged,
            onRetryDelayChanged = onRetryDelayChanged,
        )
        CopyCurlButton(isLoading = isLoading, copyFormats = copyFormats)
    }
}

// ── Method dropdown ─────────────────────────────────────────────

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
                    text = { Text(m.name, color = httpMethodColor(m), fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    onClick = { onMethodChanged(m); expanded = false },
                )
            }
        }
    }
}

// ── Action buttons ──────────────────────────────────────────────

@Composable
private fun SendButton(isLoading: Boolean, onClick: () -> Unit, onCancel: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    if (isLoading) {
        // H-2: When a request is in-flight, show a Stop / Cancel button so the user
        // can abort it immediately without having to close the tab.
        Box(
            modifier = Modifier
                .widthIn(min = 80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isHovered) androidx.compose.ui.graphics.Color(0xFFD32F2F)
                    else androidx.compose.ui.graphics.Color(0xFFC62828)
                )
                .hoverable(interactionSource)
                .clickable(onClick = onCancel)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("stop-button"),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = "Stop request",
                    tint = ReqLabColors.OnPrimary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Stop",
                    color = ReqLabColors.OnPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .widthIn(min = 80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isHovered) ReqLabColors.Primary.copy(alpha = 0.9f) else ReqLabColors.Primary)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("send-button"),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Send",
                    color = ReqLabColors.OnPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
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
            .widthIn(min = 72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isHovered) ReqLabColors.SurfaceHigh else ReqLabColors.SurfaceContainer)
            .hoverable(interactionSource)
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("save-button"),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Save",
                color = ReqLabColors.OnSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Icon(Icons.Default.Save, contentDescription = null, tint = ReqLabColors.OnSurface, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun CopyCurlButton(isLoading: Boolean, copyFormats: List<Pair<String, () -> Unit>>) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var expanded by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isHovered) ReqLabColors.SurfaceHigh else ReqLabColors.SurfaceContainer)
                .hoverable(interactionSource)
                .clickable(enabled = !isLoading) {
                    if (copyFormats.size == 1) copyFormats.first().second()
                    else if (copyFormats.isNotEmpty()) expanded = true
                    else expanded = true
                }
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .testTag("copy-curl-button"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy as…", tint = ReqLabColors.OnSurface, modifier = Modifier.size(15.dp))
        }

        if (copyFormats.isNotEmpty()) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                copyFormats.forEach { (label, action) ->
                    DropdownMenuItem(
                        text = { Text(label, fontSize = 13.sp) },
                        onClick = { action(); expanded = false },
                    )
                }
            }
        }
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
                    onClick = { onRetryCountChanged(attempts); expanded = false },
                )
            }
            listOf(100L, 250L, 500L, 1000L).forEach { delay ->
                DropdownMenuItem(
                    text = { Text("Delay: ${delay}ms") },
                    onClick = { onRetryDelayChanged(delay); expanded = false },
                )
            }
        }
    }
}
