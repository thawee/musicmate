package apincer.android.mmate.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import apincer.android.mmate.R
import apincer.music.core.model.Track
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MusicListScreen(
    tracks: List<Track>,
    selectedTracks: Set<Track>,
    nowPlayingTrack: Track?,
    isPlaying: Boolean,
    isRefreshing: Boolean,
    scrollToIndex: Int = -1,
    onScrollComplete: () -> Unit = {},
    onRefresh: () -> Unit,
    onTrackClick: (Track, Int) -> Unit,
    onTrackLongClick: (Track, Int) -> Unit,
    onTrackMenuClick: (Track, Int) -> Unit,
    onFolderPlayClick: (Track) -> Unit,
    onFolderEnqueueClick: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val pullRefreshState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = modifier.fillMaxSize()
    ) {
        if (tracks.isEmpty()) {
            if (isRefreshing) {
                // Skeleton Shimmer Loading State
                val shimmerBrush = rememberShimmerBrush()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    repeat(7) {
                        TrackListItemShimmer(brush = shimmerBrush)
                    }
                }
            } else {
                // Empty State with Brand Insignia
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_nav_musicmate_menu),
                        contentDescription = null,
                        tint = Color(0x66FFFFFF),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Music Found",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Scan your device or check storage permissions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFAAAAAA)
                    )
                }
            }
        } else {
            val listState = rememberLazyListState()

            // Trigger scroll when scrollToIndex changes
            LaunchedEffect(scrollToIndex) {
                if (scrollToIndex >= 0 && scrollToIndex < tracks.size) {
                    listState.animateScrollToItem(scrollToIndex)
                    onScrollComplete()
                }
            }
            
            // Only show FAB when scrolled down a bit
            val showFab by remember {
                derivedStateOf { listState.firstVisibleItemIndex > 5 }
            }

            // Box allows FastScrollbar and FAB to overlay on top
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = 120.dp,
                        top = 8.dp,
                        end = 10.dp   // make room for the scrollbar thumb
                    )
                ) {
                    itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                        if (track.isContainer) {
                            FolderListItem(
                                track = track,
                                onClick = { onTrackClick(track, index) },
                                onPlayClick = { onFolderPlayClick(track) },
                                onEnqueueClick = { onFolderEnqueueClick(track) }
                            )
                        } else {
                            val isSelected = selectedTracks.contains(track)
                            val isNowPlaying = nowPlayingTrack != null && nowPlayingTrack.uniqueKey == track.uniqueKey

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

                // Fast scrollbar overlay — gold thumb + letter bubble on drag
                FastScrollbar(
                    listState = listState,
                    totalItems = tracks.size,
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp, bottom = 120.dp),
                    itemLabel = { index ->
                        tracks.getOrNull(index)
                            ?.title
                            ?.trimStart()
                            ?.firstOrNull()
                            ?.uppercaseChar()
                            ?.toString() ?: "#"
                    }
                )

                // Glassmorphism Go to Top FAB
                AnimatedVisibility(
                    visible = showFab,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = 112.dp) // matches old xml margins
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp) // clickable area
                            .clip(CircleShape)
                            .background(Color(0x80000000)) // dark base
                            .background(Color(0x1AFFFFFF)) // frosted glass
                            .border(1.5.dp, Color(0x4DFFFFFF), CircleShape) // glass edge
                            .clickable {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_arrow_shape_up_stack_24),
                            contentDescription = "Scroll to top",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
