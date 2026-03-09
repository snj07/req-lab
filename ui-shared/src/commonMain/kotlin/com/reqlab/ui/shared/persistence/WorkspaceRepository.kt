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
            if (looksLikeLegacySeededDemo(workspace)) {
                PlatformStorage.remove(STORAGE_KEY)
                return
            }
            ImportExportRepository.replaceWorkspaceState(state, workspace)
        }
    }

    private fun looksLikeLegacySeededDemo(workspace: ReqLabWorkspaceDto): Boolean {
        val hasDemoCollections = workspace.collections.size == 2 &&
            workspace.collections.any { it.name == "Users API" } &&
            workspace.collections.any { it.name == "Auth" }

        val hasDemoEnvironments = workspace.environments.size == 3 &&
            workspace.environments.any { it.name == "Development" } &&
            workspace.environments.any { it.name == "Staging" } &&
            workspace.environments.any { it.name == "Production" }

        val hasDemoGlobals = workspace.globalVariables.size == 2 &&
            workspace.globalVariables.any { it.name == "appName" && it.variables["value"] == "ReqLab" } &&
            workspace.globalVariables.any { it.name == "apiVersion" && it.variables["value"] == "v1" }

        val hasDemoHistory = workspace.history.size == 3 &&
            workspace.history.any { it.name == "List users" && it.method.equals("GET", ignoreCase = true) } &&
            workspace.history.any { it.name == "Create user" && it.method.equals("POST", ignoreCase = true) } &&
            workspace.history.any { it.name == "Delete user" && it.method.equals("DELETE", ignoreCase = true) }

        return hasDemoCollections && hasDemoEnvironments && hasDemoGlobals && hasDemoHistory
    }
}
