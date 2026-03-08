package com.reqlab.ui.shared.platform

import androidx.compose.ui.input.pointer.PointerIcon
import kotlinx.coroutines.CoroutineDispatcher

// ── Identity / Time ─────────────────────────────────────────────

/** Platform-safe UUID generation. */
expect fun generateUuid(): String

/** Platform-safe epoch milliseconds. */
expect fun currentTimeMillis(): Long

// ── Clipboard ───────────────────────────────────────────────────

/** Copy [text] to the system clipboard. */
expect fun copyToClipboard(text: String)

// ── Formatting ──────────────────────────────────────────────────

/** Format an epoch-millis timestamp as "HH:mm:ss" local time. */
expect fun formatTimestamp(epochMillis: Long): String

// ── Coroutine dispatchers ───────────────────────────────────────

/** IO dispatcher (Dispatchers.IO on JVM, Dispatchers.Default on wasm). */
expect val ioDispatcher: CoroutineDispatcher

// ── Pointer icons ───────────────────────────────────────────────

/** Horizontal resize (col-resize) cursor. */
expect val horizontalResizeCursor: PointerIcon

/** Vertical resize (row-resize) cursor. */
expect val verticalResizeCursor: PointerIcon

// ── File I/O ────────────────────────────────────────────────────

/**
 * Pick a file from the filesystem and deliver its text content to [onResult].
 * On desktop this opens a JFileChooser; on web it triggers an <input type=file>.
 */
expect fun pickFileForImport(onResult: (String) -> Unit)

/**
 * Save [content] as a file with the given [defaultFilename].
 * On desktop this opens a save dialog; on web it triggers a browser download.
 */
expect fun saveFileForExport(content: String, defaultFilename: String)

// ── Storage ─────────────────────────────────────────────────────

/** Simple key-value string storage (Preferences on JVM, localStorage on web). */
expect object PlatformStorage {
    fun putString(key: String, value: String)
    fun getString(key: String): String?
    fun remove(key: String)
}
