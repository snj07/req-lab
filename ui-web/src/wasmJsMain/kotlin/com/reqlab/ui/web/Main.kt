package com.reqlab.ui.web

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.reqlab.ui.shared.MainScreen
import com.reqlab.ui.shared.persistence.SettingsRepository
import com.reqlab.ui.shared.persistence.TabsRepository
import com.reqlab.ui.shared.persistence.WorkspaceRepository
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.theme.ReqLabTheme

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(canvasElementId = "ComposeTarget", title = "ReqLab") {
        val state = remember {
            AppState(openDefaultTab = false).also {
                SettingsRepository.load(it.settings)
                it.collectionsExpanded = it.settings.collectionsExpanded
                it.environmentsExpanded = it.settings.environmentsExpanded
                WorkspaceRepository.load(it)   // collections first
                TabsRepository.load(it)         // IDs resolved against loaded collections
                it.syncSidebarToActiveTab()
            }
        }
        ReqLabTheme(appTheme = state.settings.theme, language = state.settings.language) {
            MainScreen(state)
        }
    }
}
