package apincer.android.mmate.ui.compose

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.FlowRow

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.palette.graphics.Palette
import apincer.android.mmate.R
import apincer.android.mmate.coil3.CoverartFetcher
import apincer.music.core.model.Track
import apincer.music.core.playback.PlaybackState
import apincer.music.core.utils.StringUtils
import apincer.music.core.utils.TagUtils
import coil3.compose.AsyncImage

typealias TelemetryWidgetMode = StudioVisualizerMode

@Composable
fun NowPlayingPage(
    state: NowPlayingState,
    canStartPlayback: Boolean = false,
    queuedCount: Int = 0,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onVolumeDown: () -> Unit = {},
    onVolumeUp: () -> Unit = {},
    onVolumeChanged: (Float) -> Unit = {},
    onSleepTimerSelected: (Long, Boolean) -> Unit = { _, _ -> },
    onTrackClicked: () -> Unit,
    onShowTrackDetails: (() -> Unit)? = null,
    onSelectTargetPlayer: () -> Unit = {},
    trackArtwork: (@Composable (Track) -> Unit)? = null,
    showAudioDetailsInitially: Boolean = false
) {
    var flipped by remember { mutableStateOf(showAudioDetailsInitially) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "card_flip_rotation"
    )

    // Dynamic Sound Grade Ambient Hue Fallback
    fun fallbackGradeColors(grade: String): Pair<Color, Color> {
        return when {
            grade.contains("DSD", ignoreCase = true) -> Pair(Color(0xFFE65100), Color(0xFF261204)) // Warm Amber Gold
            grade.contains("HI-RES", ignoreCase = true) || grade.contains("STUDIO", ignoreCase = true) || grade.contains("24-BIT", ignoreCase = true) -> Pair(Color(0xFF0D47A1), Color(0xFF061426)) // Deep Sapphire Cobalt
            grade.contains("MQA", ignoreCase = true) -> Pair(Color(0xFF004D40), Color(0xFF021E19)) // Emerald Cyan
            grade.contains("CD", ignoreCase = true) || grade.contains("LOSSLESS", ignoreCase = true) -> Pair(Color(0xFF1A237E), Color(0xFF0A0F2E)) // Royal Cobalt Blue
            else -> Pair(Color(0xFF2C2416), Color(0xFF141414)) // Warm Velvet Obsidian
        }
    }

    // Breathing Ambient Glow Infinite Transition
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_breathing")
    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.24f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingAlpha"
    )
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )

    val track = state.track.value
    val hasTrack = track != null
    val playControlsEnabled = UiLayoutPolicy.playControlsEnabled(hasTrack, canStartPlayback)
    val controlsEnabled = UiLayoutPolicy.transportControlsEnabled(hasTrack)
    val bitmap = state.albumArt.value
    val duration = state.durationMs.value
    val progress = state.progressMs.value
    val context = LocalContext.current
    val isPlaying = state.playbackState.value.currentState == apincer.music.core.playback.PlaybackState.State.PLAYING
    val playbackPositionDescription = if (duration > 0) {
        stringResource(
            R.string.cd_playback_position_value,
            accessiblePlaybackTime(progress),
            accessiblePlaybackTime(duration)
        )
    } else {
        stringResource(R.string.cd_playback_position)
    }

    val colorGold = MusicMateDesignTokens.Brand.Gold
    val colorGrey400 = Color(0xFFBDBDBD)

    // Dynamic Artwork Ambient Glow Palette Extraction with Grade Fallback
    val (ambientColor, secondaryAmbientColor) = remember(bitmap, state.specsVerdict.value) {
        if (bitmap != null) {
            try {
                val palette = Palette.from(bitmap).generate()
                val vibrant = palette.getVibrantColor(0)
                val darkVibrant = palette.getDarkVibrantColor(0)
                val muted = palette.getMutedColor(0)
                val darkMuted = palette.getDarkMutedColor(0)
                val dominant = palette.getDominantColor(0)

                val primary = when {
                    vibrant != 0 -> vibrant
                    darkVibrant != 0 -> darkVibrant
                    muted != 0 -> muted
                    dominant != 0 -> dominant
                    else -> 0xFF3E2723.toInt()
                }

                val secondary = when {
                    darkMuted != 0 && darkMuted != primary -> darkMuted
                    muted != 0 && muted != primary -> muted
                    darkVibrant != 0 && darkVibrant != primary -> darkVibrant
                    dominant != 0 && dominant != primary -> dominant
                    else -> primary
                }
                Pair(Color(primary), Color(secondary))
            } catch (e: Exception) {
                fallbackGradeColors(state.specsVerdict.value)
            }
        } else {
            fallbackGradeColors(state.specsVerdict.value)
        }
    }

    val animatedAmbientColor by animateColorAsState(
        targetValue = ambientColor,
        animationSpec = tween(700),
        label = "animatedAmbientColor"
    )
    val animatedSecondaryColor by animateColorAsState(
        targetValue = secondaryAmbientColor,
        animationSpec = tween(700),
        label = "animatedSecondaryColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.0f to animatedAmbientColor.copy(alpha = 0.35f),
                    0.35f to animatedSecondaryColor.copy(alpha = 0.18f),
                    0.75f to Color(0xFF080808),
                    1.0f to Color.Black
                )
            )
    ) {
        // ── 1. EDGE-TO-EDGE FLIP CONTAINER (Art + Metadata) ─────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Takes all available vertical space in the 65% sheet!
                .semantics {
                    role = Role.Button
                    onClick(if (flipped) "Show album artwork" else "Show audio details") {
                        flipped = !flipped
                        true
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onPlayPause() },
                        onTap = { flipped = !flipped }
                    )
                }
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 8 * density
                }
        ) {
            if (rotation <= 90f) {
                // FRONT: Cover Art with Overlay Badges & Metadata
                Box(modifier = Modifier.fillMaxSize()) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (track != null && trackArtwork != null) {
                        trackArtwork(track)
                    } else if (track != null) {
                        AsyncImage(
                            model = CoverartFetcher.builder(context, track).data(track).build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MusicMateDesignTokens.Surfaces.Surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_now_playing_idle),
                                contentDescription = null,
                                modifier = Modifier.size(96.dp),
                                tint = Color.DarkGray
                            )
                        }
                    }

                    // Dual-Layer soft breathing ambient radial backlight overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        animatedAmbientColor.copy(alpha = breathingAlpha),
                                        animatedSecondaryColor.copy(alpha = breathingAlpha * 0.45f),
                                        Color.Transparent
                                    ),
                                    radius = 1100f * breathingScale
                                )
                            )
                    )

                    // Bottom Gradient for maximum text legibility
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    // Darker from mid-art down: the text block covers the lower
                                    // ~45%, and bright covers left the artist line unreadable.
                                    0.0f to Color.Transparent,
                                    0.3f to Color(0x33000000),
                                    0.5f to Color(0xB3000000),
                                    0.7f to Color(0xE6000000),
                                    1.0f to Color.Black
                                )
                            )
                    )

                    // Text & Badges overlay (Bottom Aligned)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .wrapContentHeight(unbounded = true)
                            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 8.dp)
                    ) {
                        // Title
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = track?.title ?: "Ready to play",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                style = LocalTextStyle.current.copy(shadow = ArtTextShadow),
                                maxLines = 1,
                                modifier = Modifier
                                    .weight(1f)
                                    .basicMarquee(iterations = 1, velocity = 30.dp) // once per song, then rests (WCAG 2.2.2)
                                    .fadingEdge(endWidth = 14.dp) // a start fade hid the first letter at rest
                                    // Tap shows the song in the list; long-press opens its details
                                    .combinedClickable(enabled = track != null, onLongClick = onShowTrackDetails) { onTrackClicked() }
                            )
                            if (track != null && onShowTrackDetails != null) {
                                IconButton(onClick = onShowTrackDetails, modifier = Modifier.size(48.dp)) {
                                    Icon(
                                        painterResource(id = R.drawable.ic_outline_info_24),
                                        contentDescription = stringResource(R.string.cd_song_details),
                                        tint = Color(0xFFEEEEEE),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        // Artist Subtitle
                        Text(
                            text = track?.let { t ->
                                listOf(t.artist, t.album).filter { !it.isNullOrBlank() }.joinToString(" • ")
                            } ?: idleQueueText(queuedCount.takeIf { canStartPlayback } ?: 0),
                            color = Color(0xFFEEEEEE),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Normal,
                            style = LocalTextStyle.current.copy(shadow = ArtTextShadow),
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(iterations = 1, velocity = 24.dp)
                                .fadingEdge(endWidth = 12.dp)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Quality Tier Badge (Expanded / Wide Pill)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (track != null) {
                                QualityBadge(track = track, expanded = true)
                            } else if (state.specsVerdict.value.isNotEmpty()) {
                                QualityBadge(labelStr = state.specsVerdict.value, expanded = true)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Output Target Pill (Interactive Device Selector)
                        val rawTargetTitle = state.targetTitle.value.ifEmpty { "Local Audio" }
                        val cleanTargetTitle = remember(rawTargetTitle) { sanitizeTargetDeviceTitle(rawTargetTitle) }
                        val isBitPerfect = rawTargetTitle.contains("Bit-Perfect", ignoreCase = true) || state.targetBadge.value.contains("Bit-Perfect", ignoreCase = true)
                        val isDLNA = rawTargetTitle.contains("DLNA", ignoreCase = true) || state.targetBadge.value.contains("DLNA", ignoreCase = true)
                        val isBT = rawTargetTitle.contains("BT", ignoreCase = true) || rawTargetTitle.contains("Bluetooth", ignoreCase = true) || state.targetBadge.value.contains("Bluetooth", ignoreCase = true)
                        val targetDotColor = when {
                            isBitPerfect -> Color(0xFF00E676)
                            isDLNA -> Color(0xFF00E5FF)
                            isBT -> Color(0xFF64B5F6)
                            else -> Color(0xFFFFD700)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MusicMateDesignTokens.Surfaces.GlassPill)
                                .border(0.75.dp, targetDotColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .clickable { onSelectTargetPlayer() }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(targetDotColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = cleanTargetTitle,
                                color = Color.White.copy(alpha = 0.95f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.3.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (state.targetBadge.value.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(targetDotColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = state.targetBadge.value,
                                        color = targetDotColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                        if (state.carDspAvailable.value) {
                            Spacer(modifier = Modifier.width(6.dp))
                            // The chip's label is short and never cut; a long device name gives way instead
                            CarDspChip()
                        }
                        }
                    }
                }
            } else {
                // BACK: Audio Anatomy Tech Specs
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    animatedAmbientColor.copy(alpha = 0.25f),
                                    Color(0xFF141414),
                                    Color(0xFF0C0C0C)
                                )
                            )
                        )
                        .border(
                            BorderStroke(
                                0.75.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        animatedAmbientColor.copy(alpha = 0.45f),
                                        Color(0x26FFFFFF),
                                        Color.Transparent
                                    )
                                )
                            )
                        )
                        .graphicsLayer { rotationY = 180f },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_round_info_24),
                                contentDescription = "Audio Anatomy",
                                tint = colorGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AUDIO ANATOMY",
                                color = colorGold,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        // Vintage Analog VU Meter (Pillar 4 Flagship Telemetry)
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
                                .fillMaxWidth()
                                .height(135.dp)
                                .padding(horizontal = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val codecStr = track?.audioEncoding?.uppercase()?.ifEmpty { null } ?: state.specsFormat.value.split("•").firstOrNull()?.trim() ?: "UNKNOWN"
                        val encodedResolution = AudioPresentation.encodedResolution(track)
                        val resolutionStr = if (track != null && TagUtils.isMQA(track) && encodedResolution.isNotEmpty()) {
                            "Encoded: $encodedResolution"
                        } else encodedResolution.ifEmpty { state.specsFormat.value }
                        val originalRate = AudioPresentation.originalRate(track)
                        val bitrateStr = if (track?.audioBitRate != null && track.audioBitRate > 0) "${track.audioBitRate / 1000} kbps" else state.specsBitrate.value.ifEmpty { "" }
                        val drStr = state.specsDr.value.ifEmpty { if (track?.dynamicRange != null && track.dynamicRange > 0) "DR ${(track.dynamicRange).toInt()}" else "" }
                        val fileSizeStr = state.specsFileSize.value.ifEmpty { "" }

                        val durationSec = if (track != null && track.audioDuration > 0) track.audioDuration else if (duration > 0) duration / 1000.0 else 0.0
                        val durationStr = if (durationSec > 0) StringUtils.formatDuration(durationSec, false) else ""

                        val rawChannels = track?.audioChannels?.trim() ?: ""
                        val channelsStr = when {
                            rawChannels == "1" -> "Mono"
                            rawChannels == "2" -> "Stereo"
                            rawChannels.contains("5.1") -> "5.1 Surround"
                            rawChannels.contains("7.1") -> "7.1 Surround"
                            rawChannels.isNotEmpty() -> rawChannels
                            track != null -> "Stereo"
                            else -> ""
                        }

                        val trackNumStr = track?.track?.takeIf { it.isNotBlank() && it != "0" }?.let { "Track #$it" }
                        val yearStr = track?.year?.takeIf { it.isNotBlank() && it != "0" }
                        val genreStr = track?.genre?.takeIf {
                            it.isNotBlank() &&
                            !it.equals("<unknown>", ignoreCase = true) &&
                            !it.equals("Unknown", ignoreCase = true)
                        }
                        val extraSongInfo = listOfNotNull(trackNumStr, yearStr, genreStr).joinToString(" • ")

                        Text(
                            text = codecStr,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (originalRate.isNotEmpty()) {
                            Text(
                                text = "Original: $originalRate",
                                color = Color(0xFFEEEEEE),
                                fontSize = 13.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Row 1: Resolution & Bitrate
                        val hasResolution = resolutionStr.isNotEmpty() && resolutionStr != "-"
                        val hasBitrate = bitrateStr.isNotEmpty() && bitrateStr != "-"
                        if (hasResolution || hasBitrate) {
                            FlowRow(
                                itemVerticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (hasResolution) {
                                    Text(
                                        text = resolutionStr,
                                        color = Color(0xFFEEEEEE),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                if (hasBitrate) {
                                    Text(
                                        text = bitrateStr,
                                        color = Color(0xFFBDBDBD),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.4.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Row 2: Channels, Dynamic Range & ReplayGain
                        val hasChannels = channelsStr.isNotEmpty()
                        val hasDr = drStr.isNotEmpty() && drStr != "-"
                        val rgStr = state.specsReplayGain.value.ifEmpty {
                            track?.path?.let { p ->
                                val rg = apincer.music.core.playback.ReplayGainManager.getInstance().getReplayGain(p)
                                val mode = apincer.music.core.Settings.getReplayGainMode(context)
                                rg?.getDisplayString(mode)
                            } ?: ""
                        }
                        val hasRg = rgStr.isNotEmpty()
                        if (hasChannels || hasDr || hasRg) {
                            // FlowRow: at large text sizes a value moves to the next line whole
                            FlowRow(
                                itemVerticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (hasChannels) {
                                    Text(
                                        text = channelsStr,
                                        color = Color(0xFFEEEEEE),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.5.sp,
                                        softWrap = false
                                    )
                                }
                                if (hasDr) {
                                    Text(
                                        text = if (hasChannels) " • $drStr" else drStr,
                                        color = Color(0xFFFFA000),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.5.sp,
                                        softWrap = false
                                    )
                                }
                                if (hasRg) {
                                    Text(
                                        text = if (hasChannels || hasDr) " • $rgStr" else rgStr,
                                        color = Color(0xFF64B5F6),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.5.sp,
                                        softWrap = false
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Row 3: Duration & File Size
                        val hasDuration = durationStr.isNotEmpty()
                        val hasFileSize = fileSizeStr.isNotEmpty() && fileSizeStr != "-"
                        if (hasDuration || hasFileSize) {
                            FlowRow(
                                itemVerticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (hasDuration) {
                                    Text(
                                        text = durationStr,
                                        color = Color(0xFFBDBDBD),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.4.sp,
                                        softWrap = false
                                    )
                                }
                                if (hasFileSize) {
                                    Text(
                                        text = if (hasDuration) " • $fileSizeStr" else fileSizeStr,
                                        color = Color(0xFF9E9E9E),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.3.sp,
                                        softWrap = false
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Row 4: Track # • Year • Genre (if present)
                        if (extraSongInfo.isNotEmpty()) {
                            Text(
                                text = extraSongInfo,
                                color = Color(0xFFAAAAAA),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.3.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Tap to flip back", color = MusicMateDesignTokens.Text.Muted, fontSize = 12.sp)
                    }
                }
            }
        }

        // ── 2. TRANSPORT & SEEKBAR (Fixed at bottom) ────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 8.dp)
        ) {
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            var isDragging by remember { mutableStateOf(false) }
            var dragPosition by remember { mutableFloatStateOf(0f) }

            // Seekbar (Adaptive Chromatic Glowing Track & Dual Ring Thumb)
            @OptIn(ExperimentalMaterial3Api::class)
            Slider(
                value = if (isDragging) dragPosition else (if (duration > 0) progress.toFloat() / duration.toFloat() else 0f),
                enabled = controlsEnabled,
                onValueChange = { pos ->
                    if (!isDragging) isDragging = true
                    dragPosition = pos
                },
                onValueChangeFinished = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onSeek(dragPosition)
                    isDragging = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .semantics { contentDescription = playbackPositionDescription },
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(colorGold.copy(alpha = 0.4f), CircleShape)
                            .border(0.75.dp, colorGold.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(Color.White, CircleShape)
                        )
                    }
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        colors = SliderDefaults.colors(
                            // Gold, not the cover's ambient colour, so seek always reads as the main control
                            activeTrackColor = colorGold.copy(alpha = 0.95f),
                            inactiveTrackColor = Color(0x33FFFFFF)
                        ),
                        sliderState = sliderState,
                        modifier = Modifier.height(2.5.dp).clip(CircleShape)
                    )
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val displayedProgress = if (isDragging) (dragPosition * duration).toLong() else progress
                val curSec = displayedProgress / 1000.0
                val totSec = duration / 1000.0
                Text(
                    text = if (curSec > 0) StringUtils.formatDuration(curSec, false) else "00:00",
                    color = colorGrey400,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (totSec > 0) StringUtils.formatDuration(totSec, false) else "00:00",
                    color = colorGrey400,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
            }

            // While a finger is on the slider it shows the finger's value: a renderer reporting
            // its level (or the phone's coarser steps) must not pull the thumb away mid-drag
            // (only while it is pressed or dragged, so a gesture the pager takes over cannot strand it)
            val volumeInteraction = remember { MutableInteractionSource() }
            val volumeHeld = volumeInteraction.collectIsDraggedAsState().value ||
                volumeInteraction.collectIsPressedAsState().value
            var draggedVolume by remember { mutableStateOf<Float?>(null) }
            val shownVolume = ((if (volumeHeld) draggedVolume else null) ?: state.volume.value).coerceIn(0f, 1f)
            val volumePercent = (shownVolume * 100).toInt()
            // A network player's level is unknown until read, and capped by the user's maximum
            val volumeEnabled = state.volumeEnabled.value
            val volumeLimit = state.volumeLimit.value
            if (state.volumeNeedsInitialization.value) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                        .clickable(enabled = !state.volumeInitializing.value) {
                            if (state.volumeStartAvailable.value) onVolumeChanged(0.05f)
                            else onSelectTargetPlayer()
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            when {
                                state.volumeInitializing.value -> R.string.volume_hiby_setting
                                state.volumeStartAvailable.value -> R.string.volume_hiby_start
                                else -> R.string.volume_hiby_select
                            }
                        ),
                        color = if (state.volumeInitializing.value) MusicMateDesignTokens.Text.Muted else colorGold,
                        fontSize = 12.sp
                    )
                }
            } else Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onVolumeDown,
                    enabled = volumeEnabled,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = context.getString(R.string.cd_volume_down) }
                ) {
                    Icon(
                        painterResource(id = R.drawable.ic_baseline_volume_down_24),
                        contentDescription = null,
                        tint = colorGrey400,
                        modifier = Modifier.size(19.dp)
                    )
                }
                // Same thin family as the seek bar, in neutral white so the colored seek bar
                // stays the primary control (the default Material slider looked unrelated).
                BoxWithConstraints(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    @OptIn(ExperimentalMaterial3Api::class)
                    Slider(
                        value = shownVolume,
                        onValueChange = {
                            val capped = it.coerceAtMost(volumeLimit)
                            draggedVolume = capped
                            onVolumeChanged(capped)
                        },
                        onValueChangeFinished = { draggedVolume = null },
                        enabled = volumeEnabled,
                        interactionSource = volumeInteraction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = if (volumeEnabled) context.getString(R.string.cd_volume, volumePercent)
                                else context.getString(R.string.cd_volume_unavailable)
                            },
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(if (volumeEnabled) Color.White else MusicMateDesignTokens.Controls.Disabled, CircleShape)
                            )
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Color(0xB3FFFFFF),
                                    inactiveTrackColor = MusicMateDesignTokens.Controls.TrackInactive,
                                    disabledActiveTrackColor = MusicMateDesignTokens.Controls.TrackInactive,
                                    disabledInactiveTrackColor = MusicMateDesignTokens.Controls.TrackInactive
                                ),
                                enabled = volumeEnabled,
                                sliderState = sliderState,
                                drawStopIndicator = null,
                                thumbTrackGapSize = 0.dp,
                                modifier = Modifier.height(4.dp).clip(CircleShape)
                            )
                        }
                    )
                    if (volumeLimit < 1f) {
                        // The maximum: the track runs between the thumb's centers (9dp in from each end)
                        val thumbHalf = 9.dp
                        Box(
                            modifier = Modifier
                                .offset(x = thumbHalf + (maxWidth - thumbHalf * 2) * volumeLimit - 1.dp)
                                .size(width = 2.dp, height = 12.dp)
                                .background(MusicMateDesignTokens.Controls.LimitMark, RoundedCornerShape(1.dp))
                        )
                    }
                }
                Text(
                    text = if (!volumeEnabled) "--" else if (state.volumeIsWriteOnly.value) "Set $volumePercent%" else "$volumePercent%",
                    color = MusicMateDesignTokens.Text.Muted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(if (state.volumeIsWriteOnly.value) 58.dp else 36.dp).clearAndSetSemantics { }
                )
                IconButton(
                    onClick = onVolumeUp,
                    enabled = volumeEnabled,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = context.getString(R.string.cd_volume_up) }
                ) {
                    Icon(
                        painterResource(id = R.drawable.ic_baseline_volume_up_24),
                        contentDescription = null,
                        tint = colorGrey400,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Transport Controls
            val isPlaying = state.playbackState.value.currentState == PlaybackState.State.PLAYING
            val playPauseScale by animateFloatAsState(
                targetValue = if (isPlaying) 1f else 0.94f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "playPauseScale"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onShuffleToggle()
                    },
                    enabled = controlsEnabled,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = context.getString(
                                R.string.cd_shuffle_state,
                                context.getString(if (state.isShuffle.value) R.string.state_on else R.string.state_off)
                            )
                        }
                ) {
                    Icon(
                        painterResource(id = R.drawable.ic_baseline_shuffle_24),
                        contentDescription = null,
                        tint = if (state.isShuffle.value) colorGold else colorGrey400,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onPrevious()
                    },
                    enabled = controlsEnabled,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = context.getString(R.string.cd_previous_track) }
                ) {
                    Icon(
                        painterResource(id = R.drawable.ic_skip_previous_rounded),
                        contentDescription = null,
                        tint = if (controlsEnabled) Color.White else Color(0x66FFFFFF),
                        modifier = Modifier.size(26.dp)
                    )
                }
                // Play / Pause Button with tactile spring scale and refined gold/amber accent rim
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .graphicsLayer {
                            scaleX = playPauseScale
                            scaleY = playPauseScale
                        }
                        .shadow(
                            elevation = if (isPlaying) 8.dp else 3.dp,
                            shape = CircleShape,
                            ambientColor = Color(0x33FFD700),
                            spotColor = Color(0x55FFD700)
                        )
                        .clip(CircleShape)
                        .background(if (playControlsEnabled) Color.White else Color(0xFF666666))
                        .border(BorderStroke(1.5.dp, MusicMateDesignTokens.Brand.Gold.copy(alpha = 0.5f)), CircleShape)
                        .semantics {
                            role = Role.Button
                            contentDescription = context.getString(if (isPlaying) R.string.cd_pause else R.string.cd_play)
                        }
                        .clickable(enabled = playControlsEnabled) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onPlayPause()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(id = if (isPlaying) R.drawable.ic_pause_rounded else R.drawable.ic_play_rounded),
                        contentDescription = null,
                        tint = Color(0xFF141414),
                        modifier = Modifier.size(30.dp)
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onNext()
                    },
                    enabled = playControlsEnabled,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = context.getString(R.string.cd_next_track) }
                ) {
                    Icon(
                        painterResource(id = R.drawable.ic_skip_next_rounded),
                        contentDescription = null,
                        tint = if (playControlsEnabled) Color.White else Color(0x66FFFFFF),
                        modifier = Modifier.size(26.dp)
                    )
                }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onRepeatToggle()
                    },
                    enabled = controlsEnabled,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = context.getString(
                                when (state.repeatMode.value) {
                                    1 -> R.string.cd_repeat_all
                                    2 -> R.string.cd_repeat_one
                                    else -> R.string.cd_repeat_off
                                }
                            )
                        }
                ) {
                    val repeatIcon = if (state.repeatMode.value == 2) R.drawable.ic_baseline_repeat_one_24 else R.drawable.ic_baseline_repeat_24
                    Icon(
                        painterResource(id = repeatIcon),
                        contentDescription = null,
                        tint = if (state.repeatMode.value > 0) colorGold else colorGrey400,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        showSleepTimerDialog = true
                    },
                    enabled = controlsEnabled,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = if (state.isSleepTimerActive.value) {
                                context.getString(R.string.cd_sleep_timer_remaining, state.sleepTimerText.value)
                            } else {
                                context.getString(R.string.cd_sleep_timer_off)
                            }
                        }
                ) {
                    Icon(
                        painterResource(id = R.drawable.ic_baseline_timer_24),
                        contentDescription = null,
                        tint = if (state.isSleepTimerActive.value) colorGold else colorGrey400,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            activeText = state.sleepTimerText.value,
            armedMinutes = state.sleepTimerMinutes.value,
            onDismissRequest = { showSleepTimerDialog = false },
            onSelectOption = { minutes, endOfTrack ->
                onSleepTimerSelected(minutes, endOfTrack)
            }
        )
    }
}

