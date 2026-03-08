package com.reqlab.ui.shared.components

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
import com.reqlab.ui.shared.theme.ReqLabColors

/**
 * Shown when the user tries to close multiple tabs where at least one has unsaved changes.
 * Offers Save All / Discard All / Cancel.
 */
@Composable
fun DirtyMultiCloseDialog(
    dirtyCount: Int,
    onSaveAll: () -> Unit,
    onDiscardAll: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        Box(
            modifier = Modifier
                .widthIn(min = 400.dp, max = 540.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ReqLabColors.Surface)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(12.dp))
                .padding(24.dp)
                .testTag("dirty-multi-close-dialog"),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Unsaved Changes",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ReqLabColors.OnSurface,
                )
                Text(
                    text = "$dirtyCount request${if (dirtyCount == 1) "" else "s"} " +
                        "have unsaved changes. What would you like to do?",
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(ReqLabColors.SurfaceContainer)
                            .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp))
                            .clickable(onClick = onCancel)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("dirty-multi-cancel"),
                    ) {
                        Text("Cancel", fontSize = 13.sp, color = ReqLabColors.OnSurfaceVariant)
                    }

                    // Discard All
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ReqLabColors.SurfaceContainer)
                            .border(1.dp, ReqLabColors.Error.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable(onClick = onDiscardAll)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("dirty-multi-discard"),
                    ) {
                        Text("Discard All", fontSize = 13.sp, color = ReqLabColors.Error, fontWeight = FontWeight.Medium)
                    }

                    // Save All
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ReqLabColors.Primary)
                            .clickable(onClick = onSaveAll)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("dirty-multi-save-all"),
                    ) {
                        Text("Save All", fontSize = 13.sp, color = ReqLabColors.OnPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
