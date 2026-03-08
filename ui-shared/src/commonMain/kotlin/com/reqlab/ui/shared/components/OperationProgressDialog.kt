package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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

@Composable
fun OperationProgressDialog(
    title: String,
    message: String,
    onCancel: () -> Unit,
) {
    Dialog(onDismissRequest = { }) {
        Column(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 520.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ReqLabColors.Surface)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(12.dp))
                .padding(20.dp)
                .testTag("operation-progress-dialog"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, color = ReqLabColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.testTag("operation-progress-spinner"),
                    color = ReqLabColors.Primary,
                    strokeWidth = 2.5.dp,
                )
                Text(message, color = ReqLabColors.OnSurfaceVariant, fontSize = 13.sp)
            }

            Spacer(Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = "Cancel",
                    color = ReqLabColors.Error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ReqLabColors.Error.copy(alpha = 0.14f))
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("operation-progress-cancel"),
                )
            }
        }
    }
}
