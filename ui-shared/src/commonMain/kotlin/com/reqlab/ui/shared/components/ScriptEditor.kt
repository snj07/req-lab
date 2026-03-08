package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.ui.shared.theme.CodeFontFamily
import com.reqlab.ui.shared.theme.ReqLabColors

/**
 * A simple multi-line script editor used for Pre-request and Test script tabs.
 *
 * @param script         current script content
 * @param onScriptChanged callback for changes
 * @param title          human-readable label (e.g. "Pre-request Script")
 */
@Composable
fun ScriptEditor(
    script: String,
    onScriptChanged: (String) -> Unit,
    title: String,
) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = ReqLabColors.OnSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        BasicTextField(
            value = script,
            onValueChange = onScriptChanged,
            textStyle = TextStyle(color = ReqLabColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
            cursorBrush = SolidColor(ReqLabColors.Primary),
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(ReqLabColors.SurfaceContainer)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp))
                .padding(12.dp),
            decorationBox = { inner ->
                if (script.isEmpty()) {
                    Text(
                        text = "// Write your ${title.lowercase()} here…",
                        color = ReqLabColors.OnSurfaceDim,
                        fontSize = 13.sp,
                        fontFamily = CodeFontFamily,
                    )
                }
                inner()
            },
        )
    }
}
