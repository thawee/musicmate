package apincer.android.mmate.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import apincer.music.core.model.Track
import androidx.compose.foundation.lazy.rememberLazyListState

@Composable
fun MusicListScreen(
    tracks: List<Track>,
    selectedTracks: Set<Track>,
    nowPlayingTrack: Track?,
    isPlaying: Boolean,
    onTrackClick: (Track, Int) -> Unit,
    onTrackLongClick: (Track, Int) -> Unit,
    onTrackMenuClick: (Track, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tracks.isEmpty()) {
        // Empty State
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "No Music Found",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Scan your device to find music files.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        val listState = rememberLazyListState()
        
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp) // padding for bottom nav
        ) {
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                val isSelected = selectedTracks.contains(track)
                val isNowPlaying = nowPlayingTrack?.id == track.id
                
                TrackListItem(
                    track = track,
                    isSelected = isSelected,
                    isNowPlaying = isNowPlaying,
                    isPlaying = isPlaying,
                    onClick = { onTrackClick(track, index) },
                    onLongClick = { onTrackLongClick(track, index) },
                    onMenuClick = { onTrackMenuClick(track, index) }
                )
            }
        }
    }
}
