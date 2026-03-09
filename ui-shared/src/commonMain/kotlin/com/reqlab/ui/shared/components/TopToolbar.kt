package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.ResponseLayout
import com.reqlab.ui.shared.state.WorkspaceMode
import com.reqlab.ui.shared.theme.ReqLabColors

@Composable
fun TopToolbar(state: AppState) {
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
                contentDescription = "Toggle sidebar",
                tint = ReqLabColors.OnSurfaceVariant,
            )
        }

        // Logo
        Spacer(Modifier.width(4.dp))
        Text(
            text = "ReqLab",
            color = ReqLabColors.Primary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )

        Spacer(Modifier.width(16.dp))

        // Workspace mode selector (HTTP / Realtime / GraphQL)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(ReqLabColors.SurfaceContainer),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            WorkspaceMode.entries.forEach { mode ->
                val selected = state.workspaceMode == mode
                Text(
                    text = mode.label,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) ReqLabColors.Primary else ReqLabColors.OnSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) ReqLabColors.Primary.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { state.workspaceMode = mode }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("workspace-mode-${mode.name.lowercase()}"),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Environment selector
        EnvironmentChip(state)

        Spacer(Modifier.width(8.dp))

        IconButton(
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
                contentDescription = "Toggle response layout",
                tint = ReqLabColors.OnSurfaceVariant,
            )
        }

        Spacer(Modifier.width(4.dp))

        // Global variables
        IconButton(
            onClick = { state.showGlobalVariablesDialog = true },
            modifier = Modifier.testTag("global-variables-button"),
        ) {
            Icon(
                Icons.Default.Public,
                contentDescription = "Global Variables",
                tint = ReqLabColors.OnSurfaceVariant,
            )
        }

        // Settings
        IconButton(onClick = { state.showSettingsDialog = true }) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Settings",
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
private fun EnvironmentChip(state: AppState) {
    var menuExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

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
                text = state.selectedEnvironment.name,
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
            state.environments.forEachIndexed { index, env ->
                DropdownMenuItem(
                    text = { Text(env.name) },
                    onClick = {
                        state.selectedEnvIndex = index
                        menuExpanded = false
                    },
                )
            }
            androidx.compose.material3.HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Edit environment…") },
                onClick = {
                    state.openEnvEdit(state.selectedEnvIndex)
                    menuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("Global Variables…") },
                onClick = {
                    state.showGlobalVariablesDialog = true
                    menuExpanded = false
                },
            )
        }
    }
}