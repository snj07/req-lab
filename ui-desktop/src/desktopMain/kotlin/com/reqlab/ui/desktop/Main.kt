package com.reqlab.ui.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
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
            // Link restored tabs to sidebar: highlight + expand ancestors
            it.syncSidebarToActiveTab()
        }
    }

    Window(
        onCloseRequest = {
            TabsRepository.save(state)
            WorkspaceRepository.save(state)
            exitApplication()
        },
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
