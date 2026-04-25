package com.reqlab.ui.shared.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
actual fun BrandIcon() {
    val primary = Color(0xFF5B71D6)
    val secondary = Color(0xFF3A9681)
    val symbol = Color(0xFFF0F4FF)
    val outline = Color(0x66D4D4E4)

    Canvas(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(7.dp)),
    ) {
        val w = size.width
        val h = size.height
        val stroke = size.minDimension * 0.095f

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(primary, secondary),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            ),
            cornerRadius = CornerRadius(size.minDimension * 0.22f),
        )

        drawRoundRect(
            color = outline,
            cornerRadius = CornerRadius(size.minDimension * 0.22f),
            style = Stroke(width = size.minDimension * 0.04f),
        )

        // < symbol
        drawLine(
            color = symbol,
            start = Offset(w * 0.345f, h * 0.34f),
            end = Offset(w * 0.245f, h * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = symbol,
            start = Offset(w * 0.245f, h * 0.5f),
            end = Offset(w * 0.345f, h * 0.66f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )

        // > symbol
        drawLine(
            color = symbol,
            start = Offset(w * 0.655f, h * 0.34f),
            end = Offset(w * 0.755f, h * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = symbol,
            start = Offset(w * 0.755f, h * 0.5f),
            end = Offset(w * 0.655f, h * 0.66f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )

        // Flask neck
        drawLine(
            color = symbol,
            start = Offset(w * 0.5f, h * 0.25f),
            end = Offset(w * 0.5f, h * 0.40f),
            strokeWidth = stroke * 0.72f,
            cap = StrokeCap.Round,
        )

        // Flask body
        val flask = Path().apply {
            moveTo(w * 0.5f, h * 0.40f)
            lineTo(w * 0.58f, h * 0.59f)
            cubicTo(w * 0.60f, h * 0.64f, w * 0.56f, h * 0.72f, w * 0.5f, h * 0.72f)
            cubicTo(w * 0.44f, h * 0.72f, w * 0.40f, h * 0.64f, w * 0.42f, h * 0.59f)
            close()
        }
        drawPath(
            path = flask,
            color = symbol,
            style = Stroke(
                width = stroke * 0.62f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        drawCircle(
            color = symbol.copy(alpha = 0.76f),
            radius = size.minDimension * 0.07f,
            center = Offset(w * 0.5f, h * 0.59f),
        )
    }
}
