package com.reqlab.ui.shared.platform

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

actual fun formatTimestamp(epochMillis: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss")
    return sdf.format(Date(epochMillis))
}

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

actual val horizontalResizeCursor: PointerIcon = PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))

actual val verticalResizeCursor: PointerIcon = PointerIcon(Cursor(Cursor.S_RESIZE_CURSOR))

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

    actual fun putString(key: String, value: String) {
        // Preferences has a max value size of ~8KB, so split large values
        if (value.length > 7000) {
            val chunks = value.chunked(7000)
            prefs.putInt("${key}__chunks", chunks.size)
            chunks.forEachIndexed { i, chunk -> prefs.put("${key}__$i", chunk) }
        } else {
            prefs.remove("${key}__chunks")
            prefs.put(key, value)
        }
        prefs.flush()
    }

    actual fun getString(key: String): String? {
        val chunkCount = prefs.getInt("${key}__chunks", -1)
        return if (chunkCount > 0) {
            buildString { for (i in 0 until chunkCount) append(prefs.get("${key}__$i", "")) }
        } else {
            prefs.get(key, null)
        }
    }

    actual fun remove(key: String) {
        prefs.remove(key)
        val chunkCount = prefs.getInt("${key}__chunks", -1)
        if (chunkCount > 0) {
            for (i in 0 until chunkCount) prefs.remove("${key}__$i")
            prefs.remove("${key}__chunks")
        }
        prefs.flush()
    }
}
