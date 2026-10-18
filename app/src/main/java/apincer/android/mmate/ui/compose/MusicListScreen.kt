package apincer.android.mmate.ui.compose

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.R
import apincer.android.mmate.utils.GestureHints
import apincer.music.core.model.Track

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MusicListScreen(
    tracks: List<Track>,
    selectedTracks: Set<Track>,
    nowPlayingTrack: Track?,
    isPlaying: Boolean,
    isRefreshing: Boolean,
    hasMoreItems: Boolean = false,
    loadError: String? = null,
    libraryEmpty: Boolean = false,
    hasActiveFilters: Boolean = false,
    onDiscoverMusic: () -> Unit = {},
    onClearFilters: () -> Unit = {},
    onBrowseAllMusic: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    scrollToIndex: Int = -1,
    onScrollComplete: () -> Unit = {},
    onRefresh: () -> Unit,
    onTrackClick: (Track, Int) -> Unit,
    onTrackLongClick: (Track, Int) -> Unit,
    onTrackMenuAction: (Track, Int, Int) -> Unit,
    onTrackQuickPlayClick: (Track) -> Unit = {},
    onFolderPlayClick: (Track) -> Unit,
    onFolderEnqueueClick: (Track) -> Unit,
    showGestureHints: Boolean = true,
    trackArtwork: (@Composable (Track) -> Unit)? = null,
    /** Destination of [tracks]; each destination keeps its own scroll position. */
    listKey: String = "",
    // Title and message for an empty collection (e.g. "No similar songs found"); null = generic text
    emptyCollectionText: Pair<String, String>? = null,
    /** Whether the playback service is bound; track menus hide playback actions otherwise. */
    playbackAvailable: Boolean = true,
    userPlaylistOpen: Boolean = false,
    /** Tap mode for row accessibility labels (Listener: tap plays; Curator: tap edits). */
    listenerTapMode: Boolean = true,
    modifier: Modifier = Modifier
) {
    val pullRefreshState = rememberPullToRefreshState()
    // Drilling into a collection starts at the top; Back returns to where that list was
    val savedPositions = remember { mutableMapOf<String, Pair<Int, Int>>() }

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
                // Rich Audiophile Empty State with Animated Pulse
                val infiniteTransition = rememberInfiniteTransition(label = "empty_state_pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 0.85f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2500, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.15f,
                    targetValue = 0.35f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2500, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(100.dp)
                    ) {
                        // Outer pulse ring
                        Box(
                            modifier = Modifier
                                .size(90.dp * pulseScale)
                                .background(
                                    color = Color(0xFFFFD700).copy(alpha = pulseAlpha * 0.4f),
                                    shape = CircleShape
                                )
                        )
                        // Inner ambient badge container
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(
                                    color = Color(0x33FFD700),
                                    shape = CircleShape
                                )
                                .border(1.dp, Color(0x66FFD700), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_nav_musicmate_menu),
                                contentDescription = null,
                                tint = MusicMateDesignTokens.Brand.Gold,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = when {
                            loadError != null -> "Couldn’t load music"
                            libraryEmpty -> "Add your music"
                            hasActiveFilters -> "No matching music"
                            emptyCollectionText != null -> emptyCollectionText.first
                            else -> "No tracks here yet"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = loadError ?: when {
                            libraryEmpty -> "Choose the folders containing your music, then scan to build your library."
                            hasActiveFilters -> "Try another search or clear your search and filters."
                            emptyCollectionText != null -> emptyCollectionText.second
                            else -> "This collection is empty. Browse your library to find music."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFAAAAAA),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = when {
                            loadError != null -> onRefresh
                            libraryEmpty -> onDiscoverMusic
                            hasActiveFilters -> onClearFilters
                            else -> onBrowseAllMusic
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FFD700)),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MusicMateDesignTokens.Brand.Gold
                        )
                    ) {
                        Icon(
                            // Refresh only for Retry/Scan; browsing gets a library icon
                            painter = painterResource(
                                id = if (loadError != null || libraryEmpty) R.drawable.ic_round_refresh_24
                                else R.drawable.rounded_library_music_24
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = when {
                                loadError != null -> "Retry"
                                libraryEmpty -> "Choose folders / Scan"
                                hasActiveFilters -> "Clear search and filters"
                                else -> "Browse all songs"
                            },
                            fontSize = 13.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                    }
                }
            }
        } else {
            val listState = remember(listKey) {
                val (index, offset) = savedPositions[listKey] ?: (0 to 0)
                LazyListState(index, offset)
            }
            DisposableEffect(listState) {
                onDispose {
                    savedPositions[listKey] = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                }
            }
            // Gesture discovery hints for new users. They scroll with the list as its first item:
            // pinned above it they filled the whole screen in landscape and at large text.
            val pendingHints = GestureHints.rememberPendingHints(
                // Describe what tap and long-press do in the current tap mode
                if (listenerTapMode) listOf(
                    GestureHints.HintType.TRACK_TAP,
                    GestureHints.HintType.ART_TAP,
                    GestureHints.HintType.LONG_PRESS_DETAILS
                ) else listOf(
                    GestureHints.HintType.TRACK_TAP_EDIT,
                    GestureHints.HintType.ART_TAP,
                    GestureHints.HintType.LONG_PRESS
                )
            )
            // Track indexes are list indexes shifted by the hint item, when it shows
            val headerCount = if (showGestureHints && pendingHints.isNotEmpty()) 1 else 0

            LaunchedEffect(listState, tracks.size, hasMoreItems, isRefreshing, loadError, headerCount) {
                if (hasMoreItems && !isRefreshing && loadError == null) {
                    snapshotFlow {
                        (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) - headerCount >= tracks.size - 10
                    }.distinctUntilChanged().collect { nearEnd ->
                        if (nearEnd) onLoadMore()
                    }
                }
            }

            // Trigger scroll when scrollToIndex changes
            LaunchedEffect(scrollToIndex) {
                if (scrollToIndex >= 0 && scrollToIndex < tracks.size) {
                    listState.animateScrollToItem(scrollToIndex + headerCount)
                    onScrollComplete()
                }
            }
            
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
                    if (headerCount > 0) {
                        item(key = "gesture-hints") {
                            GestureHints.GestureHintBanner(pending = pendingHints)
                        }
                    }
                    itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                        if (track.isContainer) {
                            FolderListItem(
                                track = track,
                                onClick = { onTrackClick(track, index) },
                                onPlayClick = { onFolderPlayClick(track) },
                                onEnqueueClick = { onFolderEnqueueClick(track) },
                                onLongClick = { onTrackLongClick(track, index) }
                            )
                        } else {
                            val isSelected = selectedTracks.contains(track)
                            val isNowPlaying = nowPlayingTrack != null && nowPlayingTrack.uniqueKey == track.uniqueKey

                            TrackListItem(
                                track = track,
                                showPlaybackActions = playbackAvailable,
                                showRemoveFromPlaylist = userPlaylistOpen,
                                listenerTapMode = listenerTapMode,
                                selectionActive = selectedTracks.isNotEmpty(),
                                isSelected = isSelected,
                                isNowPlaying = isNowPlaying,
                                isPlaying = isPlaying,
                                onClick = { onTrackClick(track, index) },
                                onLongClick = { onTrackLongClick(track, index) },
                                onMenuAction = { actionId -> onTrackMenuAction(track, index, actionId) },
                                onQuickPlayClick = { onTrackQuickPlayClick(track) },
                                artwork = trackArtwork
                            )
                        }
                    }
                    item(key = "pagination-status") {
                        if (loadError != null) {
                            androidx.compose.material3.TextButton(onClick = onLoadMore) {
                                Text("$loadError Tap to retry")
                            }
                        } else if (isRefreshing) {
                            Text("Loading music…", modifier = Modifier.padding(16.dp))
                        }
                    }
                }

                // Fast scrollbar overlay — gold thumb + letter bubble on drag
                FastScrollbar(
                    listState = listState,
                    totalItems = tracks.size + headerCount,
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp, bottom = 120.dp),
                    itemLabel = { index ->
                        tracks.getOrNull(index - headerCount)
                            ?.title
                            ?.trimStart()
                            ?.firstOrNull()
                            ?.uppercaseChar()
                            ?.toString() ?: "#"
                    }
                )
            }
    }
}
}
