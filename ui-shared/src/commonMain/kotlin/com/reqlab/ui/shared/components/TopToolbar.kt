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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.reqlab.ui.shared.i18n.Strings
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.EnvState
import com.reqlab.ui.shared.state.ResponseLayout
import com.reqlab.ui.shared.state.WorkspaceMode
import com.reqlab.ui.shared.theme.ReqLabColors

@Composable
fun TopToolbar(state: AppState) {
    LaunchedEffect(state.workspaceMode) {
        if (state.workspaceMode != WorkspaceMode.HTTP) {
            state.workspaceMode = WorkspaceMode.HTTP
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(ReqLabColors.Surface)
            .padding(horizontal = 8.dp)
            .testTag("top-toolbar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Sidebar toggle
        IconButton(onClick = { state.sidebarExpanded = !state.sidebarExpanded }) {
            Icon(
                Icons.Default.Menu,
                contentDescription = Strings.t("toggle_sidebar"),
                tint = ReqLabColors.OnSurfaceVariant,
            )
        }

        // Logo
        Spacer(Modifier.width(4.dp))
        BrandIcon()
        Spacer(Modifier.width(8.dp))
        Text(
            text = Strings.appName,
            color = ReqLabColors.OnSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )

        Spacer(Modifier.width(16.dp))

        // Workspace mode selector (HTTP / Realtime / GraphQL)
        // Row(
        //     modifier = Modifier
        //         .clip(RoundedCornerShape(6.dp))
        //         .background(ReqLabColors.SurfaceContainer),
        //     horizontalArrangement = Arrangement.spacedBy(0.dp),
        // ) {
        //     WorkspaceMode.entries.forEach { mode ->
        //         val selected = state.workspaceMode == mode
        //         Text(
        //             text = when (mode) {
        //                 WorkspaceMode.HTTP -> "HTTP"
        //                 WorkspaceMode.REALTIME -> Strings.protocols
        //                 WorkspaceMode.GRAPHQL -> "GraphQL"
        //             },
        //             fontSize = 12.sp,
        //             fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        //             color = if (selected) ReqLabColors.Primary else ReqLabColors.OnSurfaceVariant,
        //             modifier = Modifier
        //                 .clip(RoundedCornerShape(6.dp))
        //                 .background(if (selected) ReqLabColors.Primary.copy(alpha = 0.12f) else Color.Transparent)
        //                 .clickable { state.workspaceMode = mode }
        //                 .padding(horizontal = 12.dp, vertical = 6.dp)
        //                 .testTag("workspace-mode-${mode.name.lowercase()}"),
        //         )
        //     }
        // }

        Spacer(Modifier.weight(1f))

        // Environment selector
        EnvironmentChip(state)

        Spacer(Modifier.width(8.dp))

        ToolbarIconButtonWithTooltip(
            tooltip = Strings.t("toggle_response_layout"),
            tooltipTag = "toolbar-tooltip-response-layout",
            onClick = {
                state.settings.responseLayout =
                    if (state.settings.responseLayout == ResponseLayout.RIGHT) ResponseLayout.BOTTOM
                    else ResponseLayout.RIGHT
            },
            modifier = Modifier.testTag("response-layout-toggle"),
        ) {
            Icon(
                imageVector = if (state.settings.responseLayout == ResponseLayout.RIGHT) {
                    Icons.AutoMirrored.Filled.ViewSidebar
                } else {
                    Icons.Default.ViewWeek
                },
                contentDescription = Strings.t("toggle_response_layout"),
                tint = ReqLabColors.OnSurfaceVariant,
            )
        }

        Spacer(Modifier.width(4.dp))

        // Global variables
        ToolbarIconButtonWithTooltip(
            tooltip = Strings.globalVariables,
            tooltipTag = "toolbar-tooltip-global-variables",
            onClick = { state.showGlobalVariablesDialog = true },
            modifier = Modifier.testTag("global-variables-button"),
        ) {
            Icon(
                Icons.Default.Public,
                contentDescription = Strings.globalVariables,
                tint = ReqLabColors.OnSurfaceVariant,
            )
        }

        ToolbarIconButtonWithTooltip(
            tooltip = "Help and About",
            tooltipTag = "toolbar-tooltip-help-about",
            onClick = { state.showHelpDialog = true },
        ) {
            Icon(
                Icons.Default.HelpOutline,
                contentDescription = "Help and About",
                tint = ReqLabColors.OnSurfaceVariant,
                modifier = Modifier.testTag("help-about-button"),
            )
        }

        // Settings
        ToolbarIconButtonWithTooltip(
            tooltip = Strings.settings,
            tooltipTag = "toolbar-tooltip-settings",
            onClick = { state.showSettingsDialog = true },
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = Strings.settings,
                tint = ReqLabColors.OnSurfaceVariant,
                modifier = Modifier.testTag("settings-button"),
            )
        }
    }

    // bottom border
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ReqLabColors.Border)
    )
}

