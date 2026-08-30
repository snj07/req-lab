package com.reqlab.ui.shared.platform

/**
 * Remembers the last file-dialog folder so import/export open there next time.
 * Paths are stored as native absolute strings (Java [java.io.File.absolutePath]);
 * [directoryToOpen] ignores a stored path that no longer exists so a missing
 * drive/share on Windows or an unmounted volume on macOS/Linux falls back to
 * the OS default (user home).
 *
 * Browsers cannot set `<input type=file>` start directories; wasm ignores this.
 */
internal object FileChooserMemory {
    const val LAST_DIR_KEY = "fileChooser.lastDirectory"

    fun parentPath(path: String): String? {
        if (path.isBlank()) return null
        val trimmed = path.trimEnd('/', '\\')
        if (trimmed.isEmpty()) return null
        val slash = maxOf(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'))
        if (slash < 0) return null
        if (slash == 0) return "/"
        if (slash == 2 && trimmed.length >= 2 && trimmed[1] == ':') {
            return trimmed.substring(0, 3)
        }
        return trimmed.substring(0, slash)
    }

    fun directoryToOpen(
        stored: String?,
        existsAndIsDirectory: (String) -> Boolean,
    ): String? {
        if (stored.isNullOrBlank()) return null
        if (existsAndIsDirectory(stored)) return stored
        val parent = parentPath(stored)
        return parent?.takeIf(existsAndIsDirectory)
    }

    fun directoryToRemember(
        selectedPath: String,
        existsAndIsDirectory: (String) -> Boolean,
    ): String? {
        if (selectedPath.isBlank()) return null
        if (existsAndIsDirectory(selectedPath)) return selectedPath
        return parentPath(selectedPath)?.takeIf(existsAndIsDirectory)
    }
}
