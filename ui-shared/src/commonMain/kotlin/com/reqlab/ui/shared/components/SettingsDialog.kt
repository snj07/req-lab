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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import androidx.compose.ui.window.Dialog
import com.reqlab.ui.shared.state.AppSettings
import com.reqlab.ui.shared.state.ResponseLayout
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.AppTheme
import com.reqlab.ui.shared.persistence.ImportExportRepository
import com.reqlab.ui.shared.persistence.WorkspaceRepository
import com.reqlab.ui.shared.state.LogLevel
import com.reqlab.ui.shared.theme.CodeFontFamily
import com.reqlab.ui.shared.theme.ReqLabColors
import com.reqlab.ui.shared.platform.ioDispatcher
import com.reqlab.ui.shared.platform.pickFileForImport
import com.reqlab.ui.shared.platform.saveFileForExport

private enum class SettingsSection(val label: String, val icon: ImageVector) {
    GENERAL("General",   Icons.Default.Settings),
    THEME("Theme",       Icons.Default.LightMode),
    NETWORK("Network",   Icons.Default.NetworkCheck),
    PROXY("Proxy",       Icons.Default.Language),
    DATA("Data",         Icons.Default.Storage),
}

@Composable
fun SettingsDialog(state: AppState) {
    if (!state.showSettingsDialog) return

    var selectedSection by remember { mutableStateOf(SettingsSection.GENERAL) }
    val settings = state.settings

    Dialog(onDismissRequest = { state.showSettingsDialog = false }) {
        Box(
            modifier = Modifier
                .widthIn(min = 680.dp, max = 860.dp)
                .height(520.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ReqLabColors.Surface)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(12.dp))
                .testTag("settings-dialog"),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // ── Left nav ──────────────────────────────────
                Column(
                    modifier = Modifier
                        .width(180.dp)
                        .fillMaxHeight()
                        .background(ReqLabColors.SurfaceVariant)
                        .padding(12.dp),
                ) {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleMedium,
                        color = ReqLabColors.OnSurface,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )

                    SettingsSection.entries.forEach { section ->
                        val isActive = section == selectedSection
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isActive) ReqLabColors.SelectedItem else Color.Transparent)
                                .clickable { selectedSection = section }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                section.icon,
                                contentDescription = null,
                                tint = if (isActive) ReqLabColors.Primary else ReqLabColors.OnSurfaceDim,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                section.label,
                                fontSize = 13.sp,
                                color = if (isActive) ReqLabColors.OnSurface else ReqLabColors.OnSurfaceVariant,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }

                // Right border
                Box(Modifier.width(1.dp).fillMaxHeight().background(ReqLabColors.Border))

                // ── Right content ─────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        selectedSection.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = ReqLabColors.OnSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )

                    when (selectedSection) {
                        SettingsSection.GENERAL -> GeneralSettings(settings)
                        SettingsSection.THEME   -> ThemeSettings(settings)
                        SettingsSection.NETWORK -> NetworkSettings(settings)
                        SettingsSection.PROXY   -> ProxySettings(settings)
                        SettingsSection.DATA    -> DataSettings(state)
                    }
                }
            }

            // Close button (top-right)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ReqLabColors.SurfaceContainer)
                    .clickable { state.showSettingsDialog = false }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("settings-close-button"),
            ) {
                Text("Done", color = ReqLabColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ── Section composables ─────────────────────────────────────────

@Composable
private fun GeneralSettings(s: AppSettings) {
    SettingToggle(
        label = "Auto-save requests",
        description = "Automatically save request changes before switching tabs",
        checked = s.autoSaveRequests,
        onCheckedChange = { s.autoSaveRequests = it },
        tag = "auto-save-toggle",
    )
    SettingsDivider()
    SettingToggle(
        label = "Confirm before deleting",
        description = "Show confirmation dialog when deleting requests or collections",
        checked = s.confirmBeforeDelete,
        onCheckedChange = { s.confirmBeforeDelete = it },
    )
    SettingsDivider()
    SettingNumberField(
        label = "Default request timeout (seconds)",
        value = s.defaultTimeoutSec,
        onValueChange = { s.defaultTimeoutSec = it },
    )
    SettingsDivider()
    SettingChoice(
        label = "Response layout",
        description = "Choose where response panel appears",
        options = listOf(ResponseLayout.RIGHT to "Right side", ResponseLayout.BOTTOM to "Bottom panel"),
        selected = s.responseLayout,
        onSelected = { s.responseLayout = it },
        tagPrefix = "response-layout",
    )
}

@Composable
private fun ThemeSettings(s: AppSettings) {
    Text("Theme", style = MaterialTheme.typography.labelMedium, color = ReqLabColors.OnSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(AppTheme.DARK,   "Dark",   Icons.Default.DarkMode),
            Triple(AppTheme.LIGHT,  "Light",  Icons.Default.LightMode),
            Triple(AppTheme.SYSTEM, "System", Icons.Default.SettingsApplications),
        ).forEach { (theme, label, icon) ->
            val isSelected = s.theme == theme
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) ReqLabColors.SelectedItem else ReqLabColors.SurfaceContainer)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) ReqLabColors.Primary else ReqLabColors.Border,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { s.theme = theme }
                    .padding(16.dp)
                    .testTag("theme-${label.lowercase()}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, contentDescription = label, tint = if (isSelected) ReqLabColors.Primary else ReqLabColors.OnSurfaceDim, modifier = Modifier.size(24.dp))
                Text(label, color = if (isSelected) ReqLabColors.OnSurface else ReqLabColors.OnSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun NetworkSettings(s: AppSettings) {
    SettingNumberField("Request timeout (seconds)", s.requestTimeoutSec) { s.requestTimeoutSec = it }
    SettingsDivider()
    SettingToggle(
        "Follow redirects",
        "Automatically follow HTTP 3xx redirect responses",
        s.followRedirects,
        { s.followRedirects = it },
        tag = "follow-redirects-toggle",
    )
}

@Composable
private fun ProxySettings(s: AppSettings) {
    SettingToggle("Enable proxy", "Route requests through a proxy server", s.proxyEnabled, { s.proxyEnabled = it })
    SettingsDivider()
    SettingTextField("HTTP proxy", s.httpProxy, { s.httpProxy = it }, "http://proxy:8080")
    SettingsDivider()
    SettingTextField("HTTPS proxy", s.httpsProxy, { s.httpsProxy = it }, "https://proxy:8443")
}

@Composable
private fun DataSettings(state: AppState) {
    val scope = rememberCoroutineScope()

    fun runTracked(title: String, message: String, block: suspend () -> Unit) {
        val job: Job = scope.launch(ioDispatcher) {
            runCatching { block() }
                .onFailure { e ->
                    withContext(Dispatchers.Main) {
                        state.showError("Import/Export error", e.message ?: "Unknown error")
                        state.log("$title failed: ${e.message}", LogLevel.ERROR)
                    }
                }
                .also { withContext(Dispatchers.Main) { state.finishOperation() } }
        }
        state.startOperation(title, message, job)
    }

    SettingAction("Export workspace", "Save all collections and environments to a JSON backup", actionLabel = "Export") {
        scope.launch {
            runTracked("Exporting workspace", "Exporting workspace...") {
                val jsonStr = ImportExportRepository.exportWorkspaceToString(state)
                withContext(Dispatchers.Main) {
                    saveFileForExport(jsonStr, "workspace-backup.json")
                    state.log("Workspace exported → workspace-backup.json", LogLevel.SUCCESS)
                }
            }
        }
    }
    SettingsDivider()
    SettingAction("Import workspace", "Load collections and environments from a JSON backup", actionLabel = "Import") {
        pickFileForImport { content ->
            scope.launch {
                runTracked("Importing workspace", "Importing workspace...") {
                    val result = ImportExportRepository.importWorkspaceFromString(state, content)
                    WorkspaceRepository.save(state)
                    withContext(Dispatchers.Main) {
                        state.log(
                            "Workspace imported: ${result.importedCollections} collections, ${result.importedEnvironments} environments",
                            LogLevel.SUCCESS,
                        )
                    }
                }
            }
        }
    }
    SettingsDivider()
    SettingAction(
        "Clear history",
        "Remove all request history entries",
        actionLabel = "Clear",
        actionColor = ReqLabColors.Error,
        tag = "clear-history",
    ) {
        state.showConfirm(
            title = "Clear history?",
            message = "Are you sure you want to clear request history?",
            action = {
                state.historyItems.clear()
                state.log("History cleared", LogLevel.INFO)
            },
        )
    }
    SettingsDivider()
    SettingAction(
        "Clear console logs",
        "Remove all console log entries",
        actionLabel = "Clear",
        actionColor = ReqLabColors.Error,
        tag = "clear-console",
    ) {
        state.consoleLogs.clear()
    }
}

@Composable
private fun <T> SettingChoice(
    label: String,
    description: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    tagPrefix: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 13.sp, color = ReqLabColors.OnSurface)
        if (description.isNotEmpty()) {
            Text(description, fontSize = 11.sp, color = ReqLabColors.OnSurfaceDim)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, text) ->
                val active = value == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) ReqLabColors.SelectedItem else ReqLabColors.SurfaceContainer)
                        .border(
                            width = if (active) 2.dp else 1.dp,
                            color = if (active) ReqLabColors.Primary else ReqLabColors.Border,
                            shape = RoundedCornerShape(6.dp),
                        )
                        .clickable { onSelected(value) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("$tagPrefix-${text.lowercase().replace(" ", "-")}"),
                ) {
                    Text(
                        text = text,
                        color = if (active) ReqLabColors.OnSurface else ReqLabColors.OnSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

// ── Setting primitives ──────────────────────────────────────────

@Composable
private fun SettingToggle(
    label: String,
    description: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String = "",
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(label, fontSize = 13.sp, color = ReqLabColors.OnSurface)
            if (description.isNotEmpty()) {
                Text(description, fontSize = 11.sp, color = ReqLabColors.OnSurfaceDim)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier,
            colors = SwitchDefaults.colors(
                checkedThumbColor  = ReqLabColors.OnPrimary,
                checkedTrackColor  = ReqLabColors.Primary,
                uncheckedThumbColor= ReqLabColors.OnSurfaceDim,
                uncheckedTrackColor= ReqLabColors.SurfaceHigh,
            ),
        )
    }
}

@Composable
private fun SettingNumberField(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = ReqLabColors.OnSurface, modifier = Modifier.weight(1f))
        BasicTextField(
            value = value.toString(),
            onValueChange = { it.toIntOrNull()?.let(onValueChange) },
            singleLine = true,
            textStyle = TextStyle(color = ReqLabColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
            cursorBrush = SolidColor(ReqLabColors.Primary),
            modifier = Modifier
                .width(80.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ReqLabColors.SurfaceContainer)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SettingTextField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 13.sp, color = ReqLabColors.OnSurface)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = ReqLabColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
            cursorBrush = SolidColor(ReqLabColors.Primary),
            modifier = Modifier
                .fillMaxWidth()
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
}

@Composable
private fun SettingAction(
    label: String,
    description: String = "",
    actionLabel: String = "Run",
    actionColor: Color = ReqLabColors.Primary,
    tag: String = "",
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(label, fontSize = 13.sp, color = ReqLabColors.OnSurface)
            if (description.isNotEmpty()) {
                Text(description, fontSize = 11.sp, color = ReqLabColors.OnSurfaceDim)
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(actionColor.copy(alpha = 0.12f))
                .border(1.dp, actionColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                .clickable(onClick = onAction)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .then(if (tag.isNotEmpty()) Modifier.testTag("$tag-action") else Modifier),
        ) {
            Text(actionLabel, color = actionColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = ReqLabColors.Border.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
}
