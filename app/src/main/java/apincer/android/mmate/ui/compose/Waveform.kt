package apincer.android.mmate.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap

@Composable
fun Waveform(
    data: FloatArray?,
    modifier: Modifier = Modifier,
    barSpacing: Float = 2f
) {
    if (data == null || data.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        val barWidth = (width / data.size) - barSpacing
        
        val brush = Brush.verticalGradient(
            0.1f to Color(0xFFBB86FC),
            0.5f to Color.White,
            0.9f to Color(0xFFBB86FC),
            startY = 0f,
            endY = height
        )

        for (i in data.indices) {
            val x = i * (barWidth + barSpacing) + (barWidth / 2f)
            val valH = data[i] * (height / 2.2f)

            drawLine(
                brush = brush,
                start = Offset(x, centerY - valH),
                end = Offset(x, centerY + valH),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}
