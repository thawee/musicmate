package apincer.android.mmate.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat

data class PieEntry(
    val label: String,
    val value: Float,
    val color: Int
)

@Composable
fun QualityPieChart(
    entries: List<PieEntry>,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return

    val totalValue = entries.sumOf { it.value.toDouble() }.toFloat()
    if (totalValue == 0f) return

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Chart Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val chartSize = minOf(size.width, size.height) * 0.9f
                val thickness = chartSize * 0.20f
                val radius = (chartSize - thickness) / 2f
                val topLeft = Offset(
                    (size.width - radius * 2) / 2f,
                    (size.height - radius * 2) / 2f
                )
                val canvasSize = Size(radius * 2, radius * 2)
                
                var startAngle = -90f
                val gapAngle = 1.5f
                
                for (entry in entries) {
                    val sweepAngle = (entry.value / totalValue) * 360f
                    if (sweepAngle > gapAngle) {
                        drawArc(
                            color = Color(entry.color),
                            startAngle = startAngle + gapAngle / 2,
                            sweepAngle = sweepAngle - gapAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = canvasSize,
                            style = Stroke(width = thickness, cap = StrokeCap.Butt)
                        )
                    } else if (sweepAngle > 0) {
                        drawArc(
                            color = Color(entry.color),
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = canvasSize,
                            style = Stroke(width = thickness, cap = StrokeCap.Butt)
                        )
                    }
                    startAngle += sweepAngle
                }
            }

            // Center Text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Songs",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = DecimalFormat("#,###").format(totalValue),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Legend Area
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 4
        ) {
            for (entry in entries) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.size(10.dp)
                    ) {
                        drawRoundRect(
                            color = Color(entry.color),
                            size = size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = entry.label,
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
