package apincer.android.mmate.ui.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.R
import apincer.android.mmate.coil3.CoverartFetcher
import apincer.android.mmate.utils.GestureHints
import apincer.music.core.model.Track
import apincer.music.core.repository.QueueManager
import apincer.music.core.utils.StringUtils
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SourceOption(
    val source: QueueManager.Source,
    val title: String,
    val subtitle: String,
    val iconRes: Int,
    val accentColor: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueuePage(
    state: QueueState,
    onTrackClicked: (Track) -> Unit,
    onTrackRemoved: (Track, Int) -> Unit,
    onClearQueue: () -> Unit,
    onJumpToPlaying: () -> Unit,
    onBrowseLibrary: () -> Unit,
    onMoveTrack: (Int, Int) -> Unit = { _, _ -> }
) {
    val manager = state.manager ?: MainScaffoldState.get().queueState.manager
    var source by remember(manager) { mutableStateOf(manager?.source ?: QueueManager.Source.MANUAL) }
    var refreshing by remember { mutableStateOf(false) }
    var sourceError by remember { mutableStateOf<String?>(null) }
    var caughtUp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(manager) {
        if (manager == null) return@LaunchedEffect
        while (true) {
            source = manager.source
            caughtUp = manager.isSmartQueueCaughtUp
            val snapshot = manager.songs.toList()
            if (state.tracks.map { it.id } != snapshot.map { it.id }) {
                val seconds = snapshot.sumOf { it.audioDuration.coerceAtLeast(0.0).toLong() }
                state.updateQueue(
                    snapshot,
                    state.currentPlayingKey,
                    if (seconds > 0) StringUtils.formatDuration(seconds.toDouble(), true) else ""
                )
            }
            delay(1000)
        }
    }

    var confirmClear by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.tracks.isEmpty()) {
        if (state.tracks.isEmpty()) confirmClear = false
    }

    fun selectSource(choice: QueueManager.Source) {
        if (manager == null || refreshing) return
        refreshing = true
        sourceError = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    manager.setSource(choice)
                    manager.refreshSmartQueue()
                }
                source = manager.source
                MainScaffoldState.get().nowPlayingState.isShuffle.value = manager.isShuffle
                MainScaffoldState.get().nowPlayingState.repeatMode.value = when (manager.repeatMode) {
                    QueueManager.RepeatMode.ALL -> 1
                    QueueManager.RepeatMode.ONE -> 2
                    else -> 0
                }
                state.updateTracks(manager.songs.toList())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                sourceError = "Couldn't refresh this source. Select it again to retry."
            } finally {
                refreshing = false
            }
        }
    }

    fun triggerManualRefresh() {
        if (manager == null || refreshing || source == QueueManager.Source.MANUAL) return
        refreshing = true
        sourceError = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    manager.refreshSmartQueue()
                }
                caughtUp = manager.isSmartQueueCaughtUp
                state.updateTracks(manager.songs.toList())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                sourceError = "Refill encountered an error. Tap to retry."
            } finally {
                refreshing = false
            }
        }
    }

    if (confirmClear && state.tracks.isNotEmpty()) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear queue?") },
            text = { Text("Remove all ${state.tracks.size} tracks from the queue? This cannot be undone.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmClear = false
                    onClearQueue()
                }) { Text("Clear queue") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 2.dp)
    ) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Upcoming Queue",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp
                )
                Text(
                    text = "${state.tracks.size} tracks ${if (state.totalDurationText.isNotEmpty()) "• " + state.totalDurationText else ""}",
                    color = Color(0xFF9E9E9E),
                    fontSize = 11.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onJumpToPlaying, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_center_focus_strong_black_24dp),
                        contentDescription = "Jump to Now Playing",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = { confirmClear = true },
                    enabled = state.tracks.isNotEmpty(),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_delete_24),
                        contentDescription = "Clear Queue",
                        tint = if (state.tracks.isNotEmpty()) Color(0xFFB0BEC5) else Color(0xFF555555),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (manager != null) {
            // Live Engine HUD
            SmartQueueEngineHud(
                source = source,
                refreshing = refreshing,
                caughtUp = caughtUp,
                queueSize = state.tracks.size,
                onRefreshClick = { triggerManualRefresh() }
            )

            // Segmented Smart Source Capsule Deck
            SmartSourceDeck(
                selectedSource = source,
                refreshing = refreshing,
                onSourceSelected = { selectSource(it) }
            )

            // Smart Intelligence Subtitle Banner
            SmartSourceInfoBanner(
                source = source,
                sourceError = sourceError,
                caughtUp = caughtUp
            )
        }

        // Gesture discovery hints for queue
        if (state.tracks.isNotEmpty()) {
            GestureHints.GestureHintBanner(
                hints = listOf(
                    GestureHints.HintType.SWIPE_QUEUE,
                    GestureHints.HintType.DRAG_REORDER
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }

        if (state.tracks.isEmpty()) {
            QueueEmptyState(
                source = source,
                onBrowseLibrary = onBrowseLibrary,
                onSelectSource = { selectSource(it) }
            )
        } else {
            val currentIdx = manager?.currentIndex ?: -1
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
            ) {
                itemsIndexed(
                    items = state.tracks,
                    key = { index, track -> "${index}_${track.uniqueKey ?: track.id}" }
                ) { index, track ->
                    val isPlaying = track.uniqueKey == state.currentPlayingKey ||
                            (currentIdx in state.tracks.indices && state.tracks[currentIdx].id == track.id)
                    val isSmart = manager?.isSmartSuggested(track.id) == true
                    val isNext = !isPlaying && !isSmart && index > currentIdx

                    val prevTrack = if (index > 0) state.tracks[index - 1] else null
                    val sectionHeader = getSectionHeader(
                        index = index,
                        track = track,
                        prevTrack = prevTrack,
                        currentIdx = currentIdx,
                        source = source,
                        manager = manager
                    )

                    if (sectionHeader != null) {
                        QueueSectionHeader(
                            title = sectionHeader.first,
                            accentColor = sectionHeader.second
                        )
                    }

                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart || it == SwipeToDismissBoxValue.StartToEnd) {
                                onTrackRemoved(track, index)
                                true
                            } else {
                                false
                            }
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val isDismissing = dismissState.dismissDirection != SwipeToDismissBoxValue.Settled
                            val bgBrush = if (isDismissing) {
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, Color(0x33FF5252), Color(0x66FF5252))
                                )
                            } else {
                                Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bgBrush)
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                if (isDismissing) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.rounded_delete_24),
                                        contentDescription = "Delete",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        },
                        content = {
                            QueueItem(
                                track = track,
                                index = index,
                                isPlaying = isPlaying,
                                isSmart = isSmart,
                                isNext = isNext,
                                onClick = { onTrackClicked(track) },
                                totalCount = state.tracks.size,
                                onMoveTrack = { from, to ->
                                    state.moveTrack(from, to)
                                    onMoveTrack(from, to)
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartQueueEngineHud(
    source: QueueManager.Source,
    refreshing: Boolean,
    caughtUp: Boolean,
    queueSize: Int,
    onRefreshClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val (ledColor, statusLabel) = when {
        refreshing -> Color(0xFFFFB300) to "REPLENISHING…"
        source == QueueManager.Source.MANUAL -> Color(0xFF78909C) to "MANUAL QUEUE"
        caughtUp -> Color(0xFF00E676) to "ALL CAUGHT UP"
        else -> Color(0xFF00E676) to "AUTO-FILL ACTIVE"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator pill with jewel LED
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x14FFFFFF))
                .border(0.75.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (refreshing || (source != QueueManager.Source.MANUAL && !caughtUp))
                            ledColor.copy(alpha = pulseAlpha)
                        else ledColor
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusLabel,
                color = ledColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.4.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (source != QueueManager.Source.MANUAL) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x12FFFFFF))
                        .border(0.5.dp, Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "SLOTS: ${queueSize.coerceAtMost(20)}/20",
                        color = Color(0xFFB0BEC5),
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRefreshClick()
                    },
                    enabled = !refreshing,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_refresh_24),
                        contentDescription = "Replenish Smart Queue",
                        tint = if (refreshing) Color(0xFFFFB300) else Color(0xFFB0BEC5),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartSourceDeck(
    selectedSource: QueueManager.Source,
    refreshing: Boolean,
    onSourceSelected: (QueueManager.Source) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val sources = remember {
        listOf(
            SourceOption(QueueManager.Source.MANUAL, "Manual", "Static", R.drawable.ic_round_queue_music_24, Color(0xFFB0BEC5)),
            SourceOption(QueueManager.Source.NEW, "New", "Unorganized", R.drawable.round_auto_awesome_24, Color(0xFFFFD700)),
            SourceOption(QueueManager.Source.DOWNLOADS, "Downloads", "Device", R.drawable.ic_round_download_24, Color(0xFF00E5FF)),
            SourceOption(QueueManager.Source.UNPLAYED, "Discover", "Unplayed", R.drawable.ic_round_explore_24, Color(0xFF00E676)),
            SourceOption(QueueManager.Source.REDISCOVER, "Rediscover", "30d+ old", R.drawable.rounded_music_history_24, Color(0xFFFF7043))
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        sources.forEach { opt ->
            val isSelected = opt.source == selectedSource
            val bgModifier = if (isSelected) {
                Modifier
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                opt.accentColor.copy(alpha = 0.22f),
                                opt.accentColor.copy(alpha = 0.08f)
                            )
                        )
                    )
                    .border(1.dp, opt.accentColor.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
            } else {
                Modifier
                    .background(Color(0x14FFFFFF))
                    .border(0.75.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .then(bgModifier)
                    .clickable(enabled = !refreshing) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSourceSelected(opt.source)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = opt.iconRes),
                    contentDescription = null,
                    tint = if (isSelected) opt.accentColor else Color(0x88FFFFFF),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = opt.title,
                        color = if (isSelected) Color.White else Color(0xBBFFFFFF),
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                    Text(
                        text = opt.subtitle,
                        color = if (isSelected) opt.accentColor.copy(alpha = 0.9f) else Color(0x66FFFFFF),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartSourceInfoBanner(
    source: QueueManager.Source,
    sourceError: String?,
    caughtUp: Boolean
) {
    val description = sourceError ?: when {
        caughtUp -> "All matching library tracks queued. Will auto-append new matches."
        source == QueueManager.Source.MANUAL -> "Static manual queue • Drag to reorder, swipe to remove • Shuffle & Repeat enabled"
        source == QueueManager.Source.NEW -> "✦ Unorganized tracks in library order • Auto-refills upcoming • Manual priority preserved"
        source == QueueManager.Source.DOWNLOADS -> "✦ Downloaded tracks (Telegram / outside /Music/) • Auto-refills upcoming • Manual priority preserved"
        source == QueueManager.Source.UNPLAYED -> "✦ Unplayed tracks (0 completions recorded) • Auto-refills upcoming • Tracking starts now"
        source == QueueManager.Source.REDISCOVER -> "✦ Previously completed tracks unheard for 30+ days • Oldest first • Auto-refills upcoming"
        else -> ""
    }

    val isError = sourceError != null
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (isError) Color(0x22FF5252) else Color(0x0EFFFFFF))
            .border(
                0.75.dp,
                if (isError) Color(0x66FF5252) else Color(0x16FFFFFF),
                RoundedCornerShape(9.dp)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = description,
            color = if (isError) Color(0xFFFF8A80) else Color(0xFFB0BEC5),
            fontSize = 10.5.sp,
            lineHeight = 14.sp
        )
    }
}

private fun getSectionHeader(
    index: Int,
    track: Track,
    prevTrack: Track?,
    currentIdx: Int,
    source: QueueManager.Source,
    manager: QueueManager?
): Pair<String, Color>? {
    val isSmart = manager?.isSmartSuggested(track.id) == true
    val prevSmart = prevTrack != null && manager?.isSmartSuggested(prevTrack.id) == true

    if (index == currentIdx || (currentIdx == -1 && index == 0)) {
        return "NOW PLAYING" to Color(0xFFFFD700)
    }
    if (index == currentIdx + 1 && !isSmart) {
        return "UP NEXT · MANUAL PRIORITY" to Color(0xFF00E5FF)
    }
    if (isSmart && (index == currentIdx + 1 || !prevSmart)) {
        val label = when (source) {
            QueueManager.Source.NEW -> "NEW ARRIVALS"
            QueueManager.Source.DOWNLOADS -> "DOWNLOADS"
            QueueManager.Source.UNPLAYED -> "DISCOVERIES"
            QueueManager.Source.REDISCOVER -> "REDISCOVER"
            else -> "SMART REFILL"
        }
        return "✦ SMART REFILL · $label" to Color(0xFF00E676)
    }
    return null
}

@Composable
private fun QueueSectionHeader(title: String, accentColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(accentColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            color = accentColor,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(0.5.dp)
                .background(Color(0x22FFFFFF))
        )
    }
}

@Composable
fun QueueItem(
    track: Track,
    index: Int,
    isPlaying: Boolean,
    isSmart: Boolean = false,
    isNext: Boolean = false,
    onClick: () -> Unit,
    totalCount: Int = 0,
    onMoveTrack: ((Int, Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val artist = track.artist?.takeIf { it.isNotEmpty() } ?: track.album ?: "Unknown Artist"
    val durationStr = if (track.audioDuration > 0) StringUtils.formatDuration(track.audioDuration, false) else ""

    val rowBg = if (isPlaying) Color(0x22FFD700) else Color.Transparent
    val borderModifier = if (isPlaying) {
        Modifier.border(BorderStroke(1.dp, Color(0x4DFFD700)), RoundedCornerShape(10.dp))
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(rowBg)
            .then(borderModifier)
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Index / Playing Indicator
        Box(
            modifier = Modifier.width(22.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isPlaying) "▶" else "${index + 1}",
                color = if (isPlaying) Color(0xFFFFD700) else Color(0xFF757575),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Cover Art Thumbnail (42dp squircle) with Coil AsyncImage
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF202020))
                .border(
                    width = if (isPlaying) 1.5.dp else 0.75.dp,
                    color = if (isPlaying) Color(0xFFFFD700) else Color(0x22FFFFFF),
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = CoverartFetcher.builder(context, track).data(track).build(),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x55000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_equalizer_active),
                        contentDescription = "Playing",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Title, Artist, and Micro-Badges
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp)
        ) {
            Text(
                text = track.title ?: "Unknown Title",
                color = if (isPlaying) Color(0xFFFFD700) else Color.White,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = artist,
                color = Color(0xFFAAAAAA),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            // Badges row: Quality Badge + Provenance Micro-Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                QualityBadge(track = track, expanded = false)

                if (isPlaying) {
                    ProvenanceBadge(label = "NOW PLAYING", color = Color(0xFFFFD700), bg = Color(0x26FFD700))
                } else if (isNext) {
                    ProvenanceBadge(label = "PLAY NEXT", color = Color(0xFF00E5FF), bg = Color(0x2200E5FF))
                } else if (isSmart) {
                    ProvenanceBadge(label = "SMART", color = Color(0xFF00E676), bg = Color(0x2200E676))
                }
            }
        }

        // Duration
        if (durationStr.isNotEmpty()) {
            Text(
                text = durationStr,
                color = Color(0xFF888888),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(end = 4.dp)
            )
        }

        // Vertical Drag Reorder Handle with Haptic Feedback
        var dragAccumulatedY by remember { mutableStateOf(0f) }
        Icon(
            painter = painterResource(id = R.drawable.rounded_drag_indicator_24),
            contentDescription = "Drag to reorder",
            tint = Color(0xFF757575),
            modifier = Modifier
                .size(32.dp)
                .padding(4.dp)
                .pointerInput(index, totalCount) {
                    detectVerticalDragGestures(
                        onDragStart = { dragAccumulatedY = 0f },
                        onDragEnd = { dragAccumulatedY = 0f },
                        onDragCancel = { dragAccumulatedY = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            dragAccumulatedY += dragAmount
                            val threshold = 52f
                            if (dragAccumulatedY > threshold && index < totalCount - 1) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onMoveTrack?.invoke(index, index + 1)
                                dragAccumulatedY -= threshold
                            } else if (dragAccumulatedY < -threshold && index > 0) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onMoveTrack?.invoke(index, index - 1)
                                dragAccumulatedY += threshold
                            }
                        }
                    )
                }
        )
    }
}