@Composable
private fun ToolbarIconButtonWithTooltip(
    tooltip: String,
    tooltipTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var tooltipVisible by remember { mutableStateOf(false) }

    // Debounce: wait 200 ms before showing so edge-of-icon flicker is eliminated.
    // Hide immediately when hover leaves so it doesn't linger.
    LaunchedEffect(isHovered) {
        if (isHovered) {
            delay(200L)
            tooltipVisible = true
        } else {
            tooltipVisible = false
        }
    }

    Box {
        IconButton(
            onClick = onClick,
            modifier = modifier.hoverable(interactionSource),
        ) {
            content()
        }

        if (tooltipVisible) {
            Popup(
                alignment = Alignment.BottomCenter,
                offset = IntOffset(0, 40),
                properties = PopupProperties(focusable = false),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(ReqLabColors.SurfaceHigh)
                        .border(1.dp, ReqLabColors.Border, RoundedCornerShape(5.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag(tooltipTag),
                ) {
                    Text(
                        text = tooltip,
                        color = ReqLabColors.OnSurface,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
expect fun BrandIcon()

@Composable
private fun EnvironmentChip(state: AppState) {
    var menuExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val newEnvironmentLabel = Strings.t("new_environment")
    val editEnvironmentLabel = Strings.t("edit_environment")

    val selectedEnvironment = state.selectedEnvironment

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isHovered) ReqLabColors.HoverOverlay else ReqLabColors.SurfaceContainer)
                .hoverable(interactionSource)
                .clickable { menuExpanded = true }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("env-chip"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(ReqLabColors.Secondary)
            )
            Text(
                text = selectedEnvironment?.name ?: Strings.noEnvironmentsConfigured,
                style = MaterialTheme.typography.labelMedium,
                color = ReqLabColors.OnSurface,
            )
            Icon(
                Icons.Default.MoreVert,
                contentDescription = null,
                tint = ReqLabColors.OnSurfaceDim,
                modifier = Modifier.size(14.dp),
            )
        }

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            if (state.environments.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(Strings.noEnvironmentsConfigured) },
                    onClick = { menuExpanded = false },
                )
                DropdownMenuItem(
                    text = { Text(Strings.createEnvironment) },
                    onClick = {
                        val created = EnvState(newEnvironmentLabel)
                        state.environments.add(created)
                        state.selectedEnvIndex = state.environments.lastIndex
                        state.openEnvEdit(state.selectedEnvIndex)
                        menuExpanded = false
                    },
                )
            } else {
                state.environments.forEachIndexed { index, env ->
                    DropdownMenuItem(
                        text = { Text(env.name) },
                        onClick = {
                            state.selectedEnvIndex = index
                            menuExpanded = false
                        },
                    )
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("$editEnvironmentLabel…") },
                onClick = {
                    if (state.selectedEnvIndex in state.environments.indices) {
                        state.openEnvEdit(state.selectedEnvIndex)
                    }
                    menuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("${Strings.globalVariables}…") },
                onClick = {
                    state.showGlobalVariablesDialog = true
                    menuExpanded = false
                },
            )
        }
    }
}