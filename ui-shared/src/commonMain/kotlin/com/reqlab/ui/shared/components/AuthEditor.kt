package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.core.model.AuthType
import com.reqlab.ui.shared.i18n.Strings
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
    val selectableAuthTypes = AuthType.entries.filterNot { it == AuthType.OAUTH2 }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Auth-type selector
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            selectableAuthTypes.forEach { at ->
                val selected = at == tab.authType
                Text(
                    text = at.name.replace('_', ' '),
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) ReqLabColors.Primary else ReqLabColors.OnSurfaceDim,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) ReqLabColors.SelectedItem else Color.Transparent)
                        .clickable {
                            if (at != tab.authType) {
                                tab.authType = at
                                onDirty()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        // Type-specific fields
        when (tab.authType) {
            AuthType.NONE -> {
                Text(Strings.t("no_authentication"), color = ReqLabColors.OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
            }
            AuthType.BASIC -> {
                LabeledTextField(Strings.t("username"), tab.authUsername, state = state) { tab.authUsername = it; onDirty() }
                // H-4: Use a password field with show/hide toggle so the password is masked
                PasswordLabeledTextField(Strings.t("password"), tab.authPassword, state = state) { tab.authPassword = it; onDirty() }
            }
            AuthType.BEARER, AuthType.JWT -> {
                LabeledTextField(Strings.t("token"), tab.authToken, state = state) { tab.authToken = it; onDirty() }
            }
            AuthType.API_KEY -> {
                LabeledTextField(Strings.t("key_upper"), tab.authApiKey, state = state) { tab.authApiKey = it; onDirty() }
                LabeledTextField(Strings.value, tab.authApiValue, state = state) { tab.authApiValue = it; onDirty() }
            }
            AuthType.OAUTH2 -> {
                Text(Strings.t("oauth2_coming_soon"), color = ReqLabColors.OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
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

/**
 * H-4: Password field with a reveal/hide eye toggle.
 * Characters are masked by default; clicking the eye icon toggles plaintext display.
 * This mirrors the "Secret" toggle in EnvironmentEditDialog.
 * Uses BasicTextField directly (not VariableAwareTextField) because
 * PasswordVisualTransformation is incompatible with the annotation-based variable highlighting.
 */
@Composable
fun PasswordLabeledTextField(
    label: String,
    value: String,
    state: AppState,
    onValueChange: (String) -> Unit,
) {
    var showPassword by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ReqLabColors.OnSurfaceVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ReqLabColors.SurfaceContainer)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(6.dp))
                .testTag("auth-password-field"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = ReqLabColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
                cursorBrush = SolidColor(ReqLabColors.Primary),
                visualTransformation = if (showPassword) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                decorationBox = { inner ->
                    if (value.isEmpty() && !showPassword) {
                        Text(
                            text = "••••••••",
                            color = ReqLabColors.OnSurfaceDim,
                            fontSize = 13.sp,
                        )
                    } else if (value.isEmpty()) {
                        Text(
                            text = "Enter $label…",
                            color = ReqLabColors.OnSurfaceDim,
                            fontSize = 13.sp,
                        )
                    }
                    inner()
                },
            )
            IconButton(
                onClick = { showPassword = !showPassword },
                modifier = Modifier.size(36.dp).padding(end = 4.dp),
            ) {
                Icon(
                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showPassword) "Hide password" else "Show password",
                    tint = ReqLabColors.OnSurfaceDim,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
