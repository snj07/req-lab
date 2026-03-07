package com.reqlab.ui.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.reqlab.ui.desktop.theme.ReqLabColors

/**
 * Simple yes/no confirmation dialog.
 *
 * @param title      Short heading, e.g. "Delete request?"
 * @param message    Body text with details.
 * @param confirmLabel Label for the destructive/confirm button (default: "Delete").
 * @param onConfirm  Called when user confirms.
 * @param onDismiss  Called when user cancels or dismisses.
 */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    confirmLabel: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 480.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ReqLabColors.Surface)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(12.dp))
                .padding(24.dp)
                .testTag("confirm-dialog"),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ReqLabColors.OnSurface,
                )
                Text(
                    message,
                    fontSize = 13.sp,
                    color = ReqLabColors.OnSurfaceVariant,
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    // Cancel
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ReqLabColors.SurfaceContainer)
                            .border(1.dp, ReqLabColors.Border, RoundedCornerShape(6.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("confirm-cancel-button"),
                    ) {
                        Text("Cancel", fontSize = 13.sp, color = ReqLabColors.OnSurface)
                    }

                    // Confirm (destructive)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ReqLabColors.Error.copy(alpha = 0.15f))
                            .border(1.dp, ReqLabColors.Error.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .clickable(onClick = onConfirm)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("confirm-ok-button"),
                    ) {
                        Text(
                            confirmLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = ReqLabColors.Error,
                        )
                    }
                }
            }
        }
    }
}