@Composable
fun SleepTimerDialog(
    activeText: String,
    armedMinutes: Long,
    onDismissRequest: () -> Unit,
    onSelectOption: (minutes: Long, endOfTrack: Boolean) -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.90f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "timer_dialog_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(180),
        label = "timer_dialog_alpha"
    )

    Dialog(onDismissRequest = onDismissRequest) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1C1C1E))
                .border(1.dp, MusicMateDesignTokens.Surfaces.BorderPrimary, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Sleep Timer",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (activeText.isNotEmpty()) {
                    Text(
                        text = if (armedMinutes > 0) "Stops in $activeText" else "Stops at the end of this track",
                        color = MusicMateDesignTokens.Text.Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                val options = listOf(
                    "15 Minutes" to Pair(15L, false),
                    "30 Minutes" to Pair(30L, false),
                    "45 Minutes" to Pair(45L, false),
                    "60 Minutes" to Pair(60L, false),
                    "End of Current Track" to Pair(0L, true),
                    "Turn Off Timer" to Pair(0L, false)
                )
                options.forEach { (label, option) ->
                    val (minutes, endOfTrack) = option
                    val isSelected = isSleepOptionSelected(minutes, endOfTrack, activeText, armedMinutes)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0x26FFD700) else Color.Transparent)
                            .clickable {
                                onSelectOption(minutes, endOfTrack)
                                onDismissRequest()
                            }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) MusicMateDesignTokens.Brand.Gold else Color(0xFFE0E0E0),
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_timer_24),
                                contentDescription = null,
                                tint = MusicMateDesignTokens.Brand.Gold,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Whether a Sleep Timer row is the one running. A countdown row matches the minutes it was set
 * with, not the remaining text, which reads "29m" a minute after choosing 30.
 */
internal fun isSleepOptionSelected(minutes: Long, endOfTrack: Boolean, activeText: String, armedMinutes: Long): Boolean = when {
    activeText.isEmpty() -> !endOfTrack && minutes == 0L
    endOfTrack -> armedMinutes == 0L
    else -> minutes > 0 && minutes == armedMinutes
}

private fun accessiblePlaybackTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** Soft shadow that keeps text over album art readable on bright covers. */
private val ArtTextShadow = androidx.compose.ui.graphics.Shadow(
    color = Color(0x99000000),
    offset = androidx.compose.ui.geometry.Offset(0f, 2f),
    blurRadius = 6f
)

/** Idle subtitle: Play starts the queue when nothing is loaded, so say what it will play. */
internal fun idleQueueText(queuedCount: Int): String = when {
    queuedCount <= 0 -> "Select a song"
    queuedCount == 1 -> "1 track queued · press Play"
    else -> String.format(java.util.Locale.getDefault(), "%,d tracks queued · press Play", queuedCount)
}
