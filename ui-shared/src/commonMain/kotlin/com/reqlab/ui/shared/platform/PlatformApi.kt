package com.reqlab.ui.shared.platform

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import kotlinx.coroutines.CoroutineDispatcher

data class PickedBinaryFile(
    val name: String,
    val base64Content: String,
)

// ── Identity / Time ─────────────────────────────────────────────

/** Platform-safe UUID generation. */
expect fun generateUuid(): String

/** Platform-safe epoch milliseconds. */
expect fun currentTimeMillis(): Long

// ── Clipboard ───────────────────────────────────────────────────

/** Copy [text] to the system clipboard. */
expect fun copyToClipboard(text: String)

/**
 * Read plain text from the system clipboard synchronously.
 * Returns null if the clipboard has no text content or is unavailable.
 * On desktop this accesses the AWT Toolkit clipboard on the calling thread —
 * callers must ensure this is called from a background coroutine dispatcher.
 */
expect fun readFromClipboard(): String?

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

/**
 * H-5 fix: Platform-specific modifier that applies the correct CSS resize cursor
 * on web (wasmJs) where PointerIcon.Default cannot be replaced with a CSS cursor type.
 * On desktop this is a no-op because [horizontalResizeCursor]/[verticalResizeCursor]
 * already map to the correct AWT cursor via [pointerHoverIcon].
 */
expect fun Modifier.platformResizeCursorStyle(isHorizontal: Boolean): Modifier

// ── File I/O ────────────────────────────────────────────────────

/**
 * Pick a file from the filesystem and deliver its text content to [onResult].
 * On desktop this opens a JFileChooser; on web it triggers an <input type=file>.
 */
expect fun pickFileForImport(onResult: (String) -> Unit)

/**
 * Pick any binary/text file from the filesystem and deliver filename + base64 bytes.
 */
expect fun pickBinaryFileForRequest(onResult: (PickedBinaryFile) -> Unit)

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
