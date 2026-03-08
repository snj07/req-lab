package com.reqlab.ui.shared.platform

import androidx.compose.ui.input.pointer.PointerIcon
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// ── JS interop helpers (top-level js() single-expression bodies) ──

private fun jsRandomUuid(): JsString = js("crypto.randomUUID()")

private fun jsClipboard(text: JsString): JsAny? =
    js("navigator.clipboard.writeText(text)")

private fun jsStorageSet(key: JsString, value: JsString): JsAny? =
    js("localStorage.setItem(key, value)")

private fun jsStorageGet(key: JsString): JsString? =
    js("localStorage.getItem(key)")

private fun jsStorageRemove(key: JsString): JsAny? =
    js("localStorage.removeItem(key)")

private fun jsDownloadFile(content: JsString, filename: JsString): JsAny? = js(
    "(() => { var b = new Blob([content], {type:'application/json'}); var u = URL.createObjectURL(b); var a = document.createElement('a'); a.href = u; a.download = filename; document.body.appendChild(a); a.click(); document.body.removeChild(a); URL.revokeObjectURL(u); })()"
)

private fun jsPickFileStart(): JsAny? = js(
    "(() => { window.__reqlab_fc = null; window.__reqlab_fr = false; var i = document.createElement('input'); i.type = 'file'; i.accept = '.json,application/json'; i.onchange = function() { var f = i.files[0]; if (f) { var r = new FileReader(); r.onload = function() { window.__reqlab_fc = r.result; window.__reqlab_fr = true; }; r.readAsText(f); } }; i.click(); })()"
)

private fun jsFileReady(): JsBoolean = js("window.__reqlab_fr === true")

private fun jsGetFileContent(): JsString? = js("window.__reqlab_fc")

private fun jsClearFileContent(): JsAny? = js("window.__reqlab_fc = null")
private fun jsClearFileReady(): JsAny? = js("window.__reqlab_fr = false")

// ── Actual implementations ──────────────────────────────────────

actual fun generateUuid(): String = jsRandomUuid().toString()

actual fun currentTimeMillis(): Long =
    Clock.System.now().toEpochMilliseconds()

actual fun copyToClipboard(text: String) {
    jsClipboard(text.toJsString())
}

actual fun formatTimestamp(epochMillis: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:" +
        "${local.minute.toString().padStart(2, '0')}:" +
        "${local.second.toString().padStart(2, '0')}"
}

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default

actual val horizontalResizeCursor: PointerIcon = PointerIcon.Default
actual val verticalResizeCursor: PointerIcon = PointerIcon.Default

@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
actual fun pickFileForImport(onResult: (String) -> Unit) {
    jsPickFileStart()
    GlobalScope.launch {
        repeat(600) {
            delay(100)
            if (jsFileReady().toBoolean()) {
                val content = jsGetFileContent()
                jsClearFileContent()
                jsClearFileReady()
                if (content != null) {
                    onResult(content.toString())
                }
                return@launch
            }
        }
    }
}

actual fun saveFileForExport(content: String, defaultFilename: String) {
    jsDownloadFile(content.toJsString(), defaultFilename.toJsString())
}

actual object PlatformStorage {
    actual fun putString(key: String, value: String) {
        jsStorageSet(key.toJsString(), value.toJsString())
    }

    actual fun getString(key: String): String? {
        return jsStorageGet(key.toJsString())?.toString()
    }

    actual fun remove(key: String) {
        jsStorageRemove(key.toJsString())
    }
}
