package com.reqlab.ui.desktop.persistence

import com.reqlab.ui.desktop.state.AppState
import java.io.File

object WorkspaceRepository {
    private val file = File(System.getProperty("user.home"), ".reqlab/workspace.json")

    fun save(state: AppState) {
        runCatching {
            file.parentFile?.mkdirs()
            ImportExportRepository.exportWorkspaceToFile(state, file)
        }
    }

    fun load(state: AppState) {
        runCatching {
            if (!file.exists()) return
            val workspace = ImportExportRepository.decodeWorkspace(file.readText())
            ImportExportRepository.replaceWorkspaceState(state, workspace)
        }
    }
}
