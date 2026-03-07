package com.reqlab.ui.desktop.persistence

import com.reqlab.ui.desktop.state.AppState
import java.io.File

object CollectionsRepository {

    fun exportToFile(state: AppState, file: File) {
        ImportExportRepository.exportWorkspaceToFile(state, file)
    }

    fun importFromFile(state: AppState, file: File): Int {
        val result = ImportExportRepository.importWorkspaceFromFile(state, file)
        return result.importedCollections
    }
}