@Composable
private fun ProvenanceBadge(label: String, color: Color, bg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(0.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.2.sp
        )
    }
}

@Composable
private fun QueueEmptyState(
    source: QueueManager.Source,
    onBrowseLibrary: () -> Unit,
    onSelectSource: (QueueManager.Source) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x33FFD700), Color(0x05FFD700))
                    )
                )
                .border(1.dp, Color(0x44FFD700), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.round_auto_awesome_24),
                contentDescription = null,
                tint = Color(0xFFFFD700),
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = if (source == QueueManager.Source.MANUAL) "Manual Queue is Empty" else "Smart Queue is Ready",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (source == QueueManager.Source.MANUAL)
                "Tap any song or album from the library to start playback, or activate a Smart Queue source."
            else if (source == QueueManager.Source.REDISCOVER)
                "Rediscover builds as you listen: complete a track, then leave it unheard for 30 days."
            else "Auto-fill checks for matching tracks in your library. Select a smart discovery mode below to start.",
            color = Color(0xFF9E9E9E),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
        Spacer(modifier = Modifier.height(20.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (source == QueueManager.Source.MANUAL) {
                Button(
                    onClick = { onSelectSource(QueueManager.Source.UNPLAYED) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x2200E676),
                        contentColor = Color(0xFF00E676)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0x5500E676))
                ) {
                    Text("✨ Try Discoveries", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Button(
                onClick = onBrowseLibrary,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFB300),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Browse Library", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
