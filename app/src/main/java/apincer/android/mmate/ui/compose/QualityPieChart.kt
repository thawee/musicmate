package apincer.android.mmate.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat

data class PieEntry(
    val label: String,
    val value: Float,
    val color: Int
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QualityPieChart(
    entries: List<PieEntry>,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return

    val totalValue = entries.sumOf { it.value.toDouble() }.toFloat()
    if (totalValue == 0f) return

    // Calculate Lossless Ratio
    val losslessValue = entries.filter {
        !it.label.contains("Standard", ignoreCase = true) &&
        !it.label.contains("Lossy", ignoreCase = true) &&
        !it.label.contains("MP3", ignoreCase = true)
    }.sumOf { it.value.toDouble() }.toFloat()
    val losslessPct = ((losslessValue / totalValue) * 100).toInt()

    // Kinetic Sweep-In Animation (0 -> 1)
    val sweepProgress = remember { Animatable(0f) }
    LaunchedEffect(entries) {
        sweepProgress.snapTo(0f)
        sweepProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 900,
                easing = FastOutSlowInEasing
            )
        )
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val activeEntry = selectedIndex?.let { entries.getOrNull(it) }

    val displayCount = activeEntry?.value?.toInt() ?: totalValue.toInt()
    val displayLabel = activeEntry?.label?.uppercase() ?: "TOTAL TRACKS"
    val displaySub = if (activeEntry != null) {
        val pct = ((activeEntry.value / totalValue) * 100).toInt()
        "$pct% OF LIBRARY"
    } else {
        "$losslessPct% LOSSLESS"
    }
    val activeColor = if (activeEntry != null) Color(activeEntry.color) else Color(0xFFFFD700)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── 1. Donut Chart + Center Lossless HUD ─────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(190.dp)
                    .pointerInput(entries) {
                        detectTapGestures { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val touchVector = offset - center
                            val distance = touchVector.getDistance()
                            val chartSize = minOf(size.width, size.height) * 0.92f
                            val outerRadius = chartSize / 2f
                            val innerRadius = outerRadius * 0.60f
                            if (distance in innerRadius..outerRadius * 1.15f) {
                                var touchAngle = Math.toDegrees(Math.atan2(touchVector.y.toDouble(), touchVector.x.toDouble())).toFloat()
                                touchAngle = (touchAngle + 90f + 360f) % 360f // normalize from -90 deg (top)
                                var currentAngle = 0f
                                for (i in entries.indices) {
                                    val sweep = (entries[i].value / totalValue) * 360f
                                    if (touchAngle in currentAngle..(currentAngle + sweep)) {
                                        selectedIndex = if (selectedIndex == i) null else i
                                        break
                                    }
                                    currentAngle += sweep
                                }
                            } else {
                                selectedIndex = null
                            }
                        }
                    }
            ) {
                val chartSize = minOf(size.width, size.height) * 0.92f
                val baseThickness = chartSize * 0.17f
                val radius = (chartSize - baseThickness) / 2f
                val topLeft = Offset(
                    (size.width - radius * 2) / 2f,
                    (size.height - radius * 2) / 2f
                )
                val canvasSize = Size(radius * 2, radius * 2)

                // Background track track
                drawArc(
                    color = Color(0x14FFFFFF),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = canvasSize,
                    style = Stroke(width = baseThickness, cap = StrokeCap.Round)
                )

                var startAngle = -90f
                val gapAngle = 2.5f
                val currentMaxSweep = 360f * sweepProgress.value

                for (i in entries.indices) {
                    val entry = entries[i]
                    val sliceSweep = (entry.value / totalValue) * 360f
                    val isSelected = (selectedIndex == i)
                    val thickness = if (isSelected) baseThickness * 1.25f else baseThickness
                    val sliceColor = if (isSelected) {
                        Color(entry.color)
                    } else if (selectedIndex != null) {
                        Color(entry.color).copy(alpha = 0.40f)
                    } else {
                        Color(entry.color)
                    }

                    if (currentMaxSweep > startAngle + 90f) {
                        val availableSweep = (currentMaxSweep - (startAngle + 90f)).coerceIn(0f, sliceSweep)
                        if (availableSweep > gapAngle) {
                            drawArc(
                                color = sliceColor,
                                startAngle = startAngle + gapAngle / 2,
                                sweepAngle = availableSweep - gapAngle,
                                useCenter = false,
                                topLeft = topLeft,
                                size = canvasSize,
                                style = Stroke(width = thickness, cap = StrokeCap.Round)
                            )
                        }
                    }
                    startAngle += sliceSweep
                }
            }

            // ── Center HUD Readout ──────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = displayLabel,
                    color = Color(0xFF9E9E9E),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = DecimalFormat("#,###").format(displayCount),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    color = activeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.5.dp, activeColor.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = displaySub,
                        color = activeColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ── 2. Rich Interactive Pill Legend ──────────────────────────────────
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (i in entries.indices) {
                val entry = entries[i]
                val isSelected = (selectedIndex == i)
                val pct = if (totalValue > 0) ((entry.value / totalValue) * 100).toInt() else 0
                val formattedCount = DecimalFormat("#,###").format(entry.value.toInt())

                Surface(
                    color = if (isSelected) Color(entry.color).copy(alpha = 0.22f) else Color(0x12FFFFFF),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Color(entry.color) else Color(0x1AFFFFFF)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            selectedIndex = if (selectedIndex == i) null else i
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(entry.color))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = entry.label,
                            color = if (isSelected) Color.White else Color(0xFFDDDDDD),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "• $formattedCount ($pct%)",
                            color = if (isSelected) Color(entry.color) else Color(0xFF8E8E93),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

