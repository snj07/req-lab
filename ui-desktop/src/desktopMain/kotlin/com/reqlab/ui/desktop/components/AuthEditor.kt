package com.reqlab.ui.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.core.model.AuthType
import com.reqlab.ui.desktop.state.RequestTabState
import com.reqlab.ui.desktop.theme.CodeFontFamily
import com.reqlab.ui.desktop.theme.ReqLabColors

/**
 * Editor panel for the request authentication configuration.
 * Shows auth-type selector chips and type-specific input fields.
 */
@Composable
internal fun AuthEditor(tab: RequestTabState, onDirty: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Auth-type selector
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AuthType.entries.forEach { at ->
                val selected = at == tab.authType
                Text(
                    text = at.name.replace('_', ' '),
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) ReqLabColors.Primary else ReqLabColors.OnSurfaceDim,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) ReqLabColors.SelectedItem else Color.Transparent)
                        .clickable { tab.authType = at; onDirty() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        // Type-specific fields
        when (tab.authType) {
            AuthType.NONE -> {
                Text("No authentication", color = ReqLabColors.OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
            }
            AuthType.BASIC -> {
                LabeledTextField("Username", tab.authUsername) { tab.authUsername = it; onDirty() }
                LabeledTextField("Password", tab.authPassword) { tab.authPassword = it; onDirty() }
            }
            AuthType.BEARER, AuthType.JWT -> {
                LabeledTextField("Token", tab.authToken) { tab.authToken = it; onDirty() }
            }
            AuthType.API_KEY -> {
                LabeledTextField("Key",   tab.authApiKey)   { tab.authApiKey   = it; onDirty() }
                LabeledTextField("Value", tab.authApiValue) { tab.authApiValue = it; onDirty() }
            }
            AuthType.OAUTH2 -> {
                Text("OAuth 2.0 configuration (coming soon)", color = ReqLabColors.OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ── Labeled text field helper ───────────────────────────────────

@Composable
internal fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ReqLabColors.OnSurfaceVariant)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = ReqLabColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
            cursorBrush = SolidColor(ReqLabColors.Primary),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ReqLabColors.SurfaceContainer)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) Text("Enter $label…", color = ReqLabColors.OnSurfaceDim, fontSize = 13.sp)
                inner()
            },
        )
    }
}
