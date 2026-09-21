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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueuePage(
    state: QueueState,
    onTrackClicked: (Track) -> Unit,
    onTrackRemoved: (Track, Int) -> Unit,
    onClearQueue: () -> Unit,
    onJumpToPlaying: () -> Unit,
    onBrowseLibrary: () -> Unit = {},
    onMoveTrack: (Int, Int) -> Unit = { _, _ -> }
) {
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
                IconButton(onClick = onClearQueue, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_delete_24),
                        contentDescription = "Clear Queue",
                        tint = Color(0xFF9E9E9E),
                        modifier = Modifier.size(20.dp)
                    )
                }
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
                    text = "Queue is Empty",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tap any song or album from the library to start streaming.",
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