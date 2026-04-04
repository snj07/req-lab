package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt
import com.reqlab.ui.shared.i18n.AppLanguage
import com.reqlab.ui.shared.i18n.Strings
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

private enum class SettingsSection(val icon: ImageVector) {
    GENERAL(Icons.Default.Settings),
    THEME(Icons.Default.LightMode),
    LANGUAGE(Icons.Default.Language),
    NETWORK(Icons.Default.NetworkCheck),
    PROXY(Icons.Default.SettingsApplications),
    SCRIPTING(Icons.Default.DataObject),
    DATA(Icons.Default.Storage),
}

@Composable
private fun settingsSectionLabel(section: SettingsSection): String = when (section) {
    SettingsSection.GENERAL -> Strings.general
    SettingsSection.THEME -> Strings.theme
    SettingsSection.LANGUAGE -> Strings.language
    SettingsSection.NETWORK -> Strings.network
    SettingsSection.PROXY -> Strings.proxy
    SettingsSection.SCRIPTING -> Strings.t("scripts")
    SettingsSection.DATA -> Strings.t("data")
}

@Composable
fun SettingsDialog(state: AppState) {
    if (!state.showSettingsDialog) return

    var selectedSection by remember { mutableStateOf(SettingsSection.GENERAL) }
    val settings = state.settings

    // M-1: Make the dialog draggable (same pattern as EnvironmentEditDialog).
    var settingsOffsetX by remember { mutableStateOf(0f) }
    var settingsOffsetY by remember { mutableStateOf(0f) }
    var settingsViewportSize by remember { mutableStateOf(IntSize.Zero) }
    var settingsCardSize by remember { mutableStateOf(IntSize.Zero) }

    Dialog(onDismissRequest = { state.showSettingsDialog = false }) {
        // Full-screen invisible backdrop — captures taps outside the card to dismiss.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { settingsViewportSize = it }
                .pointerInput(Unit) {
                    detectTapGestures { state.showSettingsDialog = false }
                },
            contentAlignment = Alignment.Center,
        ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(settingsOffsetX.roundToInt(), settingsOffsetY.roundToInt()) }
                .widthIn(min = 680.dp, max = 860.dp)
                .height(520.dp)
                .onSizeChanged { settingsCardSize = it }
                .clip(RoundedCornerShape(12.dp))
                .background(ReqLabColors.Surface)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(12.dp))
                .draggableNoSlop { dx, dy ->
                    val (cx, cy) = clampDialogOffsetFromCenter(
                        offsetX = settingsOffsetX + dx,
                        offsetY = settingsOffsetY + dy,
                        cardSize = settingsCardSize,
                        viewportSize = settingsViewportSize,
                    )
                    settingsOffsetX = cx
                    settingsOffsetY = cy
                }
                .pointerInput(Unit) { detectTapGestures { } }
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
                        Strings.settings,
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
                                settingsSectionLabel(section),
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
                        settingsSectionLabel(selectedSection),
                        style = MaterialTheme.typography.titleMedium,
                        color = ReqLabColors.OnSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )

                    when (selectedSection) {
                        SettingsSection.GENERAL  -> GeneralSettings(settings)
                        SettingsSection.THEME    -> ThemeSettings(settings)
                        SettingsSection.LANGUAGE -> LanguageSettings(settings)
                        SettingsSection.NETWORK  -> NetworkSettings(settings)
                        SettingsSection.PROXY    -> ProxySettings(settings)
                        SettingsSection.SCRIPTING -> ScriptingSettings(settings)
                        SettingsSection.DATA     -> DataSettings(state)
                    }

                    Spacer(Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ReqLabColors.SurfaceContainer)
                            .clickable { state.showHelpDialog = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("settings-help-about-button"),
                    ) {
                        Text(
                            text = "Open Help & About",
                            color = ReqLabColors.Primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
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
                Text(Strings.t("done"), color = ReqLabColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }  // closes settings card Box
        }  // closes full-screen backdrop Box
    }
}

// ── Section composables ─────────────────────────────────────────

