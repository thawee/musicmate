package apincer.android.mmate.ui.compose

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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.palette.graphics.Palette
import apincer.android.mmate.R
import apincer.android.mmate.coil3.CoverartFetcher
import apincer.music.core.playback.PlaybackState
import apincer.music.core.utils.StringUtils
import apincer.music.core.utils.TagUtils
import coil3.compose.AsyncImage

@Composable
fun NowPlayingPage(
    state: NowPlayingState,
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
    onSelectTargetPlayer: () -> Unit = {}
) {
    var flipped by remember { mutableStateOf(false) }
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
            grade.contains("CD", ignoreCase = true) -> Pair(Color(0xFF1A237E), Color(0xFF0A0F2E)) // Royal Cobalt Blue
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
    val bitmap = state.albumArt.value
    val duration = state.durationMs.value
    val progress = state.progressMs.value
    val context = LocalContext.current
    val isPlaying = state.playbackState.value.currentState == apincer.music.core.playback.PlaybackState.State.PLAYING

    val colorGold = Color(0xFFFFB300)
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
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onPlayPause() },
                        onTap = { flipped = !flipped }
                    )
                }
                .pointerInput(Unit) {
                    var dragAmount = 0f
                    detectDragGestures(
                        onDragEnd = {
                            if (dragAmount > 80f) onPrevious()
                            else if (dragAmount < -80f) onNext()
                            dragAmount = 0f
                        }
                    ) { change, drag ->
                        change.consume()
                        dragAmount += drag.x
                    }
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
                            contentDescription = "Album Art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (track != null) {
                        AsyncImage(
                            model = CoverartFetcher.builder(context, track).data(track).build(),
                            contentDescription = "Album Art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF1E1E1E)),
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
                                    0.0f to Color.Transparent,
                                    0.35f to Color(0x40000000),
                                    0.65f to Color(0xCC000000),
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
                        // Title & Info Action Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = track?.title ?: "Music Mate Ready",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                                    .basicMarquee(iterations = Int.MAX_VALUE, velocity = 30.dp)
                                    .fadingEdge(startWidth = 10.dp, endWidth = 14.dp)
                            )

                            Icon(
                                painter = painterResource(id = R.drawable.ic_round_info_24),
                                contentDescription = "Audio Anatomy Specs",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable { onTrackClicked() }
                            )
                        }

                        // Artist Subtitle
                        Text(
                            text = track?.artist ?: "Select a song",
                            color = Color(0xFFDDDDDD),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(iterations = Int.MAX_VALUE, velocity = 24.dp)
                                .fadingEdge(startWidth = 8.dp, endWidth = 12.dp)
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
                        val targetTitle = state.targetTitle.value.ifEmpty { "Local Audio" }
                        val isBitPerfect = targetTitle.contains("Bit-Perfect", ignoreCase = true) || state.targetBadge.value.contains("Bit-Perfect", ignoreCase = true)
                        val isDLNA = targetTitle.contains("DLNA", ignoreCase = true) || state.targetBadge.value.contains("DLNA", ignoreCase = true)
                        val isBT = targetTitle.contains("BT", ignoreCase = true) || targetTitle.contains("Bluetooth", ignoreCase = true) || state.targetBadge.value.contains("Bluetooth", ignoreCase = true)
                        val targetDotColor = when {
                            isBitPerfect -> Color(0xFF00E676)
                            isDLNA -> Color(0xFF00E5FF)
                            isBT -> Color(0xFF64B5F6)
                            else -> Color(0xFFFFD700)
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xD9141414))
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
                                text = targetTitle,
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
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
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
                                .padding(horizontal = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val codecStr = track?.audioEncoding?.uppercase()?.ifEmpty { null } ?: state.specsFormat.value.split("•").firstOrNull()?.trim() ?: "UNKNOWN"
                        val sampleRateStr = if (track?.audioSampleRate != null && track.audioSampleRate > 0) "${track.audioSampleRate / 1000.0} kHz" else ""
                        val bitDepthStr = if (track?.audioBitsDepth != null && track.audioBitsDepth > 0) "${track.audioBitsDepth}-bit" else ""
                        val resolutionStr = if (bitDepthStr.isNotEmpty() || sampleRateStr.isNotEmpty()) "$bitDepthStr $sampleRateStr".trim() else state.specsFormat.value
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

                        // Row 1: Resolution & Bitrate
                        val hasResolution = resolutionStr.isNotEmpty() && resolutionStr != "-"
                        val hasBitrate = bitrateStr.isNotEmpty() && bitrateStr != "-"
                        if (hasResolution || hasBitrate) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
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
                                        text = if (hasResolution) " • $bitrateStr" else bitrateStr,
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (hasChannels) {
                                    Text(
                                        text = channelsStr,
                                        color = Color(0xFFEEEEEE),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                if (hasDr) {
                                    Text(
                                        text = if (hasChannels) " • $drStr" else drStr,
                                        color = Color(0xFFFFA000),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                if (hasRg) {
                                    Text(
                                        text = if (hasChannels || hasDr) " • $rgStr" else rgStr,
                                        color = Color(0xFF64B5F6),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Row 3: Duration & File Size
                        val hasDuration = durationStr.isNotEmpty()
                        val hasFileSize = fileSizeStr.isNotEmpty() && fileSizeStr != "-"
                        if (hasDuration || hasFileSize) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (hasDuration) {
                                    Text(
                                        text = durationStr,
                                        color = Color(0xFFBDBDBD),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.4.sp
                                    )
                                }
                                if (hasFileSize) {
                                    Text(
                                        text = if (hasDuration) " • $fileSizeStr" else fileSizeStr,
                                        color = Color(0xFF9E9E9E),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.3.sp
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
                        Text(text = "Tap to flip back", color = Color(0xFF757575), fontSize = 12.sp)
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
                onValueChange = { pos ->
                    if (!isDragging) isDragging = true
                    dragPosition = pos
                },
                onValueChangeFinished = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onSeek(dragPosition)
                    isDragging = false
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(animatedAmbientColor.copy(alpha = 0.4f), CircleShape)
                            .border(0.75.dp, animatedAmbientColor.copy(alpha = 0.7f), CircleShape),
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
                            activeTrackColor = animatedAmbientColor.copy(alpha = 0.95f),
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

            Spacer(modifier = Modifier.height(10.dp))

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
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        painterResource(id = R.drawable.ic_baseline_shuffle_24),
                        contentDescription = "Shuffle",
                        tint = if (state.isShuffle.value) colorGold else colorGrey400,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onPrevious()
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        painterResource(id = R.drawable.ic_skip_previous_rounded),
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))

                // Play / Pause Button with tactile spring scale
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .graphicsLayer {
                            scaleX = playPauseScale
                            scaleY = playPauseScale
                        }
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onPlayPause()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(id = if (isPlaying) R.drawable.ic_pause_rounded else R.drawable.ic_play_rounded),
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onNext()
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        painterResource(id = R.drawable.ic_skip_next_rounded),
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onRepeatToggle()
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    val repeatIcon = if (state.repeatMode.value == 2) R.drawable.ic_baseline_repeat_one_24 else R.drawable.ic_baseline_repeat_24
                    Icon(
                        painterResource(id = repeatIcon),
                        contentDescription = "Repeat",
                        tint = if (state.repeatMode.value > 0) colorGold else colorGrey400,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        showSleepTimerDialog = true
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        painterResource(id = R.drawable.ic_baseline_timer_24),
                        contentDescription = "Sleep Timer",
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
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Sleep Timer",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
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
                    val isSelected = when {
                        endOfTrack -> activeText.contains("Track", ignoreCase = true)
                        minutes > 0 -> activeText == "${minutes}m"
                        else -> activeText.isEmpty()
                    }
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
                            color = if (isSelected) Color(0xFFFFD700) else Color(0xFFE0E0E0),
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_timer_24),
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
