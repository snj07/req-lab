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

@Composable
fun DirtyCloseDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        Box(
            modifier = Modifier
                .widthIn(min = 380.dp, max = 520.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ReqLabColors.Surface)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(12.dp))
                .padding(24.dp)
                .testTag("dirty-close-dialog"),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Unsaved Changes",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ReqLabColors.OnSurface,
                )
                Text(
                    text = "Request has unsaved changes. Save before closing?",
                    fontSize = 13.sp,
                    color = ReqLabColors.OnSurfaceVariant,
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ReqLabColors.SurfaceContainer)
                            .border(1.dp, ReqLabColors.Border, RoundedCornerShape(6.dp))
                            .clickable(onClick = onCancel)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("dirty-close-cancel"),
                    ) {
                        Text("Cancel", fontSize = 13.sp, color = ReqLabColors.OnSurface)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ReqLabColors.Tertiary.copy(alpha = 0.14f))
                            .border(1.dp, ReqLabColors.Tertiary.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .clickable(onClick = onDiscard)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("dirty-close-discard"),
                    ) {
                        Text("Discard", fontSize = 13.sp, color = ReqLabColors.Tertiary)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ReqLabColors.Primary)
                            .clickable(onClick = onSave)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("dirty-close-save"),
                    ) {
                        Text("Save", fontSize = 13.sp, color = ReqLabColors.OnPrimary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
