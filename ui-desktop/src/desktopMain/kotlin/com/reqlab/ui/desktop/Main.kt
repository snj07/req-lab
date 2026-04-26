package com.reqlab.ui.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.reqlab.ui.shared.MainScreen
import com.reqlab.ui.shared.persistence.SettingsRepository
import com.reqlab.ui.shared.persistence.TabsRepository
import com.reqlab.ui.shared.persistence.WorkspaceRepository
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.theme.ReqLabTheme

fun main() {
    // Ensure OS surfaces (dock/app switcher/task tooltip) use the product name.
    System.setProperty("apple.awt.application.name", "ReqLab")
    System.setProperty("java.awt.application.name", "ReqLab")

    application {
    val state = remember {
        AppState(openDefaultTab = false).also {
            SettingsRepository.load(it.settings)
            it.collectionsExpanded = it.settings.collectionsExpanded
            it.environmentsExpanded = it.settings.environmentsExpanded
            WorkspaceRepository.load(it)   // collections first
            TabsRepository.load(it)         // IDs resolved against loaded collections
            // Always start with an unfiltered sidebar on app launch.
            it.sidebarSearchQuery = ""
            // Link restored tabs to sidebar: highlight + expand ancestors
            it.syncSidebarToActiveTab()
        }
    }

    // Async exit: avoids blocking the EDT with file I/O during window close.
    var exitPending by remember { mutableStateOf(false) }
    if (exitPending) {
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                TabsRepository.save(state)
                WorkspaceRepository.save(state)
            }
            exitApplication()
        }
    }

    Window(
        onCloseRequest = { exitPending = true },
        // Prevent a second close event from launching a duplicate save coroutine.
        // The window stays visible briefly while the LaunchedEffect finishes.
        icon = painterResource("icons/reqlab-icon-64.png"),
        title = "ReqLab",
        state = rememberWindowState(
            size = DpSize(1400.dp, 900.dp),
            position = WindowPosition.Aligned(Alignment.Center),
        ),
        onPreviewKeyEvent = { false },
    ) {
        ReqLabTheme(appTheme = state.settings.theme, language = state.settings.language) {
            MainScreen(state)
        }
    }
    }
}
