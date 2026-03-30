package com.reqlab.ui.shared.persistence

import com.reqlab.ui.shared.platform.PlatformStorage
import com.reqlab.ui.shared.state.AppState

/**
 * Saves and restores workspace state (collections + environments) using PlatformStorage.
 */
object WorkspaceRepository {

    private const val STORAGE_KEY = "reqlab.workspace"

    fun save(state: AppState) {
        runCatching {
            val json = ImportExportRepository.exportWorkspaceToString(state)
            PlatformStorage.putString(STORAGE_KEY, json)
        }
    }

    fun load(state: AppState) {
        runCatching {
            val json = PlatformStorage.getString(STORAGE_KEY) ?: return
            val workspace = ImportExportRepository.decodeWorkspace(json)
            ImportExportRepository.replaceWorkspaceState(state, workspace)
        }
    }
}
