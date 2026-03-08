package com.reqlab.ui.shared.components

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.core.model.AuthType
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.RequestTabState
import com.reqlab.ui.shared.theme.CodeFontFamily
import com.reqlab.ui.shared.theme.ReqLabColors

/**
 * Editor panel for the request authentication configuration.
 * Shows auth-type selector chips and type-specific input fields.
 */
@Composable
fun AuthEditor(tab: RequestTabState, state: AppState, onDirty: () -> Unit) {
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
                LabeledTextField("Username", tab.authUsername, state = state) { tab.authUsername = it; onDirty() }
                LabeledTextField("Password", tab.authPassword, state = state) { tab.authPassword = it; onDirty() }
            }
            AuthType.BEARER, AuthType.JWT -> {
                LabeledTextField("Token", tab.authToken, state = state) { tab.authToken = it; onDirty() }
            }
            AuthType.API_KEY -> {
                LabeledTextField("Key",   tab.authApiKey, state = state)   { tab.authApiKey   = it; onDirty() }
                LabeledTextField("Value", tab.authApiValue, state = state) { tab.authApiValue = it; onDirty() }
            }
            AuthType.OAUTH2 -> {
                Text("OAuth 2.0 configuration (coming soon)", color = ReqLabColors.OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ── Labeled text field helper ───────────────────────────────────

@Composable
fun LabeledTextField(
    label: String,
    value: String,
    state: AppState,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ReqLabColors.OnSurfaceVariant)
        VariableAwareTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = ReqLabColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ReqLabColors.SurfaceContainer)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            placeholder = "Enter $label…",
        )
    }
}
