package mx.ita.vitalsense.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mx.ita.vitalsense.ui.theme.NeomorphicBackground
import mx.ita.vitalsense.ui.theme.NeomorphicDarkShadow
import mx.ita.vitalsense.ui.theme.NeomorphicLightShadow

/**
 * Draws a single colored, blurred shadow offset from the composable bounds.
 * Uses BlurMaskFilter — renders correctly on hardware-accelerated canvases
 * (API 28+ guaranteed; API 24-27 degrades gracefully to solid shadow).
 */
private fun Modifier.coloredShadow(
    color: Color,
    cornerRadius: Dp,
    blurRadius: Dp,
    offsetX: Dp,
    offsetY: Dp,
): Modifier = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint()
        val fp = paint.asFrameworkPaint()
        fp.isAntiAlias = true
        fp.color = color.toArgb()
        if (blurRadius != 0.dp) {
            fp.maskFilter = BlurMaskFilter(blurRadius.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
        val cr = cornerRadius.toPx()
        canvas.drawRoundRect(
            left = offsetX.toPx(),
            top = offsetY.toPx(),
            right = size.width + offsetX.toPx(),
            bottom = size.height + offsetY.toPx(),
            radiusX = cr,
            radiusY = cr,
            paint = paint,
        )
    }
}

/**
 * Neumorphic (Soft UI) card.
 * Light shadow emits from top-left, dark shadow from bottom-right,
 * both on the same #F0F2F5 background — creating the extruded-surface effect.
 */
@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            // Light shadow — top-left
            .coloredShadow(
                color = NeomorphicLightShadow.copy(alpha = 0.85f),
                cornerRadius = cornerRadius,
                blurRadius = 12.dp,
                offsetX = (-6).dp,
                offsetY = (-6).dp,
            )
            // Dark shadow — bottom-right
            .coloredShadow(
                color = NeomorphicDarkShadow.copy(alpha = 0.55f),
                cornerRadius = cornerRadius,
                blurRadius = 12.dp,
                offsetX = 6.dp,
                offsetY = 6.dp,
            )
            .clip(shape)
            .background(NeomorphicBackground),
    ) {
        content()
    }
}
