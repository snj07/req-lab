@file:JsModule("@codemirror/state")
@file:JsNonModule

package com.reqlab.editor.platform.web.interop

external class EditorStateConfig {
    var doc: String?
}

external class EditorStateCM {
    val doc: dynamic
    companion object {
        fun create(config: EditorStateConfig = definedExternally): EditorStateCM
    }
}
