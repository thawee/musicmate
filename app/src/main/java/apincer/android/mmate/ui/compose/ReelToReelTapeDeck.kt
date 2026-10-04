package apincer.android.mmate.ui.compose

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Path
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
 * Audiophile Vintage Reel-to-Reel Tape Deck Widget
 *
 * Emulates the mechanical motion and precision engineering of iconic master tape recorders
 * (Studer A820, Revox B77, Otari MTR-90).
 *
 * Features:
 * - Dual rotating 3-hole NAB precision aluminum tape reels.
 * - Dynamic supply & take-up tape pack radii tracking track progress (progressMs / durationMs).
 * - Real mechanical tape physics: Left supply reel accelerates as it empties;
 *   right take-up reel decelerates as it fills (v = omega * r).
 * - Authentic tape path with guide rollers, tension arms, and center tape heads.
 * - Digital/mechanical tape counter and illuminated RUN/PAUSE status lamp.
 * - Cycles Audiophile themes (Accuphase Gold, McIntosh Blue, Studio Slate) on tap.
 * - Power-efficient idling: breaks frame loop when paused and reels come to a complete stop.
 */
@Composable
fun ReelToReelTapeDeck(
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
    initialTheme: VUMeterTheme = VUMeterTheme.ACCUPHASE
) {
    val themes = remember { VUMeterTheme.values() }
    var themeIndex by remember { mutableIntStateOf(themes.indexOf(initialTheme).coerceAtLeast(0)) }
    val currentTheme = themes[themeIndex % themes.size]

    // Transient theme switch pill toast
    var showThemeBadge by remember { mutableStateOf(false) }
    LaunchedEffect(currentTheme) {
        showThemeBadge = true
        kotlinx.coroutines.delay(1800)
        showThemeBadge = false
    }

    // Animation & reel angles
    var angleLeft by remember { mutableFloatStateOf(0f) }
    var angleRight by remember { mutableFloatStateOf(0f) }

    // Reel physics state (progress normalized 0f..1f)
    val progress = if (durationMs > 0) (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val currentProgress by rememberUpdatedState(progress)

    LaunchedEffect(isPlaying) {
        var lastTime = System.nanoTime()
        var speedL = 0f
        var speedR = 0f

        while (isActive) {
            withFrameNanos { now ->
                val dt = ((now - lastTime) / 1_000_000_000f).coerceIn(0.004f, 0.04f)
                lastTime = now

                // As left reel empties (radius shrinks from 1.0 to 0.4), it rotates faster
                // As right reel fills (radius grows from 0.4 to 1.0), it rotates slower
                val p = currentProgress
                val leftRadiusFactor = (1.0f - p * 0.6f).coerceIn(0.4f, 1.0f)
                val rightRadiusFactor = (0.4f + p * 0.6f).coerceIn(0.4f, 1.0f)

                val targetSpeedL = if (isPlaying) (120f / leftRadiusFactor) else 0f
                val targetSpeedR = if (isPlaying) (120f / rightRadiusFactor) else 0f

                // Smooth mechanical inertia damping
                speedL += (targetSpeedL - speedL) * 8f * dt
                speedR += (targetSpeedR - speedR) * 8f * dt

                angleLeft = (angleLeft + speedL * dt) % 360f
                angleRight = (angleRight + speedR * dt) % 360f
            }

            // Power-efficiency idling: stop frame loop once reels settle to rest during pause
            if (!isPlaying && abs(speedL) < 0.1f && abs(speedR) < 0.1f) {
                speedL = 0f
                speedR = 0f
                break
            }
        }
    }

    // Palette mapping matching AnalogVUMeter
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

    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    val counterPaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create("monospace", Typeface.BOLD)
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
                themeIndex = (themeIndex + 1) % themes.size
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Chassis Header: Status Lamp & Tape Counter
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
                        text = "REEL-TO-REEL TAPE DECK",
                        color = palette.accentTextColor.copy(alpha = 0.85f),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }

                // Mechanical Counter / Time
                val elapsedSec = progressMs / 1000
                val totalSec = durationMs / 1000
                val timeStr = String.format(Locale.US, "%02d:%02d / %02d:%02d", elapsedSec / 60, elapsedSec % 60, totalSec / 60, totalSec % 60)
                Text(
                    text = "TAPE $timeStr",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 7.5.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }

            // Dual Spinning Reels Deck
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas

                val reelRadius = min(h * 0.50f, w * 0.23f)
                val cy = h * 0.48f

                // Left reel center and right reel center
                val cxLeft = w * 0.25f
                val cxRight = w * 0.75f

                // Dynamic tape radii based on progress
                val minTapeRadius = reelRadius * 0.42f
                val maxTapeRadius = reelRadius * 0.92f

                val leftTapeRadius = minTapeRadius + (maxTapeRadius - minTapeRadius) * (1f - progress)
                val rightTapeRadius = minTapeRadius + (maxTapeRadius - minTapeRadius) * progress

                // 1. Draw Tape Path behind center heads
                drawTapeRibbon(
                    cxLeft = cxLeft,
                    leftTapeRadius = leftTapeRadius,
                    cxRight = cxRight,
                    rightTapeRadius = rightTapeRadius,
                    cy = cy,
                    height = h
                )

                // 2. Center Head Assembly & Guide Rollers
                drawCenterHeadBridge(
                    cx = w * 0.5f,
                    cy = cy + reelRadius * 0.52f,
                    palette = palette,
                    isPlaying = isPlaying,
                    textPaint = textPaint,
                    counterPaint = counterPaint,
                    progress = progress
                )

                // 3. Draw Left Reel (Supply Reel)
                drawReel(
                    cx = cxLeft,
                    cy = cy,
                    reelRadius = reelRadius,
                    tapeRadius = leftTapeRadius,
                    rotationDeg = angleLeft,
                    palette = palette,
                    label = "SUPPLY"
                )

                // 4. Draw Right Reel (Take-Up Reel)
                drawReel(
                    cx = cxRight,
                    cy = cy,
                    reelRadius = reelRadius,
                    tapeRadius = rightTapeRadius,
                    rotationDeg = angleRight,
                    palette = palette,
                    label = "TAKE-UP"
                )
            }
        }

        // Theme switch pill indicator
        AnimatedVisibility(
            visible = showThemeBadge,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.88f))
                    .border(1.dp, palette.arcNormalColor.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = currentTheme.displayName,
                    color = palette.accentTextColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

/**
 * Draws a single 3-hole NAB aluminum tape reel with tape pack and central spindle.
 */
private fun DrawScope.drawReel(
    cx: Float,
    cy: Float,
    reelRadius: Float,
    tapeRadius: Float,
    rotationDeg: Float,
    palette: VUMeterPalette,
    label: String
) {
    // 1. Reel Outer Chassis Shadow
    drawCircle(
        color = Color.Black.copy(alpha = 0.5f),
        radius = reelRadius + 2f,
        center = Offset(cx + 1f, cy + 2f)
    )

    // 2. Wound Magnetic Tape Pack (Dark warm brown / oxide black)
    if (tapeRadius > reelRadius * 0.43f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF261D18),
                    Color(0xFF1B1410),
                    Color(0xFF120E0B)
                ),
                center = Offset(cx, cy),
                radius = tapeRadius
            ),
            radius = tapeRadius,
            center = Offset(cx, cy)
        )
        // Subtle magnetic tape wrap ridges
        drawCircle(
            color = Color.White.copy(alpha = 0.06f),
            radius = tapeRadius - 1.5f,
            center = Offset(cx, cy),
            style = Stroke(width = 1f)
        )
    }

    // 3. Clear Aluminum Reel Flange Rim (Outer Ring)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                palette.arcNormalColor.copy(alpha = 0.25f),
                Color(0x33EEEEEE),
                Color(0x11222222)
            ),
            center = Offset(cx, cy),
            radius = reelRadius
        ),
        radius = reelRadius,
        center = Offset(cx, cy),
        style = Stroke(width = 2.5f)
    )

    // 4. Rotating 3-Hole Precision Metal Cutouts (120 deg apart)
    val hubRadius = reelRadius * 0.38f
    val holeDistance = reelRadius * 0.65f
    val holeRadius = reelRadius * 0.16f

    for (i in 0..2) {
        val holeAngle = Math.toRadians((rotationDeg + i * 120.0)).toFloat()
        val hx = cx + holeDistance * sin(holeAngle)
        val hy = cy - holeDistance * cos(holeAngle)

        // Cutout hole
        drawCircle(
            color = Color(0xFF0C0A09),
            radius = holeRadius,
            center = Offset(hx, hy)
        )
        drawCircle(
            color = palette.arcNormalColor.copy(alpha = 0.35f),
            radius = holeRadius,
            center = Offset(hx, hy),
            style = Stroke(width = 1f)
        )

        // Aluminum spoke lines connecting center to rim
        val spokeAngle = holeAngle + Math.PI.toFloat() / 3f
        val sx1 = cx + hubRadius * sin(spokeAngle)
        val sy1 = cy - hubRadius * cos(spokeAngle)
        val sx2 = cx + (reelRadius - 2f) * sin(spokeAngle)
        val sy2 = cy - (reelRadius - 2f) * cos(spokeAngle)
        drawLine(
            color = palette.arcNormalColor.copy(alpha = 0.22f),
            start = Offset(sx1, sy1),
            end = Offset(sx2, sy2),
            strokeWidth = 1.2f,
            cap = StrokeCap.Round
        )
    }

    // 5. Central Metal Hub & NAB Spindle Locking Clamps
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFDDDDDD),
                Color(0xFF888888),
                Color(0xFF333333)
            ),
            center = Offset(cx, cy),
            radius = hubRadius
        ),
        radius = hubRadius,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = palette.bezelBorder.copy(alpha = 0.6f),
        radius = hubRadius,
        center = Offset(cx, cy),
        style = Stroke(width = 1.2f)
    )

    // Spindle 3-Tooth Drive Notch
    val spindleRadius = hubRadius * 0.45f
    drawCircle(
        color = Color(0xFF141414),
        radius = spindleRadius,
        center = Offset(cx, cy)
    )
    for (i in 0..2) {
        val sAngle = Math.toRadians((rotationDeg + i * 120.0)).toFloat()
        val tx = cx + spindleRadius * 0.9f * sin(sAngle)
        val ty = cy - spindleRadius * 0.9f * cos(sAngle)
        drawCircle(
            color = palette.needleColor,
            radius = 1.8f,
            center = Offset(tx, ty)
        )
    }
}

/**
 * Draws the magnetic tape path spanning across guide rollers and heads.
 */
private fun DrawScope.drawTapeRibbon(
    cxLeft: Float,
    leftTapeRadius: Float,
    cxRight: Float,
    rightTapeRadius: Float,
    cy: Float,
    height: Float
) {
    val bottomGuideY = cy + max(leftTapeRadius, rightTapeRadius) * 0.72f
    val tapeColor = Color(0xFF181310)

    val path = Path().apply {
        // Start from supply reel underside
        moveTo(cxLeft - leftTapeRadius * 0.3f, cy + leftTapeRadius * 0.88f)
        // Sweep gently down through guide rollers
        cubicTo(
            cxLeft + 20f, bottomGuideY,
            cxRight - 20f, bottomGuideY,
            cxRight + rightTapeRadius * 0.3f, cy + rightTapeRadius * 0.88f
        )
    }

    drawPath(
        path = path,
        color = tapeColor,
        style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
    )

    // Tape top highlight sheen
    drawPath(
        path = path,
        color = Color.White.copy(alpha = 0.08f),
        style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
    )
}

/**
 * Draws the center tape head bridge (Erase, Record, Playback heads + capstan & roller).
 */
private fun DrawScope.drawCenterHeadBridge(
    cx: Float,
    cy: Float,
    palette: VUMeterPalette,
    isPlaying: Boolean,
    textPaint: Paint,
    counterPaint: Paint,
    progress: Float
) {
    val bridgeWidth = 74.dp.toPx()
    val bridgeHeight = 22.dp.toPx()

    // 1. Brushed Metal Head Assembly Base
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF2B2824),
                Color(0xFF1C1A18),
                Color(0xFF100F0E)
            ),
            startY = cy - bridgeHeight * 0.5f,
            endY = cy + bridgeHeight * 0.5f
        ),
        topLeft = Offset(cx - bridgeWidth * 0.5f, cy - bridgeHeight * 0.5f),
        size = Size(bridgeWidth, bridgeHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
    )

    drawRoundRect(
        color = palette.bezelBorder.copy(alpha = 0.4f),
        topLeft = Offset(cx - bridgeWidth * 0.5f, cy - bridgeHeight * 0.5f),
        size = Size(bridgeWidth, bridgeHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
        style = Stroke(width = 1f)
    )

    // 2. Miniature Chrome Heads (Erase, Sync, Playback)
    val headW = 10.dp.toPx()
    val headH = 12.dp.toPx()
    val headY = cy - headH * 0.5f

    for (offsetIdx in listOf(-1.3f, 0f, 1.3f)) {
        val hx = cx + offsetIdx * 18.dp.toPx() - headW * 0.5f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFD6D6D6), Color(0xFF707070), Color(0xFF383838)),
                startY = headY,
                endY = headY + headH
            ),
            topLeft = Offset(hx, headY),
            size = Size(headW, headH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
        // Head gap micro-line
        drawLine(
            color = Color.Black,
            start = Offset(hx + headW * 0.5f, headY + 1f),
            end = Offset(hx + headW * 0.5f, headY + headH - 1f),
            strokeWidth = 1f
        )
    }

    // 3. Center Glowing Head Lamp (REC / PLAY status)
    val lampColor = if (isPlaying) palette.needleColor else Color(0xFF555555)
    drawCircle(
        color = lampColor,
        radius = 2.dp.toPx(),
        center = Offset(cx, cy + bridgeHeight * 0.35f)
    )
    if (isPlaying) {
        drawCircle(
            color = lampColor.copy(alpha = 0.35f),
            radius = 4.dp.toPx(),
            center = Offset(cx, cy + bridgeHeight * 0.35f)
        )
    }
}
