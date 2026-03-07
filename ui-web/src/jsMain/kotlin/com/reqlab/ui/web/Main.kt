package com.reqlab.ui.web

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.renderComposable

fun main() {
    renderComposable(rootElementId = "root") {
        WebShell()
    }
}

@Composable
private fun WebShell() {
    Div({ style { padding(16.px) } }) {
        H1 { Text("ReqLab") }
        P { Text("Kotlin Multiplatform API Client shell initialized.") }
    }
}
