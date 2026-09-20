package apincer.android.mmate.ui.compose

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.*

/**
 * Audiophile Analog Needle VU Meter (Pillar 4 Flagship Experience)
 *
 * Emulates the iconic ballistic mechanics and warm illumination of legendary
 * Accuphase, McIntosh, and Studer analog master tape & power amplifier VU meters.
 *
 * Features:
 * - Dual Stereo channels (CH 1 Left & CH 2 Right) in a shared vintage chassis.
 * - ANSI standard ballistic spring-damper physics (300ms rise, ~1.5% overshoot).
 * - Calibrated -20 dB to +3 dB logarithmic scale with Red Overload Zone above 0 dB.
 * - Peak hold indicators and active overload LEDs.
 * - 3 Legendary Audiophile Themes (Tap to cycle):
 *   1. Accuphase Amber (Champagne Gold / Warm Incandescent Glow)
 *   2. McIntosh Blue (Electric Cyan-Blue / Carmine Red Needles)
 *   3. Studio Reference (Dark Slate / High-Contrast Precision)
 */
data class VUMeterPalette(
    val chassisBg: Color,
    val bezelBorder: Color,
    val glowColor: Color,
    val arcNormalColor: Color,
    val needleColor: Color,
    val accentTextColor: Color
)

enum class VUMeterTheme(val displayName: String) {
    ACCUPHASE("ACCUPHASE GOLD"),
    MCINTOSH("MCINTOSH BLUE"),
    STUDIO_SLATE("STUDIO REFERENCE")
}

@Composable
fun AnalogVUMeter(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    volume: Float = 1f,
    drScore: Int = 10,
    trackPeak: Float = 1f
) {
    val themes = remember { VUMeterTheme.values() }
    var themeIndex by remember { mutableIntStateOf(0) }
    val currentTheme = themes[themeIndex % themes.size]

    // Transient theme switch pill toast
    var showThemeBadge by remember { mutableStateOf(false) }
    LaunchedEffect(currentTheme) {
        showThemeBadge = true
        kotlinx.coroutines.delay(1800)
        showThemeBadge = false
    }

    val liveTelemetry by apincer.android.mmate.audio.AudioTelemetryManager.levels.collectAsState()
    val isLivePcm = isPlaying && liveTelemetry.isRealtime && (System.currentTimeMillis() - liveTelemetry.timestampMs < 500)

    // Ballistic physics state
    var levelL by remember { mutableFloatStateOf(0f) }
    var levelR by remember { mutableFloatStateOf(0f) }
    var peakL by remember { mutableFloatStateOf(0f) }
    var peakR by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying, volume, drScore, trackPeak) {
        var lastTime = System.nanoTime()
        var velL = 0f
        var velR = 0f
        var peakDecayTimerL = 0f
        var peakDecayTimerR = 0f

        while (isActive) {
            withFrameNanos { now ->
                val dt = ((now - lastTime) / 1_000_000_000f).coerceIn(0.004f, 0.04f)
                lastTime = now

                val targetL: Float
                val targetR: Float

                val currentLevels = apincer.android.mmate.audio.AudioTelemetryManager.levels.value
                val isPcmActive = isPlaying && currentLevels.isRealtime && (System.currentTimeMillis() - currentLevels.timestampMs < 350)

                if (isPlaying) {
                    if (isPcmActive) {
                        // True Stereo PCM RMS Telemetry from ExoPlayer AudioProcessor
                        targetL = currentLevels.levelL.coerceIn(0.0f, 1.05f)
                        targetR = currentLevels.levelR.coerceIn(0.0f, 1.05f)
                    } else {
                        val t = now / 1_000_000_000.0
                        val vol = if (volume <= 0f) 0.88f else volume.coerceIn(0.1f, 1.0f)

                        // Dynamic Range (DR) Modulation:
                        // High DR (e.g. DR14) -> dynamic swings, lower resting RMS, sharp transient peaks.
                        // Low DR (e.g. DR6) -> compressed, high sustained RMS.
                        val safeDr = drScore.coerceIn(4, 20)
                        val drFactor = (safeDr - 4f) / 16f

                        // Musical Harmonic Synthesizers (Procedural Fallback)
                        val beat1 = sin(t * 2.0 * PI * 1.85).toFloat()
                        val beat2 = cos(t * 2.0 * PI * 3.7).toFloat()
                        val swell = (sin(t * 2.0 * PI * 0.14).toFloat() * 0.5f + 0.5f)

                        // Left channel synthesis
                        val flutterL = sin(t * 21.3).toFloat() * 0.07f + sin(t * 33.7).toFloat() * 0.04f
                        val rawL = (0.40f + 0.28f * beat1 + 0.16f * beat2 + 0.16f * swell + flutterL) * vol

                        // Right channel synthesis with stereo phase decorrelation
                        val beat1R = sin(t * 2.0 * PI * 1.85 + 0.38).toFloat()
                        val beat2R = cos(t * 2.0 * PI * 3.7 - 0.28).toFloat()
                        val flutterR = sin(t * 23.9).toFloat() * 0.07f + sin(t * 28.4).toFloat() * 0.04f
                        val rawR = (0.40f + 0.28f * beat1R + 0.16f * beat2R + 0.16f * swell + flutterR) * vol

                        val baseFloor = 0.22f - 0.12f * drFactor
                        val headroom = 0.58f + 0.32f * drFactor
                        val peakMod = trackPeak.coerceIn(0.75f, 1.25f)

                        targetL = (baseFloor + rawL * headroom * peakMod).coerceIn(0.04f, 0.98f)
                        targetR = (baseFloor + rawR * headroom * peakMod).coerceIn(0.04f, 0.98f)
                    }
                } else {
                    targetL = 0f
                    targetR = 0f
                }

                // ANSI Ballistic Dynamics (spring frequency ~160, damping ~17)
                val forceL = (targetL - levelL) * 160f
                velL += (forceL - velL * 17f) * dt
                levelL = (levelL + velL * dt).coerceIn(0f, 1.05f)

                val forceR = (targetR - levelR) * 160f
                velR += (forceR - velR * 17f) * dt
                levelR = (levelR + velR * dt).coerceIn(0f, 1.05f)

                // Peak Hold Dynamics
                if (levelL > peakL) {
                    peakL = levelL
                    peakDecayTimerL = 0f
                } else {
                    peakDecayTimerL += dt
                    if (peakDecayTimerL > 0.45f) {
                        peakL = (peakL - dt * 0.5f).coerceAtLeast(levelL)
                    }
                }

                if (levelR > peakR) {
                    peakR = levelR
                    peakDecayTimerR = 0f
                } else {
                    peakDecayTimerR += dt
                    if (peakDecayTimerR > 0.45f) {
                        peakR = (peakR - dt * 0.5f).coerceAtLeast(levelR)
                    }
                }
            }

            // Power-efficiency guard: stop 120Hz frame loop once needles settle to zero during pause
            if (!isPlaying && levelL < 0.001f && levelR < 0.001f && peakL < 0.001f && peakR < 0.001f && abs(velL) < 0.001f && abs(velR) < 0.001f) {
                levelL = 0f
                levelR = 0f
                peakL = 0f
                peakR = 0f
                velL = 0f
                velR = 0f
                break
            }
        }
    }

    // Theme Color Palettes
    val palette = when (currentTheme) {
        VUMeterTheme.ACCUPHASE -> VUMeterPalette(
            chassisBg = Color(0xFF14110E),
            bezelBorder = Color(0x44FFA000),
            glowColor = Color(0x35FFA000),
            arcNormalColor = Color(0xFFFFD54F),
            needleColor = Color(0xFFFFB300),
            accentTextColor = Color(0xFFFFE082)
        )
        VUMeterTheme.MCINTOSH -> VUMeterPalette(
            chassisBg = Color(0xFF09121B),
            bezelBorder = Color(0x4400E5FF),
            glowColor = Color(0x3800B0FF),
            arcNormalColor = Color(0xFF80D8FF),
            needleColor = Color(0xFFFF334B),
            accentTextColor = Color(0xFFB3E5FC)
        )
        VUMeterTheme.STUDIO_SLATE -> VUMeterPalette(
            chassisBg = Color(0xFF161616),
            bezelBorder = Color(0x33FFFFFF),
            glowColor = Color(0x1AFFFFFF),
            arcNormalColor = Color(0xFFE0E0E0),
            needleColor = Color(0xFFFF5252),
            accentTextColor = Color(0xFFCCCCCC)
        )
    }
    val overloadRed = Color(0xFFFF3B30)

    // Reusable Paints for Text Rendering
    val scalePaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 118.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        palette.chassisBg,
                        palette.chassisBg.copy(alpha = 0.95f),
                        Color(0xFF0A0806)
                    )
                )
            )
            .border(BorderStroke(1.dp, palette.bezelBorder), RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                themeIndex++
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Telemetry Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (isPlaying) palette.needleColor else Color.Gray.copy(alpha = 0.4f))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "ANALOG VU METER",
                        color = palette.accentTextColor.copy(alpha = 0.85f),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = if (isLivePcm) "LIVE PCM • 300ms ANSI" else "ANSI BALLISTICS • 300ms",
                    color = if (isLivePcm) palette.needleColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.35f),
                    fontSize = 7.5.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }

            // Dual Channel Meters Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
            ) {
                // LEFT CHANNEL
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    SingleVUDial(
                        channelLabel = "CH 1 • LEFT",
                        levelNorm = levelL,
                        peakNorm = peakL,
                        glowColor = palette.glowColor,
                        arcNormalColor = palette.arcNormalColor,
                        overloadRed = overloadRed,
                        needleColor = palette.needleColor,
                        accentTextColor = palette.accentTextColor,
                        scalePaint = scalePaint
                    )
                }

                // RIGHT CHANNEL
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    SingleVUDial(
                        channelLabel = "CH 2 • RIGHT",
                        levelNorm = levelR,
                        peakNorm = peakR,
                        glowColor = palette.glowColor,
                        arcNormalColor = palette.arcNormalColor,
                        overloadRed = overloadRed,
                        needleColor = palette.needleColor,
                        accentTextColor = palette.accentTextColor,
                        scalePaint = scalePaint
                    )
                }
            }
        }

        // Glass Reflection Sheen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.35f)
                .background(
                    Brush.linearGradient(
                        0f to Color(0x18FFFFFF),
                        0.4f to Color(0x08FFFFFF),
                        1f to Color.Transparent
                    )
                )
        )

        // Transient Theme Badge Overlay (appears on tap)
        AnimatedVisibility(
            visible = showThemeBadge,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(350)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(BorderStroke(0.75.dp, palette.needleColor.copy(alpha = 0.8f)), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = currentTheme.displayName,
                    color = palette.needleColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}

@Composable
private fun SingleVUDial(
    channelLabel: String,
    levelNorm: Float,
    peakNorm: Float,
    glowColor: Color,
    arcNormalColor: Color,
    overloadRed: Color,
    needleColor: Color,
    accentTextColor: Color,
    scalePaint: Paint
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return@Canvas

        val cx = w / 2f
        val cy = h * 0.96f
        val dialRadius = h * 0.78f

        // Warm radial backlight illumination
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowColor, glowColor.copy(alpha = 0.1f), Color.Transparent),
                center = Offset(cx, cy * 0.45f),
                radius = dialRadius * 1.15f
            ),
            radius = dialRadius * 1.15f,
            center = Offset(cx, cy * 0.45f)
        )

        // Scale Angles: -42 deg (-20 dB) to +42 deg (+3 dB) -> Total sweep = 84 deg
        val sweepTotal = 84f
        val startAngleDeg = -42f
        val zeroVuNorm = 0.74f // 0 dB is at ~74% of the sweep
        val zeroSweep = sweepTotal * zeroVuNorm
        val overloadSweep = sweepTotal * (1f - zeroVuNorm)

        val arcRect = androidx.compose.ui.geometry.Rect(
            cx - dialRadius,
            cy - dialRadius,
            cx + dialRadius,
            cy + dialRadius
        )

        // Arc 1: Normal Region (-20 dB to 0 dB)
        drawArc(
            color = arcNormalColor.copy(alpha = 0.85f),
            startAngle = 270f + startAngleDeg,
            sweepAngle = zeroSweep,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // Arc 2: Overload Region (0 dB to +3 dB)
        drawArc(
            color = overloadRed,
            startAngle = 270f + startAngleDeg + zeroSweep,
            sweepAngle = overloadSweep,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
        )

        // Scale Ticks & Decibel Numbers
        // Major ticks: -20, -10, -5, -3, 0, +3
        val majorTicks = listOf(
            Pair(0.00f, "-20"),
            Pair(0.24f, "-10"),
            Pair(0.44f, "-5"),
            Pair(0.57f, "-3"),
            Pair(zeroVuNorm, "0"),
            Pair(1.00f, "+3")
        )
        // Minor ticks
        val minorNorms = listOf(0.12f, 0.35f, 0.65f, 0.84f, 0.92f)

        val majorLen = 6.dp.toPx()
        val minorLen = 3.5.dp.toPx()

        // Draw Minor Ticks
        for (mNorm in minorNorms) {
            val angleRad = Math.toRadians((startAngleDeg + mNorm * sweepTotal).toDouble()).toFloat()
            val isOverload = mNorm >= zeroVuNorm
            val tColor = if (isOverload) overloadRed else arcNormalColor.copy(alpha = 0.5f)

            val x1 = cx + dialRadius * sin(angleRad)
            val y1 = cy - dialRadius * cos(angleRad)
            val x2 = cx + (dialRadius - minorLen) * sin(angleRad)
            val y2 = cy - (dialRadius - minorLen) * cos(angleRad)

            drawLine(
                color = tColor,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Draw Major Ticks & Text
        scalePaint.textSize = 8.5.sp.toPx()
        val textRadius = dialRadius - majorLen - 5.5.dp.toPx()

        for ((mNorm, label) in majorTicks) {
            val angleRad = Math.toRadians((startAngleDeg + mNorm * sweepTotal).toDouble()).toFloat()
            val isOverload = mNorm >= zeroVuNorm
            val tColor = if (isOverload) overloadRed else arcNormalColor

            val x1 = cx + dialRadius * sin(angleRad)
            val y1 = cy - dialRadius * cos(angleRad)
            val x2 = cx + (dialRadius - majorLen) * sin(angleRad)
            val y2 = cy - (dialRadius - majorLen) * cos(angleRad)

            drawLine(
                color = tColor,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = if (mNorm == zeroVuNorm) 1.8.dp.toPx() else 1.3.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Text Label
            val tx = cx + textRadius * sin(angleRad)
            val ty = cy - textRadius * cos(angleRad) + (scalePaint.textSize / 2.8f)

            scalePaint.color = if (isOverload) overloadRed.toArgb() else arcNormalColor.copy(alpha = 0.9f).toArgb()
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(label, tx, ty, scalePaint)
            }
        }

        // Vintage "VU" Mark
        scalePaint.textSize = 8.sp.toPx()
        scalePaint.color = arcNormalColor.copy(alpha = 0.6f).toArgb()
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText("VU", cx, cy - dialRadius * 0.46f, scalePaint)
        }

        // Peak Hold Line Indicator
        if (peakNorm > 0.05f) {
            val peakAngleRad = Math.toRadians((startAngleDeg + peakNorm * sweepTotal).toDouble()).toFloat()
            val isOverload = peakNorm >= zeroVuNorm
            val pColor = if (isOverload) overloadRed else arcNormalColor.copy(alpha = 0.8f)
            val px1 = cx + dialRadius * sin(peakAngleRad)
            val py1 = cy - dialRadius * cos(peakAngleRad)
            val px2 = cx + (dialRadius - majorLen * 1.2f) * sin(peakAngleRad)
            val py2 = cy - (dialRadius - majorLen * 1.2f) * cos(peakAngleRad)

            drawLine(
                color = pColor,
                start = Offset(px1, py1),
                end = Offset(px2, py2),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Active Needle Pointer
        val needleAngleRad = Math.toRadians((startAngleDeg + levelNorm.coerceIn(0f, 1.05f) * sweepTotal).toDouble()).toFloat()
        val needleLen = dialRadius * 0.96f

        val tipX = cx + needleLen * sin(needleAngleRad)
        val tipY = cy - needleLen * cos(needleAngleRad)

        // Needle Shadow for Depth
        drawLine(
            color = Color.Black.copy(alpha = 0.35f),
            start = Offset(cx + 2f, cy + 2f),
            end = Offset(tipX + 2f, tipY + 2f),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Needle Body
        drawLine(
            color = needleColor,
            start = Offset(cx, cy),
            end = Offset(tipX, tipY),
            strokeWidth = 1.35.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Red Accent on Tip
        val tipAccentLen = dialRadius * 0.22f
        val accentStartX = cx + (needleLen - tipAccentLen) * sin(needleAngleRad)
        val accentStartY = cy - (needleLen - tipAccentLen) * cos(needleAngleRad)
        drawLine(
            color = overloadRed,
            start = Offset(accentStartX, accentStartY),
            end = Offset(tipX, tipY),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Pivot Hub Assembly
        drawCircle(
            color = Color(0xFF1E1A16),
            radius = 5.dp.toPx(),
            center = Offset(cx, cy)
        )
        drawCircle(
            color = needleColor,
            radius = 2.2.dp.toPx(),
            center = Offset(cx, cy)
        )

        // Overload PEAK LED Indicator (Top Right)
        val ledRadius = 3.dp.toPx()
        val ledOffset = Offset(w - 11.dp.toPx(), 9.dp.toPx())
        val isPeaking = levelNorm >= zeroVuNorm

        if (isPeaking) {
            drawCircle(
                color = overloadRed.copy(alpha = 0.4f),
                radius = ledRadius * 2.2f,
                center = ledOffset
            )
            drawCircle(
                color = overloadRed,
                radius = ledRadius,
                center = ledOffset
            )
        } else {
            drawCircle(
                color = overloadRed.copy(alpha = 0.18f),
                radius = ledRadius * 0.9f,
                center = ledOffset
            )
        }

        // Channel & Real-time Decibel Readout (Bottom Center)
        val currentDb = if (levelNorm <= 0.01f) -20.0f else (-20f + levelNorm * 23f)
        val dbFormatted = "${if (currentDb > 0) "+" else ""}${String.format(Locale.US, "%.1f", currentDb)} dB"

        scalePaint.textSize = 7.5.sp.toPx()
        scalePaint.color = accentTextColor.copy(alpha = 0.75f).toArgb()
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(channelLabel, cx, cy - 14.dp.toPx(), scalePaint)
        }

        scalePaint.textSize = 8.5.sp.toPx()
        scalePaint.color = (if (levelNorm >= zeroVuNorm) overloadRed else Color.White.copy(alpha = 0.85f)).toArgb()
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(dbFormatted, cx, cy - 2.dp.toPx(), scalePaint)
        }
    }
}
