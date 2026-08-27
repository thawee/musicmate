package apincer.android.mmate.ui.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Adds a smooth horizontal gradient alpha fade mask to the start and/or end of a composable.
 * Essential for eliminating harsh cutoff edges on scrolling marquee text.
 */
fun Modifier.fadingEdge(
    startWidth: Dp = 0.dp,
    endWidth: Dp = 10.dp
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val startPx = startWidth.toPx()
        val endPx = endWidth.toPx()

        if (startPx > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black,
                    startX = 0f,
                    endX = startPx
                ),
                blendMode = BlendMode.DstIn
            )
        }

        if (endPx > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Black,
                    1f to Color.Transparent,
                    startX = size.width - endPx,
                    endX = size.width
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }
