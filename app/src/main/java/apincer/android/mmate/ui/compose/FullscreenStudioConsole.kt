package apincer.android.mmate.ui.compose

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.palette.graphics.Palette
import apincer.android.mmate.R
import apincer.android.mmate.coil3.CoverartFetcher
import apincer.music.core.model.Track
import apincer.music.core.playback.PlaybackState
import apincer.music.core.utils.StringUtils
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10

/**
 * Visualizer modes available in the Landscape Studio Console.
 */
enum class StudioVisualizerMode(val displayName: String) {
    VU_METER("VU METER"),
    TAPE_DECK("TAPE DECK"),
    COVER_ART("ALBUM ART")
}

/**
 * Clamps raw extracted RGB colors to darkroom obsidian luminance levels (max 18% brightness),
 * guaranteeing that dynamic background ambient glow never washes out analog dials or meters.
 */
internal fun clampToDarkroomObsidian(color: Color): Color {
    val r = color.red
    val g = color.green
    val b = color.blue
    val maxC = maxOf(r, g, b)
    if (maxC <= 0.01f) return Color(0xFF14110E)
    val scale = if (maxC > 0.18f) 0.18f / maxC else 1.0f
    return Color(
        red = (r * scale).coerceIn(0.04f, 0.22f),
        green = (g * scale).coerceIn(0.03f, 0.20f),
        blue = (b * scale).coerceIn(0.03f, 0.22f),
        alpha = 1.0f
    )
}

/**
 * Strips raw local IP addresses (e.g. " • 192.168.1.52") from target player labels,
 * converting developer telemetry into elegant user-facing hi-fi device titles.
 */
internal fun sanitizeTargetDeviceTitle(title: String): String {
    if (title.isBlank()) return "Local Audio"
    val noIp = title.replace(Regex("•?\\s*\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"), "").trim()
    return noIp.trimEnd('•', ' ', '-').ifEmpty { "Local Audio" }
}

/**
 * Fullscreen Landscape Studio Console ("Hi-Fi Desk Mode") - 10/10 Luxury Edition
 *
 * Immersive split-bay horizontal audio playback console designed for desktop listening stands,
 * hi-fi audio racks, tablets, and car consoles.
 *
 * Architecture:
 * - Dynamic Studio Ambilight: Darkroom luminance-clamped ambient glow extracted from cover art.
 * - True Immersive Mode: Hides Android status bar & navigation pill via WindowInsetsControllerCompat.
 * - Symmetrical Dual Chassis: Balanced Left and Right brushed obsidian bezels with hairline gold strokes.
 * - De-cluttered Visualizer Deck: Dedicated dock for visualizer mode switcher preventing tape-head overlap.
 * - Bespoke Audiophile Sliders: Custom illuminated micro-track scrubber and calibrated dB volume fader.
 * - Machined Master Transport: Concentric brushed brass button with tactile depression animation.
 */
@Composable
fun FullscreenStudioConsole(
    state: NowPlayingState,
    queueState: QueueState,
    onDismissRequest: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onSelectTargetPlayer: () -> Unit,
    onQueueTrackClicked: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // 1. Manage Landscape Orientation & True Immersive Fullscreen Mode
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            activity?.requestedOrientation = originalOrientation
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 2. Keep screen awake toggle state (persisted in Settings)
    var keepScreenOn by remember {
        mutableStateOf(apincer.music.core.Settings.isStudioKeepScreenOn(context))
    }

    DisposableEffect(keepScreenOn) {
        if (keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { }
    }

    // Intercept Android System Back to exit fullscreen
    BackHandler {
        onDismissRequest()
    }

    val track = state.track.value
    val duration = state.durationMs.value
    val progress = state.progressMs.value
    val isPlaying = state.playbackState.value.currentState == PlaybackState.State.PLAYING

    val rawTargetTitle = state.targetTitle.value.ifEmpty { "Local Audio" }
    val cleanTargetTitle = remember(rawTargetTitle) { sanitizeTargetDeviceTitle(rawTargetTitle) }
    val isDLNA = rawTargetTitle.contains("DLNA", ignoreCase = true) || state.targetBadge.value.contains("DLNA", ignoreCase = true)
    val isBitPerfect = rawTargetTitle.contains("Bit-Perfect", ignoreCase = true) || state.targetBadge.value.contains("Bit-Perfect", ignoreCase = true)

    // Default visualizer mode: Tape Deck on DLNA; VU Meter on Local Audio
    var visualizerMode by remember {
        mutableStateOf(if (isDLNA) StudioVisualizerMode.TAPE_DECK else StudioVisualizerMode.VU_METER)
    }

    val colorGold = Color(0xFFFFB300)
    val colorGoldAccent = Color(0xFFFFD54F)

    // 3. Dynamic Studio Ambilight Color Extraction (Clamped to Darkroom Obsidian)
    val albumBitmap = state.albumArt.value
    val (ambientPrimary, ambientSecondary) = remember(albumBitmap, track?.id) {
        if (albumBitmap != null && !albumBitmap.isRecycled) {
            try {
                val palette = Palette.from(albumBitmap).generate()
                val darkVibrant = palette.getDarkVibrantColor(0)
                val darkMuted = palette.getDarkMutedColor(0)
                val vibrant = palette.getVibrantColor(0)
                val dominant = palette.getDominantColor(0)

                val rawPrimary = when {
                    darkVibrant != 0 -> darkVibrant
                    darkMuted != 0 -> darkMuted
                    vibrant != 0 -> vibrant
                    dominant != 0 -> dominant
                    else -> 0xFF2B1D0E.toInt()
                }
                val rawSecondary = when {
                    darkMuted != 0 && darkMuted != rawPrimary -> darkMuted
                    darkVibrant != 0 && darkVibrant != rawPrimary -> darkVibrant
                    dominant != 0 && dominant != rawPrimary -> dominant
                    else -> rawPrimary
                }
                Pair(clampToDarkroomObsidian(Color(rawPrimary)), clampToDarkroomObsidian(Color(rawSecondary)))
            } catch (e: Exception) {
                Pair(Color(0xFF191410), Color(0xFF100D0A))
            }
        } else {
            Pair(Color(0xFF191410), Color(0xFF100D0A))
        }
    }

    val animatedAmbientColor by animateColorAsState(
        targetValue = ambientPrimary,
        animationSpec = tween(900),
        label = "studioAmbientGlow"
    )
    val animatedSecondaryColor by animateColorAsState(
        targetValue = ambientSecondary,
        animationSpec = tween(900),
        label = "studioSecondaryGlow"
    )

    // 4. Integrated Minimalist Studio Digital Clock
    var studioClockTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (isActive) {
            studioClockTime = timeFormatter.format(Date())
            delay(1000)
        }
    }

    // Scrubber drag & queue overlay state
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    var showQueueOverlay by remember { mutableStateOf(false) }

    Surface(
        color = Color(0xFF070605),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            animatedAmbientColor,
                            animatedSecondaryColor.copy(alpha = 0.55f),
                            Color(0xFF0F0D0B),
                            Color(0xFF060504)
                        ),
                        radius = 2200f
                    )
                )
                .displayCutoutPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ═══════════════════════════════════════════════════════════════════
                // LEFT BAY: Grand Visualizer Deck (50% Width) - Symmetrical Chassis
                // ═══════════════════════════════════════════════════════════════════
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF14110F).copy(alpha = 0.94f),
                                    Color(0xFF0C0A09).copy(alpha = 0.97f),
                                    Color(0xFF070605)
                                )
                            )
                        )
                        .border(BorderStroke(0.75.dp, Color(0x35FFA000)), RoundedCornerShape(14.dp))
                        .shadow(6.dp, RoundedCornerShape(14.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Visualizer Canvas takes maximum available space
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = visualizerMode,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                            },
                            label = "ConsoleVisualizerTransition",
                            modifier = Modifier.fillMaxSize()
                        ) { mode ->
                            when (mode) {
                                StudioVisualizerMode.VU_METER -> {
                                    val trackDr = (track?.dynamicRange ?: 10.0).toInt()
                                    val trackPeak = track?.path?.let { p ->
                                        apincer.music.core.playback.ReplayGainManager.getInstance().getReplayGain(p)?.trackPeak?.toFloat() ?: 1.0f
                                    } ?: 1.0f

                                    AnalogVUMeter(
                                        isPlaying = isPlaying,
                                        volume = state.volume.value,
                                        drScore = trackDr,
                                        trackPeak = trackPeak,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                }
                                StudioVisualizerMode.TAPE_DECK -> {
                                    ReelToReelTapeDeck(
                                        isPlaying = isPlaying,
                                        progressMs = progress,
                                        durationMs = duration,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                }
                                StudioVisualizerMode.COVER_ART -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF100E0C)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (track != null) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight(0.88f)
                                                    .aspectRatio(1f)
                                                    .shadow(12.dp, RoundedCornerShape(8.dp), ambientColor = Color(0x80000000), spotColor = Color(0xAA000000))
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(BorderStroke(0.75.dp, Color(0x33FFA000)), RoundedCornerShape(8.dp))
                                            ) {
                                                AsyncImage(
                                                    model = CoverartFetcher.builder(context, track).data(track).build(),
                                                    contentDescription = "Studio Cover Art",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        } else {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_now_playing_idle),
                                                contentDescription = null,
                                                tint = Color(0x33FFFFFF),
                                                modifier = Modifier.size(64.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Docked Visualizer Mode Switcher Pill (Below the canvas - ZERO overlap with tape heads!)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0D0B0A))
                            .border(BorderStroke(0.75.dp, Color(0x28FFFFFF)), RoundedCornerShape(12.dp))
                            .padding(horizontal = 3.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StudioVisualizerMode.values().forEach { m ->
                            val isSelected = (visualizerMode == m)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (isSelected) colorGold.copy(alpha = 0.22f) else Color.Transparent)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        visualizerMode = m
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = m.displayName,
                                    color = if (isSelected) colorGoldAccent else Color.White.copy(alpha = 0.45f),
                                    fontSize = 8.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }

                // ═══════════════════════════════════════════════════════════════════
                // RIGHT BAY: Master Telemetry & Studio Transport Controls (50% Width)
                // ═══════════════════════════════════════════════════════════════════
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF14110F).copy(alpha = 0.94f),
                                    Color(0xFF0C0A09).copy(alpha = 0.97f),
                                    Color(0xFF070605)
                                )
                            )
                        )
                        .border(BorderStroke(0.75.dp, Color(0x35FFA000)), RoundedCornerShape(14.dp))
                        .shadow(6.dp, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Row 1: Header (Integrated Studio Clock & Status, Sanitized Output Target, Quick Toggles)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Studio Status & Digital Clock
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isPlaying) Color(0xFF00E676) else Color.Gray.copy(alpha = 0.5f))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "STUDIO DESK",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            if (studioClockTime.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = studioClockTime,
                                    color = colorGoldAccent,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Right: Output Target Selector & Quick Actions
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val targetDotColor = when {
                                isBitPerfect -> Color(0xFF00E676)
                                isDLNA -> Color(0xFF00E5FF)
                                else -> colorGold
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x33222222))
                                    .border(BorderStroke(0.75.dp, targetDotColor.copy(alpha = 0.45f)), RoundedCornerShape(10.dp))
                                    .clickable(onClick = onSelectTargetPlayer)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(targetDotColor)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = cleanTargetTitle,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isDLNA) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "DLNA",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                } else if (isBitPerfect) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "BIT-PERFECT",
                                        color = Color(0xFF00E676),
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Keep Screen Awake Quick Toggle Button
                            IconButton(
                                onClick = {
                                    val newState = !keepScreenOn
                                    keepScreenOn = newState
                                    apincer.music.core.Settings.setStudioKeepScreenOn(context, newState)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (keepScreenOn) R.drawable.rounded_wb_sunny_24 else R.drawable.rounded_bedtime_24
                                    ),
                                    contentDescription = if (keepScreenOn) "Screen Awake" else "Screen Sleep",
                                    tint = if (keepScreenOn) colorGold else Color.White.copy(alpha = 0.45f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Close / Exit Fullscreen Button
                            IconButton(
                                onClick = onDismissRequest,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.rounded_fullscreen_exit_24),
                                    contentDescription = "Exit Fullscreen",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Row 2: Track Title & Artist Marquee with Companion Album Art Sleeve
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Interactive Mini-Sleeve (shown when Left Bay is in VU_METER or TAPE_DECK)
                        AnimatedVisibility(
                            visible = visualizerMode != StudioVisualizerMode.COVER_ART,
                            enter = fadeIn(tween(250)) + expandHorizontally(tween(250)),
                            exit = fadeOut(tween(250)) + shrinkHorizontally(tween(250))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .shadow(
                                            elevation = 6.dp,
                                            shape = RoundedCornerShape(8.dp),
                                            ambientColor = Color(0x66000000),
                                            spotColor = Color(0x99000000)
                                        )
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF141210))
                                        .border(BorderStroke(0.75.dp, Color(0x44FFA000)), RoundedCornerShape(8.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            // 1-Tap quick-expand: switches visualizer mode to ALBUM ART!
                                            visualizerMode = StudioVisualizerMode.COVER_ART
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (track != null) {
                                        AsyncImage(
                                            model = CoverartFetcher.builder(context, track).data(track).build(),
                                            contentDescription = "Cover Art Miniature (Tap to Expand)",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_now_playing_idle),
                                            contentDescription = null,
                                            tint = Color(0x44FFFFFF),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                        }

                        // Track Title & Artist Metadata Column
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track?.title ?: "MusicMate",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .basicMarquee(iterations = Int.MAX_VALUE, velocity = 26.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val artistAlbum = listOfNotNull(track?.artist, track?.album).filter { it.isNotBlank() }.joinToString(" • ")
                            Text(
                                text = artistAlbum.ifEmpty { "High-Fidelity Studio Audio" },
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Row 3: Technical Specs Diagnostics Strip with Leading Quality Verdict Badge
                    val verdictText = state.specsVerdict.value.ifEmpty { "HI-RES AUDIO" }
                    val codecStr = track?.audioEncoding?.uppercase()?.ifEmpty { null } ?: state.specsFormat.value.split("•").firstOrNull()?.trim() ?: "PCM"
                    val sampleRateStr = if (track?.audioSampleRate != null && track.audioSampleRate > 0) "${track.audioSampleRate / 1000.0} kHz" else ""
                    val bitDepthStr = if (track?.audioBitsDepth != null && track.audioBitsDepth > 0) "${track.audioBitsDepth}-bit" else ""
                    val resStr = listOf(bitDepthStr, sampleRateStr).filter { it.isNotEmpty() }.joinToString(" ")
                    val drStr = state.specsDr.value.ifEmpty { if (track?.dynamicRange != null && track.dynamicRange > 0) "DR ${(track.dynamicRange).toInt()}" else "" }
                    val rgStr = state.specsReplayGain.value

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StudioSpecChip(
                            text = verdictText,
                            isHighlighted = true,
                            highlightColor = colorGold
                        )
                        StudioSpecChip(codecStr)
                        if (resStr.isNotEmpty()) StudioSpecChip(resStr)
                        if (drStr.isNotEmpty()) StudioSpecChip(drStr)
                        if (rgStr.isNotEmpty()) StudioSpecChip(rgStr)
                    }

                    // Row 4: Bespoke High-Precision Audiophile Scrubber
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val progressFraction = if (isDragging) dragPosition else (if (duration > 0) progress.toFloat() / duration.toFloat() else 0f)
                        StudioHiFiScrubber(
                            progressFraction = progressFraction,
                            isDragging = isDragging,
                            dragPosition = dragPosition,
                            onDragStart = { isDragging = true },
                            onDragChange = { pos -> dragPosition = pos },
                            onDragEnd = {
                                isDragging = false
                                onSeek(dragPosition)
                            },
                            colorGold = colorGold,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val curSec = if (isDragging) (dragPosition * duration) / 1000.0 else progress / 1000.0
                            val durSec = duration / 1000.0
                            Text(
                                text = StringUtils.formatDuration(curSec, false),
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = StringUtils.formatDuration(durSec, false),
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Row 5: Studio Transport Controls (Machined Concentric Push-Button)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Shuffle
                        IconButton(onClick = onShuffleToggle, modifier = Modifier.size(36.dp)) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_shuffle_24),
                                contentDescription = "Shuffle",
                                tint = if (state.isShuffle.value) colorGold else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Previous
                        IconButton(onClick = onPrevious, modifier = Modifier.size(40.dp)) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_skip_previous_24),
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(25.dp)
                            )
                        }

                        // Master Play / Pause (Machined Concentric Brushed Brass Button)
                        val playInteractionSource = remember { MutableInteractionSource() }
                        val isPlayPressed by playInteractionSource.collectIsPressedAsState()
                        val buttonScale by animateFloatAsState(
                            targetValue = if (isPlayPressed) 0.93f else 1.0f,
                            animationSpec = tween(100),
                            label = "playButtonScale"
                        )

                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .graphicsLayer {
                                    scaleX = buttonScale
                                    scaleY = buttonScale
                                }
                                .shadow(
                                    elevation = if (isPlaying) 10.dp else 4.dp,
                                    shape = CircleShape,
                                    ambientColor = Color(0x40FFA000),
                                    spotColor = Color(0xFFFFB300)
                                )
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Color(0xFFFFD54F),
                                            Color(0xFFFFB300),
                                            Color(0xFFE65100),
                                            Color(0xFFBF360C)
                                        )
                                    )
                                )
                                .border(BorderStroke(1.5.dp, Color(0xFFFFF176)), CircleShape)
                                .clickable(
                                    interactionSource = playInteractionSource,
                                    indication = null,
                                    onClick = onPlayPause
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isPlaying) R.drawable.ic_baseline_pause_24 else R.drawable.ic_baseline_play_arrow_24
                                ),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color(0xFF181109),
                                modifier = Modifier.size(27.dp)
                            )
                        }

                        // Next
                        IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_skip_next_24),
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(25.dp)
                            )
                        }

                        // Repeat
                        IconButton(onClick = onRepeatToggle, modifier = Modifier.size(36.dp)) {
                            val repeatMode = state.repeatMode.value
                            Icon(
                                painter = painterResource(
                                    id = if (repeatMode == 2) R.drawable.ic_baseline_repeat_one_24 else R.drawable.ic_baseline_repeat_24
                                ),
                                contentDescription = "Repeat",
                                tint = if (repeatMode > 0) colorGold else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    // Row 6: Linear Studio Volume Fader & Up Next Preview
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Bespoke Linear Studio Volume Attenuator
                        StudioLinearVolumeFader(
                            volume = state.volume.value,
                            onVolumeChanged = onVolumeChanged,
                            colorGold = colorGold,
                            modifier = Modifier.width(180.dp)
                        )

                        // Up Next Track Preview Capsule (Clickable -> Expands Studio Queue Overlay!)
                        val nextTrack = queueState.tracks.firstOrNull()
                        if (nextTrack != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (showQueueOverlay) colorGold.copy(alpha = 0.25f) else Color(0x22FFFFFF))
                                    .border(BorderStroke(0.75.dp, if (showQueueOverlay) colorGold else Color(0x33FFFFFF)), RoundedCornerShape(8.dp))
                                    .clickable { showQueueOverlay = !showQueueOverlay }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "UP NEXT: ",
                                    color = colorGold.copy(alpha = 0.85f),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = nextTrack.title,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 140.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    painter = painterResource(
                                        id = if (showQueueOverlay) R.drawable.ic_round_keyboard_arrow_down_24 else R.drawable.ic_round_queue_music_24
                                    ),
                                    contentDescription = "Toggle Queue",
                                    tint = colorGold,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                // Interactive Studio Queue Overlay (Toggled by UP NEXT chip)
                androidx.compose.animation.AnimatedVisibility(
                    visible = showQueueOverlay,
                    enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
                    modifier = Modifier.matchParentSize()
                ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xF70D0B0A))
                                .border(BorderStroke(0.75.dp, colorGold.copy(alpha = 0.6f)), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            // Queue Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_round_queue_music_24),
                                        contentDescription = null,
                                        tint = colorGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "PLAY QUEUE (${queueState.tracks.size})",
                                        color = colorGoldAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                IconButton(
                                    onClick = { showQueueOverlay = false },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.round_close_24),
                                        contentDescription = "Close Queue",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (queueState.tracks.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Queue is empty",
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    itemsIndexed(queueState.tracks) { index, queueTrack ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0x18FFFFFF))
                                                .clickable {
                                                    onQueueTrackClicked(queueTrack)
                                                    showQueueOverlay = false
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                color = colorGold.copy(alpha = 0.7f),
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.width(20.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = queueTrack.title,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (!queueTrack.artist.isNullOrBlank()) {
                                                    Text(
                                                        text = queueTrack.artist,
                                                        color = Color.White.copy(alpha = 0.55f),
                                                        fontSize = 9.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = StringUtils.formatDuration(queueTrack.audioDuration, false),
                                                color = Color.White.copy(alpha = 0.4f),
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bespoke Precision Audiophile Scrubber Track
 */
@Composable
private fun StudioHiFiScrubber(
    progressFraction: Float,
    isDragging: Boolean,
    dragPosition: Float,
    onDragStart: () -> Unit,
    onDragChange: (Float) -> Unit,
    onDragEnd: () -> Unit,
    colorGold: Color,
    modifier: Modifier = Modifier
) {
    val activeFraction = if (isDragging) dragPosition else progressFraction
    val animatedFraction by animateFloatAsState(
        targetValue = activeFraction.coerceIn(0f, 1f),
        animationSpec = if (isDragging) tween(0) else tween(100),
        label = "scrubberFraction"
    )

    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.25f else 1.0f,
        animationSpec = tween(150),
        label = "thumbScale"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onDragChange(fraction)
                    onDragEnd()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        onDragStart()
                        val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onDragChange(fraction)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onDragChange(fraction)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val trackHeight = 3.dp
        val density = LocalDensity.current

        // 1. Inactive Background Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                .background(Color(0x28FFFFFF))
        )

        // 2. Active Illuminated Track with Amber Glow
        val activeWidth = (widthPx * animatedFraction).coerceIn(0f, widthPx)
        val activeWidthDp = with(density) { activeWidth.toDp() }

        Box(
            modifier = Modifier
                .width(activeWidthDp)
                .height(trackHeight)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            colorGold.copy(alpha = 0.75f),
                            Color(0xFFFFD54F)
                        )
                    )
                )
        )

        // 3. Machined Knurled Brass Thumb Pip
        val thumbBaseSize = 12.dp
        val thumbPx = with(density) { (thumbBaseSize * thumbScale).toPx() }
        val thumbOffsetPx = (activeWidth - thumbPx / 2f).coerceIn(0f, widthPx - thumbPx)
        val thumbOffsetDp = with(density) { thumbOffsetPx.toDp() }

        Box(
            modifier = Modifier
                .offset(x = thumbOffsetDp)
                .size(thumbBaseSize * thumbScale)
                .shadow(elevation = if (isDragging) 6.dp else 3.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFFFFF9C4),
                            Color(0xFFFFD54F),
                            Color(0xFFFFB300),
                            Color(0xFFFF8F00)
                        )
                    )
                )
                .border(BorderStroke(0.75.dp, Color(0xFFFFF59D)), CircleShape)
        )
    }
}

