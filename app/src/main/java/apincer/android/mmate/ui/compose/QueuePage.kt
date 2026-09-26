package apincer.android.mmate.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.R
import apincer.android.mmate.utils.GestureHints
import apincer.music.core.model.Track
import apincer.music.core.utils.StringUtils
import apincer.music.core.repository.QueueManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
    var sourceMenu by remember { mutableStateOf(false) }
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
                state.updateQueue(snapshot, state.currentPlayingKey,
                    if (seconds > 0) StringUtils.formatDuration(seconds.toDouble(), true) else "")
            }
            delay(1000)
        }
    }
    var confirmClear by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(state.tracks.isEmpty()) {
        if (state.tracks.isEmpty()) confirmClear = false
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
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
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
                        tint = Color(0xFF9E9E9E),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (manager != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    TextButton(onClick = { sourceMenu = true }, enabled = !refreshing) {
                        Text("Source: ${when (source) {
                            QueueManager.Source.MANUAL -> "Manual"
                            QueueManager.Source.NEW -> "New"
                            QueueManager.Source.DOWNLOADS -> "Downloads"
                            QueueManager.Source.UNPLAYED -> "Unplayed Discoveries"
                            QueueManager.Source.REDISCOVER -> "Rediscover"
                        }} ▾")
                    }
                    DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                        QueueManager.Source.values().forEach { choice ->
                            DropdownMenuItem(text = { Text(when (choice) {
                                QueueManager.Source.MANUAL -> "Manual · keep this queue"
                                QueueManager.Source.NEW -> "New · unorganized tracks"
                                QueueManager.Source.DOWNLOADS -> "Downloads · all downloaded tracks"
                                QueueManager.Source.UNPLAYED -> "Unplayed Discoveries · no completed listens"
                                QueueManager.Source.REDISCOVER -> "Rediscover · not played in 30 days"
                            }) }, onClick = {
                                sourceMenu = false
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
                                    } finally { refreshing = false }
                                }
                            })
                        }
                    }
                }
                Text(if (refreshing) "Refreshing…" else if (source == QueueManager.Source.MANUAL) "Auto-fill off" else "Auto-fill on",
                    color = Color(0xFFBCC5CF), fontSize = 11.sp)
            }
            if (source != QueueManager.Source.MANUAL || sourceError != null) {
                Text(sourceError ?: if (caughtUp) "You're caught up. New matching tracks will be added automatically." else when (source) {
                    QueueManager.Source.NEW -> "Unorganized tracks • library order • shuffle/repeat off"
                    QueueManager.Source.UNPLAYED -> "No completed listen recorded by MusicMate • tracking starts now"
                    QueueManager.Source.REDISCOVER -> "Previously completed • not played for 30 days • oldest first"
                    else -> "Current Downloads category • library order • shuffle/repeat off"
                }, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    color = Color(0xFFBCC5CF), fontSize = 11.sp)
            }
        }

        // Gesture discovery hints for queue
        if (state.tracks.isNotEmpty()) {
            GestureHints.GestureHintBanner(
                hints = listOf(
                    GestureHints.HintType.SWIPE_QUEUE,
                    GestureHints.HintType.DRAG_REORDER
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        if (state.tracks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFB300))
                        .border(1.dp, Color(0x33FFB300), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_queue_music_24),
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (source == QueueManager.Source.MANUAL) "Queue is Empty" else "No matching tracks queued",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (source == QueueManager.Source.MANUAL)
                        "Tap any song or album from the library to start streaming."
                    else if (source == QueueManager.Source.REDISCOVER)
                        "Rediscover builds as you listen: complete a track, then leave it unheard for 30 days."
                    else "Auto-fill checks for matching library tracks. Choose another source above or browse your library.",
                    color = Color(0xFF9E9E9E),
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onBrowseLibrary,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFB300),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Browse Library",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(items = state.tracks, key = { index, track -> "${index}_${track.uniqueKey ?: track.id}" }) { index, track ->
                    val isPlaying = track.uniqueKey == state.currentPlayingKey
                    
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
                            val color = if (isDismissing) Color(0x33FF5252) else Color.Transparent
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                if (isDismissing) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.rounded_delete_24),
                                        contentDescription = "Delete",
                                        tint = Color(0xFFFF5252)
                                    )
                                }
                            }
                        },
                        content = {
                            QueueItem(
                                track = track,
                                index = index,
                                totalCount = state.tracks.size,
                                isPlaying = isPlaying,
                                onClick = { onTrackClicked(track) },
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
fun QueueItem(
    track: Track,
    index: Int,
    isPlaying: Boolean,
    onClick: () -> Unit,
    totalCount: Int = 0,
    onMoveTrack: ((Int, Int) -> Unit)? = null
) {
    val artist = track.artist?.takeIf { it.isNotEmpty() } ?: track.album ?: ""
    val durationStr = if (track.audioDuration > 0) StringUtils.formatDuration(track.audioDuration, false) else ""
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isPlaying) Color(0x22FFD700) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isPlaying) "▶" else "${index + 1}",
            color = if (isPlaying) Color(0xFFFFD700) else Color(0xFF757575),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(26.dp)
        )
        
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(
                text = track.title ?: "Unknown Title",
                color = if (isPlaying) Color(0xFFFFD700) else Color.White,
                fontSize = 13.5.sp,
                lineHeight = 17.sp,
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist,
                color = Color(0xFF9E9E9E),
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (durationStr.isNotEmpty()) {
            Text(
                text = durationStr,
                color = Color(0xFF757575),
                fontSize = 11.sp,
                modifier = Modifier.padding(end = 6.dp)
            )
        }

        // Functional drag handle with vertical drag detection and haptic feedback
        var dragAccumulatedY by remember { mutableStateOf(0f) }
        Icon(
            painter = painterResource(id = R.drawable.rounded_drag_indicator_24),
            contentDescription = "Drag to reorder",
            tint = Color(0xFF888888),
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
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onMoveTrack?.invoke(index, index + 1)
                                dragAccumulatedY -= threshold
                            } else if (dragAccumulatedY < -threshold && index > 0) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onMoveTrack?.invoke(index, index - 1)
                                dragAccumulatedY += threshold
                            }
                        }
                    )
                }
        )
    }
}
