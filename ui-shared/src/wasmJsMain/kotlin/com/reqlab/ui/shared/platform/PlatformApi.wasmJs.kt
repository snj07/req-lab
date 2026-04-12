package com.reqlab.ui.shared.platform

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerInput
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

/**
 * H-6 fix: File picker JS now listens for the 'cancel' event so that when the
 * user dismisses the dialog without selecting a file, __reqlab_fdone is set to
 * true immediately. The Kotlin polling coroutine checks __reqlab_fdone and exits
 * on the very next 100 ms tick, eliminating the 60-second resource leak.
 */
private fun jsPickFileStart(): JsAny? = js(
    "(() => { window.__reqlab_fc = null; window.__reqlab_fr = false; window.__reqlab_fdone = false; var i = document.createElement('input'); i.type = 'file'; i.accept = '.json,application/json'; i.onchange = function() { var f = i.files[0]; if (f) { var r = new FileReader(); r.onload = function() { window.__reqlab_fc = r.result; window.__reqlab_fr = true; window.__reqlab_fdone = true; }; r.onerror = function() { window.__reqlab_fdone = true; }; r.readAsText(f); } else { window.__reqlab_fdone = true; } }; i.addEventListener('cancel', function() { window.__reqlab_fdone = true; }); i.click(); })()"
)

private fun jsPickBinaryFileStart(): JsAny? = js(
    "(() => { window.__reqlab_bf = null; window.__reqlab_bn = null; window.__reqlab_br = false; window.__reqlab_bdone = false; var i = document.createElement('input'); i.type = 'file'; i.onchange = function() { var f = i.files[0]; if (f) { var r = new FileReader(); r.onload = function() { var result = String(r.result || ''); var comma = result.indexOf(','); window.__reqlab_bf = comma >= 0 ? result.substring(comma + 1) : result; window.__reqlab_bn = f.name || 'upload.bin'; window.__reqlab_br = true; window.__reqlab_bdone = true; }; r.onerror = function() { window.__reqlab_bdone = true; }; r.readAsDataURL(f); } else { window.__reqlab_bdone = true; } }; i.addEventListener('cancel', function() { window.__reqlab_bdone = true; }); i.click(); })()"
)

private fun jsFileReady(): JsBoolean = js("window.__reqlab_fr === true")
private fun jsFilePickDone(): JsBoolean = js("window.__reqlab_fdone === true")
private fun jsBinaryFileReady(): JsBoolean = js("window.__reqlab_br === true")
private fun jsBinaryFilePickDone(): JsBoolean = js("window.__reqlab_bdone === true")

private fun jsGetFileContent(): JsString? = js("window.__reqlab_fc")
private fun jsGetBinaryFileContent(): JsString? = js("window.__reqlab_bf")
private fun jsGetBinaryFileName(): JsString? = js("window.__reqlab_bn")

private fun jsClearFileContent(): JsAny? = js("window.__reqlab_fc = null")
private fun jsClearFileReady(): JsAny? = js("window.__reqlab_fr = false")
private fun jsClearFilePickDone(): JsAny? = js("window.__reqlab_fdone = false")
private fun jsClearBinaryFileContent(): JsAny? = js("window.__reqlab_bf = null")
private fun jsClearBinaryFileName(): JsAny? = js("window.__reqlab_bn = null")
private fun jsClearBinaryFileReady(): JsAny? = js("window.__reqlab_br = false")
private fun jsClearBinaryFilePickDone(): JsAny? = js("window.__reqlab_bdone = false")

/**
 * H-5 fix: Set the CSS cursor style on the Compose canvas element directly.
 * Used by [platformResizeCursorStyle] to show col-resize/row-resize cursors
 * on Web where PointerIcon.Default cannot be replaced with a CSS cursor type.
 */
private fun jsSetCanvasCursor(cursor: JsString): JsAny? =
    js("(function(c) { var els = document.querySelectorAll('canvas'); els.forEach(function(el) { el.style.cursor = c; }); })(cursor)")

// ── Actual implementations ──────────────────────────────────────

actual fun generateUuid(): String = jsRandomUuid().toString()

actual fun currentTimeMillis(): Long =
    Clock.System.now().toEpochMilliseconds()

actual fun copyToClipboard(text: String) {
    jsClipboard(text.toJsString())
}

// On wasm/browser, clipboard read requires an async navigator.clipboard.readText() call
// and cannot be done synchronously. The paste interception path is desktop-only;
// return null here so the desktop-optimised code path is never reached on web.
actual fun readFromClipboard(): String? = null

actual fun formatTimestamp(epochMillis: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:" +
        "${local.minute.toString().padStart(2, '0')}:" +
        "${local.second.toString().padStart(2, '0')}"
}

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default

actual val horizontalResizeCursor: PointerIcon = PointerIcon.Default
actual val verticalResizeCursor: PointerIcon   = PointerIcon.Default

/**
 * H-5: On wasmJs, PointerIcon cannot be set to CSS cursor strings via the
 * standard Compose API. This modifier applies the cursor override directly on
 * the Compose canvas element via JS whenever the pointer enters/exits bounds.
 */
actual fun Modifier.platformResizeCursorStyle(isHorizontal: Boolean): Modifier {
    val cursorCSS = if (isHorizontal) "col-resize" else "row-resize"
    return this.pointerInput(isHorizontal) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                when (event.type) {
                    PointerEventType.Enter -> jsSetCanvasCursor(cursorCSS.toJsString())
                    PointerEventType.Exit  -> jsSetCanvasCursor("default".toJsString())
                    else -> {}
                }
            }
        }
    }
}

@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
actual fun pickFileForImport(onResult: (String) -> Unit) {
    jsPickFileStart()
    GlobalScope.launch {
        // H-6 fix: Exit as soon as __reqlab_fdone is true — set immediately by
        // the JS 'cancel' event or on file-read completion, so the coroutine
        // doesn't block for up to 60 s when the user dismisses the dialog.
        // Safety cap of 3 000 iterations ≈ 5 minutes.
        repeat(3000) {
            delay(100)
            if (jsFilePickDone().toBoolean()) {
                if (jsFileReady().toBoolean()) {
                    val content = jsGetFileContent()
                    jsClearFileContent()
                    jsClearFileReady()
                    jsClearFilePickDone()
                    if (content != null) onResult(content.toString())
                } else {
                    // User cancelled — just clean up without calling onResult.
                    jsClearFilePickDone()
                }
                return@launch
            }
        }
    }
}

@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
actual fun pickBinaryFileForRequest(onResult: (PickedBinaryFile) -> Unit) {
    jsPickBinaryFileStart()
    GlobalScope.launch {
        // H-6 fix: Same cancel-detection applied to the binary file picker.
        repeat(3000) {
            delay(100)
            if (jsBinaryFilePickDone().toBoolean()) {
                if (jsBinaryFileReady().toBoolean()) {
                    val content = jsGetBinaryFileContent()?.toString().orEmpty()
                    val name = jsGetBinaryFileName()?.toString().orEmpty().ifBlank { "upload.bin" }
                    jsClearBinaryFileContent()
                    jsClearBinaryFileName()
                    jsClearBinaryFileReady()
                    jsClearBinaryFilePickDone()
                    if (content.isNotBlank()) {
                        onResult(PickedBinaryFile(name = name, base64Content = content))
                    }
                } else {
                    jsClearBinaryFilePickDone()
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
