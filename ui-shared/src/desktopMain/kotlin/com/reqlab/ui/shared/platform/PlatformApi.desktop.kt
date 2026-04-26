package com.reqlab.ui.shared.platform

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.awt.Cursor
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.util.Base64
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import java.util.prefs.Preferences
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

actual fun generateUuid(): String = UUID.randomUUID().toString()

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun copyToClipboard(text: String) {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(text), null)
}

actual fun readFromClipboard(): String? {
    return try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val data = clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor)
        data as? String
    } catch (_: Exception) {
        null
    }
}

actual fun formatTimestamp(epochMillis: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss")
    return sdf.format(Date(epochMillis))
}

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

actual val horizontalResizeCursor: PointerIcon = PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))

actual val verticalResizeCursor: PointerIcon = PointerIcon(Cursor(Cursor.S_RESIZE_CURSOR))

/**
 * H-5: Desktop already applies the correct cursor via `pointerHoverIcon(horizontalResizeCursor)`.
 * This modifier is a no-op on desktop.
 */
actual fun Modifier.platformResizeCursorStyle(isHorizontal: Boolean): Modifier = this

actual fun pickFileForImport(onResult: (String) -> Unit) {
    val chooser = JFileChooser()
    chooser.fileFilter = FileNameExtensionFilter("JSON files", "json")
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        runCatching { chooser.selectedFile.readText() }
            .onSuccess { onResult(it) }
    }
}

actual fun pickBinaryFileForRequest(onResult: (PickedBinaryFile) -> Unit) {
    val chooser = JFileChooser()
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        val selected = chooser.selectedFile
        runCatching { selected.readBytes() }
            .onSuccess { bytes ->
                val base64 = Base64.getEncoder().encodeToString(bytes)
                onResult(PickedBinaryFile(name = selected.name, base64Content = base64))
            }
    }
}

actual fun saveFileForExport(content: String, defaultFilename: String) {
    val chooser = JFileChooser()
    chooser.selectedFile = File(defaultFilename)
    chooser.fileFilter = FileNameExtensionFilter("JSON files", "json")
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        var file = chooser.selectedFile
        if (!file.name.endsWith(".json")) file = File(file.absolutePath + ".json")
        runCatching { file.writeText(content) }
    }
}

actual object PlatformStorage {
    private val prefs: Preferences =
        Preferences.userNodeForPackage(PlatformStorage::class.java)

    // Values larger than this are written to a file in ~/.reqlab/pstore/ instead of being
    // chunked into Preferences entries.  Java Preferences flushes to an XML plist on every
    // write; storing MB-scale data there creates thousands of entries and freezes the UI.
    private const val FILE_THRESHOLD = 65_536  // 64 KB
    private val pstoreDir: File
        get() = File(System.getProperty("user.home"), ".reqlab/pstore").also { it.mkdirs() }

    private fun fileForKey(key: String): File {
        val safeName = key.replace(".", "_").replace("/", "_")
        return File(pstoreDir, safeName)
    }

    actual fun putString(key: String, value: String) {
        if (value.length > FILE_THRESHOLD) {
            // Write to a plain file; mark it in Preferences so getString knows where to look.
            fileForKey(key).writeText(value)
            prefs.putBoolean("${key}__isFile", true)
            // Remove any stale chunked or direct entry.
            val oldChunks = prefs.getInt("${key}__chunks", -1)
            if (oldChunks > 0) {
                for (i in 0 until oldChunks) prefs.remove("${key}__$i")
                prefs.remove("${key}__chunks")
            }
            prefs.remove(key)
        } else {
            // Remove any file-backed entry that may have existed before.
            if (prefs.getBoolean("${key}__isFile", false)) {
                fileForKey(key).delete()
                prefs.remove("${key}__isFile")
            }
            // Preferences has a max value size of ~8 KB, so split moderate values.
            if (value.length > 7000) {
                val chunks = value.chunked(7000)
                prefs.putInt("${key}__chunks", chunks.size)
                chunks.forEachIndexed { i, chunk -> prefs.put("${key}__$i", chunk) }
            } else {
                val oldChunks = prefs.getInt("${key}__chunks", -1)
                if (oldChunks > 0) {
                    for (i in 0 until oldChunks) prefs.remove("${key}__$i")
                    prefs.remove("${key}__chunks")
                }
                prefs.put(key, value)
            }
        }
        prefs.flush()
    }

    actual fun getString(key: String): String? {
        // File-backed large value?
        if (prefs.getBoolean("${key}__isFile", false)) {
            val f = fileForKey(key)
            return if (f.exists()) f.readText() else null
        }
        // Chunked moderate value?
        val chunkCount = prefs.getInt("${key}__chunks", -1)
        return if (chunkCount > 0) {
            buildString { for (i in 0 until chunkCount) append(prefs.get("${key}__$i", "")) }
        } else {
            prefs.get(key, null)
        }
    }

    actual fun remove(key: String) {
        if (prefs.getBoolean("${key}__isFile", false)) {
            fileForKey(key).delete()
            prefs.remove("${key}__isFile")
        }
        prefs.remove(key)
        val chunkCount = prefs.getInt("${key}__chunks", -1)
        if (chunkCount > 0) {
            for (i in 0 until chunkCount) prefs.remove("${key}__$i")
            prefs.remove("${key}__chunks")
        }
        prefs.flush()
    }
}
