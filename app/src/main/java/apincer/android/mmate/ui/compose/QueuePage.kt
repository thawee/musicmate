package apincer.android.mmate.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.R
import apincer.music.core.model.Track
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueuePage(
    state: QueueState,
    onTrackClicked: (Track) -> Unit,
    onTrackRemoved: (Track, Int) -> Unit,
    onClearQueue: () -> Unit,
    onJumpToPlaying: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 6.dp)
    ) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Upcoming Queue",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${state.tracks.size} tracks ${if (state.totalDurationText.isNotEmpty()) "• " + state.totalDurationText else ""}",
                    color = Color(0xFF9E9E9E),
                    fontSize = 11.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onJumpToPlaying, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_center_focus_strong_black_24dp),
                        contentDescription = "Jump to Now Playing",
                        tint = Color(0xFFFFB300)
                    )
                }
                IconButton(onClick = onClearQueue, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_delete_24),
                        contentDescription = "Clear Queue",
                        tint = Color(0xFF9E9E9E)
                    )
                }
            }
        }

        if (state.tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Playback queue is empty",
                    color = Color(0xFFBDBDBD),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(items = state.tracks, key = { _, track -> track.uniqueKey ?: track.hashCode().toString() }) { index, track ->
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
                            val color = if (dismissState.dismissDirection != null) Color(0x33FF5252) else Color.Transparent
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                if (dismissState.dismissDirection != null) {
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
                                isPlaying = isPlaying,
                                onClick = { onTrackClicked(track) }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun QueueItem(track: Track, index: Int, isPlaying: Boolean, onClick: () -> Unit) {
    val artist = track.artist?.takeIf { it.isNotEmpty() } ?: track.album ?: ""
    val durationStr = if (track.audioDuration > 0) {
        val mins = (track.audioDuration / 60).toInt()
        val secs = (track.audioDuration % 60).toInt()
        String.format(Locale.US, "%d:%02d", mins, secs)
    } else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isPlaying) Color(0x22FFD700) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isPlaying) "▶" else "${index + 1}",
            color = if (isPlaying) Color(0xFFFFD700) else Color.DarkGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp)
        )
        
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = track.title ?: "Unknown Title",
                color = if (isPlaying) Color(0xFFFFD700) else Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist,
                color = Color(0xFF9E9E9E),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (durationStr.isNotEmpty()) {
            Text(
                text = durationStr,
                color = Color(0xFF757575),
                fontSize = 11.sp,
                modifier = Modifier.padding(end = 12.dp)
            )
        }

        // Drag handle placeholder for visual parity
        Icon(
            painter = painterResource(id = R.drawable.rounded_drag_indicator_24),
            contentDescription = "Drag to reorder",
            tint = Color(0xFF616161),
            modifier = Modifier.size(24.dp)
        )
    }
}