/**
 * Bespoke Linear Studio Volume Attenuator with Calibrated dB Readout
 */
@Composable
private fun StudioLinearVolumeFader(
    volume: Float,
    onVolumeChanged: (Float) -> Unit,
    colorGold: Color,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragVol by remember { mutableFloatStateOf(volume) }
    val currentVol = if (isDragging) dragVol else volume

    val dbText = remember(currentVol) {
        if (currentVol <= 0.001f) "-∞ dB"
        else {
            val db = (20f * log10(currentVol.toDouble())).toFloat().coerceIn(-60f, 0f)
            String.format(Locale.US, "%.0f dB", db)
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_baseline_volume_down_24),
            contentDescription = "Volume Down",
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(15.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Custom Fader Rail
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onVolumeChanged(fraction)
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragVol = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onVolumeChanged(dragVol)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragVol = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onVolumeChanged(dragVol)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val density = LocalDensity.current

            // Inactive rail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color(0x28FFFFFF))
            )

            // Active rail
            val activeWidth = (widthPx * currentVol.coerceIn(0f, 1f)).coerceIn(0f, widthPx)
            Box(
                modifier = Modifier
                    .width(with(density) { activeWidth.toDp() })
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(colorGold)
            )

            // Fader indicator pip
            val faderPipSize = 9.dp
            val pipPx = with(density) { faderPipSize.toPx() }
            val pipOffsetPx = (activeWidth - pipPx / 2f).coerceIn(0f, widthPx - pipPx)
            Box(
                modifier = Modifier
                    .offset(x = with(density) { pipOffsetPx.toDp() })
                    .size(faderPipSize)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(BorderStroke(0.5.dp, colorGold), CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Icon(
            painter = painterResource(id = R.drawable.ic_baseline_volume_up_24),
            contentDescription = "Volume Up",
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(15.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Calibrated dB Readout
        Text(
            text = dbText,
            color = colorGold.copy(alpha = 0.9f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(34.dp)
        )
    }
}

/**
 * Micro technical specification capsule chip with optional quality highlight.
 */
@Composable
private fun StudioSpecChip(
    text: String,
    isHighlighted: Boolean = false,
    highlightColor: Color = Color(0xFFFFB300)
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isHighlighted) highlightColor.copy(alpha = 0.15f) else Color(0x1CFFFFFF))
            .border(
                BorderStroke(0.5.dp, if (isHighlighted) highlightColor.copy(alpha = 0.6f) else Color(0x33FFFFFF)),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = if (isHighlighted) highlightColor else Color.White.copy(alpha = 0.85f),
            fontSize = 8.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        )
    }
}