@Composable
private fun GeneralSettings(s: AppSettings) {
    SettingToggle(
        label = Strings.autoSave,
        description = Strings.t("settings_auto_save_desc"),
        checked = s.autoSaveRequests,
        onCheckedChange = { s.autoSaveRequests = it },
        tag = "auto-save-toggle",
    )
    SettingsDivider()
    SettingToggle(
        label = Strings.t("confirm_before_deleting"),
        description = Strings.t("settings_confirm_delete_desc"),
        checked = s.confirmBeforeDelete,
        onCheckedChange = { s.confirmBeforeDelete = it },
    )
    SettingsDivider()
    SettingNumberField(
        label = Strings.t("default_request_timeout_seconds"),
        value = s.defaultTimeoutSec,
        onValueChange = { s.defaultTimeoutSec = it },
    )
    SettingsDivider()
    SettingChoice(
        label = Strings.responseLayout,
        description = Strings.t("settings_response_layout_desc"),
        options = listOf(ResponseLayout.RIGHT to Strings.t("right_side"), ResponseLayout.BOTTOM to Strings.t("bottom_panel")),
        selected = s.responseLayout,
        onSelected = { s.responseLayout = it },
        tagPrefix = "response-layout",
    )
}

@Composable
private fun ThemeSettings(s: AppSettings) {
    Text(Strings.theme, style = MaterialTheme.typography.labelMedium, color = ReqLabColors.OnSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(AppTheme.DARK, Strings.darkMode, Icons.Default.DarkMode),
            Triple(AppTheme.LIGHT, Strings.lightMode, Icons.Default.LightMode),
            Triple(AppTheme.SYSTEM, Strings.systemTheme, Icons.Default.SettingsApplications),
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
private fun LanguageSettings(s: AppSettings) {
    Text(Strings.language, style = MaterialTheme.typography.labelMedium, color = ReqLabColors.OnSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppLanguage.entries.forEach { lang ->
            val isSelected = s.language == lang
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) ReqLabColors.SelectedItem else ReqLabColors.SurfaceContainer)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) ReqLabColors.Primary else ReqLabColors.Border,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { s.language = lang }
                    .padding(16.dp)
                    .testTag("language-${lang.code}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(lang.code.uppercase(), color = if (isSelected) ReqLabColors.Primary else ReqLabColors.OnSurfaceDim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(lang.displayName, color = if (isSelected) ReqLabColors.OnSurface else ReqLabColors.OnSurfaceVariant, fontSize = 12.sp)
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    Text(
        Strings.t("settings_language_change_hint"),
        color = ReqLabColors.OnSurfaceDim,
        fontSize = 11.sp,
    )
}

@Composable
private fun NetworkSettings(s: AppSettings) {
    SettingNumberField(Strings.t("request_timeout_seconds"), s.requestTimeoutSec) { s.requestTimeoutSec = it }
    SettingsDivider()
    SettingToggle(
        Strings.followRedirects,
        Strings.t("settings_follow_redirects_desc"),
        s.followRedirects,
        { s.followRedirects = it },
        tag = "follow-redirects-toggle",
    )
}

@Composable
private fun ProxySettings(s: AppSettings) {
    SettingToggle(Strings.t("enable_proxy"), Strings.t("settings_enable_proxy_desc"), s.proxyEnabled, { s.proxyEnabled = it })
    SettingsDivider()
    SettingTextField(Strings.t("http_proxy"), s.httpProxy, { s.httpProxy = it }, "http://proxy:8080")
    SettingsDivider()
    SettingTextField(Strings.t("https_proxy"), s.httpsProxy, { s.httpsProxy = it }, "https://proxy:8443")
}

@Composable
private fun ScriptingSettings(s: AppSettings) {
    SettingTextField(
        label = Strings.t("script_namespace_prefix"),
        value = s.scriptPrefix,
        onValueChange = { v ->
            val clean = v.trim().filter { it.isLetterOrDigit() || it == '_' }
            if (clean.isNotEmpty()) s.scriptPrefix = clean
        },
        placeholder = "reqlab",
    )
    SettingsDivider()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            Strings.t("script_namespace_desc"),
            color = ReqLabColors.OnSurfaceVariant,
            fontSize = 12.sp,
        )
        Text(
            Strings.t("example_with_prefix") + " \"${s.scriptPrefix}\":",
            color = ReqLabColors.OnSurfaceDim,
            fontSize = 12.sp,
        )
        Text(
            "${s.scriptPrefix}.test(\"status\", () => {\n" +
            "  ${s.scriptPrefix}.expect(${s.scriptPrefix}.response.code).to.equal(200)\n" +
            "})",
            color = ReqLabColors.Primary,
            fontSize = 11.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
        Text(
            Strings.t("script_namespace_default_hint"),
            color = ReqLabColors.OnSurfaceDim,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun DataSettings(state: AppState) {
    val scope = rememberCoroutineScope()
    val importExportError = Strings.t("import_export_error")
    val unknownError = Strings.t("unknown_error")
    val exportWorkspace = Strings.t("export_workspace")
    val exportWorkspaceDesc = Strings.t("export_workspace_desc")
    val exportLabel = Strings.t("export")
    val exportingWorkspace = Strings.t("exporting_workspace")
    val exportingWorkspaceMessage = Strings.t("exporting_workspace_message")
    val workspaceExported = Strings.t("workspace_exported")
    val importWorkspace = Strings.t("import_workspace")
    val importWorkspaceDesc = Strings.t("import_workspace_desc")
    val importLabel = Strings.t("import")
    val importingWorkspace = Strings.t("importing_workspace")
    val importingWorkspaceMessage = Strings.t("importing_workspace_message")
    val clearHistoryDesc = Strings.t("clear_history_desc")
    val clearLabel = Strings.t("clear")
    val clearHistoryTitle = Strings.t("clear_history_title")
    val clearHistoryConfirmMessage = Strings.t("clear_history_confirm_message")
    val historyCleared = Strings.t("history_cleared")
    val clearConsoleLogs = Strings.t("clear_console_logs")
    val clearConsoleLogsDesc = Strings.t("clear_console_logs_desc")

    fun runTracked(title: String, message: String, block: suspend () -> Unit) {
        val job: Job = scope.launch(ioDispatcher) {
            runCatching { block() }
                .onFailure { e ->
                    withContext(Dispatchers.Main) {
                        state.showError(importExportError, e.message ?: unknownError)
                        state.log("$title failed: ${e.message}", LogLevel.ERROR)
                    }
                }
                .also { withContext(Dispatchers.Main) { state.finishOperation() } }
        }
        state.startOperation(title, message, job)
    }

    SettingAction(exportWorkspace, exportWorkspaceDesc, actionLabel = exportLabel) {
        scope.launch {
            runTracked(exportingWorkspace, exportingWorkspaceMessage) {
                val jsonStr = ImportExportRepository.exportWorkspaceToString(state)
                withContext(Dispatchers.Main) {
                    saveFileForExport(jsonStr, "workspace-backup.json")
                    state.log(workspaceExported, LogLevel.SUCCESS)
                }
            }
        }
    }
    SettingsDivider()
    SettingAction(importWorkspace, importWorkspaceDesc, actionLabel = importLabel) {
        pickFileForImport { content ->
            scope.launch {
                runTracked(importingWorkspace, importingWorkspaceMessage) {
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
        Strings.clearHistory,
        clearHistoryDesc,
        actionLabel = clearLabel,
        actionColor = ReqLabColors.Error,
        tag = "clear-history",
    ) {
        state.showConfirm(
            title = clearHistoryTitle,
            message = clearHistoryConfirmMessage,
            action = {
                state.clearHistory()
                state.log(historyCleared, LogLevel.INFO)
            },
        )
    }
    SettingsDivider()
    SettingAction(
        clearConsoleLogs,
        clearConsoleLogsDesc,
        actionLabel = clearLabel,
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
    // M-10: Maintain local text state so mid-edit values (e.g. empty string
    // while the user is deleting digits) are never silently discarded.
    // A red border signals that the current text cannot be parsed as a number.
    var localText by remember(value) { mutableStateOf(value.toString()) }
    val isValid = localText.toIntOrNull() != null

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = ReqLabColors.OnSurface, modifier = Modifier.weight(1f))
        BasicTextField(
            value = localText,
            onValueChange = { newText ->
                // Allow any text while typing; only commit valid integers.
                localText = newText
                newText.toIntOrNull()?.let(onValueChange)
            },
            singleLine = true,
            textStyle = TextStyle(color = ReqLabColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
            cursorBrush = SolidColor(ReqLabColors.Primary),
            modifier = Modifier
                .width(80.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ReqLabColors.SurfaceContainer)
                // Red border for invalid input so the user gets immediate feedback.
                .border(
                    1.dp,
                    if (isValid) ReqLabColors.Border else ReqLabColors.Error,
                    RoundedCornerShape(4.dp),
                )
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
