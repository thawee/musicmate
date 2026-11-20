package apincer.android.mmate.ui.compose

import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.PlainTooltip
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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import androidx.annotation.StringRes
import apincer.android.mmate.R
import apincer.android.mmate.coil3.CoverartFetcher
import apincer.android.mmate.utils.GestureHints
import apincer.music.core.model.Track
import apincer.music.core.repository.PlaylistRepository
import apincer.music.core.repository.QueueManager
import apincer.music.core.utils.StringUtils
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import apincer.android.mmate.MusixMateApp
import apincer.music.core.model.PlaylistEntry
import apincer.music.core.model.SearchCriteria
import java.io.File

private const val TAG = "QueuePage"

private data class SourceOption(
    val source: QueueManager.Source,
    val label: String,
    val iconRes: Int,
    val mood: Boolean = false // a mood preset: the PLAYLIST source fed by a mood entry
)

/** A failed queue action: what to show, and the action that tapping the message runs again. */
private class QueueError(val message: String, val retry: () -> Unit)

@StringRes
private fun sourceLabel(source: QueueManager.Source): Int = when (source) {
    QueueManager.Source.MANUAL -> R.string.queue_source_manual
    QueueManager.Source.NEW -> R.string.queue_source_new
    QueueManager.Source.DOWNLOADS -> R.string.queue_source_downloads
    QueueManager.Source.UNPLAYED -> R.string.queue_source_discover
    QueueManager.Source.REDISCOVER -> R.string.queue_source_rediscover
    QueueManager.Source.PLAYLIST -> R.string.queue_source_playlist
}

private fun sourceAccent(source: QueueManager.Source, mood: Boolean = false): Color = when (source) {
    QueueManager.Source.MANUAL -> MusicMateDesignTokens.SourceAccent.Manual
    QueueManager.Source.NEW -> MusicMateDesignTokens.SourceAccent.New
    QueueManager.Source.DOWNLOADS -> MusicMateDesignTokens.SourceAccent.Downloads
    QueueManager.Source.UNPLAYED -> MusicMateDesignTokens.SourceAccent.Discover
    QueueManager.Source.REDISCOVER -> MusicMateDesignTokens.SourceAccent.Rediscover
    QueueManager.Source.PLAYLIST -> if (mood) MusicMateDesignTokens.SourceAccent.Mood else MusicMateDesignTokens.SourceAccent.Playlist
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueuePage(
    state: QueueState,
    onTrackClicked: (Track) -> Unit,
    onTrackRemoved: (Track, Int) -> Unit,
    onClearQueue: () -> Unit,
    onJumpToPlaying: () -> Unit,
    onBrowseLibrary: () -> Unit,
    onMoveTrack: (Int, Int) -> Unit = { _, _ -> },
    showGestureHints: Boolean = true,
    trackArtwork: (@Composable (Track) -> Unit)? = null
) {
    val manager = state.manager ?: MainScaffoldState.get().queueState.manager
    var source by remember(manager) { mutableStateOf(manager?.source ?: QueueManager.Source.MANUAL) }
    var activePlaylistName by remember(manager) { mutableStateOf(manager?.activePlaylistName) }
    var refreshing by remember { mutableStateOf(false) }
    var sourceError by remember { mutableStateOf<QueueError?>(null) }
    // the queue before the first of several source switches, while their Undo is still offered
    var undoSnapshot by remember { mutableStateOf<QueueManager.Snapshot?>(null) }
    var undoGeneration by remember { mutableStateOf(0) }
    var undoLabel by remember { mutableStateOf("") }
    var caughtUp by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showMoodPicker by remember { mutableStateOf(false) }
    val moodActive = remember(source, activePlaylistName) {
        source == QueueManager.Source.PLAYLIST &&
            activePlaylistName?.let { PlaylistRepository.getPlaylistByName(it) }?.isMood == true
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(manager) {
        if (manager == null) return@LaunchedEffect
        while (true) {
            source = manager.source
            activePlaylistName = manager.activePlaylistName
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

    fun syncFromManager(m: QueueManager) {
        source = m.source
        activePlaylistName = m.activePlaylistName
        caughtUp = m.isSmartQueueCaughtUp
        MainScaffoldState.get().nowPlayingState.isShuffle.value = m.isShuffle
        MainScaffoldState.get().nowPlayingState.repeatMode.value = when (m.repeatMode) {
            QueueManager.RepeatMode.ALL -> 1
            QueueManager.RepeatMode.ONE -> 2
            else -> 0
        }
        val tracks = m.songs.toList()
        val seconds = tracks.sumOf { it.audioDuration.coerceAtLeast(0.0).toLong() }
        state.updateQueue(
            tracks,
            state.currentPlayingKey,
            if (seconds > 0) StringUtils.formatDuration(seconds.toDouble(), true) else ""
        )
    }

    fun offerUndo(m: QueueManager, before: QueueManager.Snapshot, label: String) {
        val generation = ++undoGeneration
        undoSnapshot = before
        undoLabel = label
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.queue_source_replaced, label),
                actionLabel = context.getString(R.string.queue_undo),
                withDismissAction = true,
                // stays until dismissed: Undo is the only way back to a long hand-built queue
                duration = SnackbarDuration.Indefinite
            )
            // a later source switch replaced this snackbar and carries the same snapshot on
            if (generation == undoGeneration) undoSnapshot = null
            if (result != SnackbarResult.ActionPerformed) return@launch
            withContext(Dispatchers.IO) { m.restore(before) }
            syncFromManager(m)
        }
    }

    /** A source's tracks replace the upcoming ones; a source that finds nothing leaves the queue alone. */
    fun switchSource(label: String, errorMessage: Int, select: (QueueManager) -> Unit) {
        val m = manager ?: return
        if (refreshing) return
        refreshing = true
        sourceError = null
        scope.launch {
            try {
                val (before, replaced) = withContext(Dispatchers.IO) {
                    val before = m.snapshot()
                    select(m)
                    val replaced = m.replaceUpcomingFromSource()
                    if (!replaced) m.restore(before)
                    before to replaced
                }
                syncFromManager(m)
                if (replaced) {
                    offerUndo(m, undoSnapshot ?: before, label)
                } else {
                    val pending = undoSnapshot
                    // the message replaces a pending Undo snackbar without dropping its snapshot
                    val generation = ++undoGeneration
                    snackbarHostState.currentSnackbarData?.dismiss()
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.queue_source_nothing_found, label))
                        if (pending != null && generation == undoGeneration) offerUndo(m, pending, undoLabel)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't switch the queue source to $label", e)
                sourceError = QueueError(context.getString(errorMessage)) { switchSource(label, errorMessage, select) }
            } finally {
                refreshing = false
            }
        }
    }

    fun selectSource(choice: QueueManager.Source) {
        val m = manager ?: return
        if (refreshing) return
        when (choice) {
            QueueManager.Source.PLAYLIST -> showPlaylistPicker = true
            QueueManager.Source.MANUAL -> scope.launch {
                withContext(Dispatchers.IO) { m.setSource(choice) }
                syncFromManager(m)
            }
            else -> {
                val label = context.getString(
                    when (choice) {
                        QueueManager.Source.NEW -> R.string.queue_source_new
                        QueueManager.Source.DOWNLOADS -> R.string.queue_source_downloads
                        QueueManager.Source.UNPLAYED -> R.string.queue_source_discover
                        else -> R.string.queue_source_rediscover
                    }
                )
                switchSource(label, R.string.queue_error_refresh_source) { it.setSource(choice) }
            }
        }
    }

    fun playPlaylist(entry: PlaylistEntry) {
        if (manager == null || refreshing) return
        refreshing = true
        sourceError = null
        scope.launch {
            try {
                val playableTracks = withContext(Dispatchers.IO) {
                    val criteria = SearchCriteria(SearchCriteria.TYPE.PLAYLIST)
                    criteria.keyword = entry.name
                    val tagRepos = MusixMateApp.getInstance()?.tagRepository
                    val results = tagRepos?.findPlaylist(criteria) ?: emptyList()
                    results.filter { !it.isContainer && it.path != null && File(it.path).isFile }
                }
                if (playableTracks.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        manager.setPlayingQueue(playableTracks)
                    }
                    source = manager.source
                    state.updateTracks(manager.songs.toList())
                    onTrackClicked(playableTracks[0])
                } else {
                    sourceError = QueueError(context.getString(R.string.queue_error_no_playable, entry.name.orEmpty())) { playPlaylist(entry) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't load playlist ${entry.name}", e)
                sourceError = QueueError(context.getString(R.string.queue_error_load_playlist)) { playPlaylist(entry) }
            } finally {
                refreshing = false
            }
        }
    }

    fun enqueuePlaylist(entry: PlaylistEntry) {
        if (manager == null || refreshing) return
        refreshing = true
        sourceError = null
        scope.launch {
            try {
                val playableTracks = withContext(Dispatchers.IO) {
                    val criteria = SearchCriteria(SearchCriteria.TYPE.PLAYLIST)
                    criteria.keyword = entry.name
                    val tagRepos = MusixMateApp.getInstance()?.tagRepository
                    val results = tagRepos?.findPlaylist(criteria) ?: emptyList()
                    results.filter { !it.isContainer && it.path != null && File(it.path).isFile }
                }
                if (playableTracks.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        manager.enqueuePlayingQueue(playableTracks)
                    }
                    state.updateTracks(manager.songs.toList())
                } else {
                    sourceError = QueueError(context.getString(R.string.queue_error_no_playable, entry.name.orEmpty())) { enqueuePlaylist(entry) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't add playlist ${entry.name} to the queue", e)
                sourceError = QueueError(context.getString(R.string.queue_error_add_playlist)) { enqueuePlaylist(entry) }
            } finally {
                refreshing = false
            }
        }
    }

    fun setPlaylistSmartSource(entry: PlaylistEntry) {
        val name = entry.name ?: return
        switchSource(name, R.string.queue_error_fill_from_playlist) { it.setActivePlaylist(name) }
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
                Log.w(TAG, "Couldn't refresh the smart queue", e)
                sourceError = QueueError(context.getString(R.string.queue_error_find_tracks)) { triggerManualRefresh() }
            } finally {
                refreshing = false
            }
        }
    }

    // QueueManager has no insert-at-index: add the track back, then move it to its old slot
    // through the same path as a drag, so the host syncs the count, duration and playing key
    fun restoreTrack(m: QueueManager, track: Track, index: Int) {
        scope.launch {
            try {
                val songs = withContext(Dispatchers.IO) {
                    m.addPlayingQueue(track)
                    m.songs.toList()
                }
                val at = songs.indexOfFirst { it.id == track.id }
                if (at == -1) return@launch
                val target = index.coerceAtMost(songs.size - 1)
                state.updateTracks(songs)
                state.moveTrack(at, target)
                onMoveTrack(at, target)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't restore a removed track", e)
            }
        }
    }

    fun removeTrack(track: Track, index: Int) {
        onTrackRemoved(track, index)
        val m = manager ?: return
        val title = track.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.queue_unknown_title)
        scope.launch {
            // Only the latest removal can be undone: older indexes are stale by now
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.queue_removed_track, title),
                actionLabel = context.getString(R.string.queue_undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) restoreTrack(m, track, index)
        }
    }

    if (showPlaylistPicker) {
        PlaylistPickerDialog(
            activePlaylistName = activePlaylistName,
            onDismissRequest = { showPlaylistPicker = false },
            onPlayAll = { playPlaylist(it) },
            onEnqueue = { enqueuePlaylist(it) },
            onSetSmartSource = { setPlaylistSmartSource(it) }
        )
    }

    if (showMoodPicker) {
        MoodPickerDialog(
            activePlaylistName = activePlaylistName.takeIf { moodActive },
            onDismissRequest = { showMoodPicker = false },
            onMoodSelected = { setPlaylistSmartSource(it) }
        )
    }

    if (confirmClear && state.tracks.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.queue_clear_title)) },
            text = {
                Text(pluralStringResource(R.plurals.queue_clear_message, state.tracks.size, state.tracks.size))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClearQueue()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MusicMateDesignTokens.Status.Destructive)
                ) { Text(stringResource(R.string.queue_action_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 2.dp)
        ) {
            // Ultra-Compact Header Bar (Consolidated Toolbar + Status HUD)
            CompactQueueHeader(
                queueSize = state.tracks.size,
                durationText = state.totalDurationText,
                source = source,
                activePlaylistName = activePlaylistName,
                refreshing = refreshing,
                caughtUp = caughtUp,
                onJumpToPlaying = onJumpToPlaying,
                onClearQueue = { confirmClear = true },
                onOpenPlaylistPicker = { showPlaylistPicker = true },
                canClear = state.tracks.isNotEmpty()
            )

            // The list stays in list order while shuffled, so say what actually plays next
            val shuffled = MainScaffoldState.get().nowPlayingState.isShuffle.value
            val nextLabel = state.upNextLabel(shuffled)
            if (shuffled && nextLabel != null) {
                Text(
                    text = stringResource(R.string.queue_shuffled_up_next, nextLabel),
                    color = MusicMateDesignTokens.Text.Muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 27.dp, end = 14.dp, bottom = 2.dp)
                )
            }

            if (manager != null) {
                // Single-line source chips (48dp touch targets)
                CompactSourceDeck(
                    selectedSource = source,
                    activePlaylistName = activePlaylistName,
                    moodActive = moodActive,
                    refreshing = refreshing,
                    onRefreshClick = { triggerManualRefresh() },
                    onSourceSelected = { selectSource(it) },
                    onMoodClick = { if (!refreshing) showMoodPicker = true }
                )

                // Transient error; tapping it runs the failed action again
                sourceError?.let { error ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x22FF5252))
                            .border(0.75.dp, Color(0x66FF5252), RoundedCornerShape(8.dp))
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .clickable(role = Role.Button, onClick = error.retry)
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error.message,
                            color = Color(0xFFFF8A80),
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Gesture discovery hints for queue; the list's first item, so they scroll away
            val pendingHints = GestureHints.rememberPendingHints(
                listOf(GestureHints.HintType.SWIPE_QUEUE, GestureHints.HintType.DRAG_REORDER)
            )

            if (state.tracks.isEmpty()) {
                QueueEmptyState(
                    source = source,
                    refreshing = refreshing,
                    onBrowseLibrary = onBrowseLibrary,
                    onSelectSource = { selectSource(it) },
                    onOpenPlaylistPicker = { showPlaylistPicker = true }
                )
            } else {
                val currentIdx = manager?.currentIndex ?: -1
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
                ) {
                    if (showGestureHints && pendingHints.isNotEmpty()) {
                        item(key = "gesture-hints") {
                            GestureHints.GestureHintBanner(
                                pending = pendingHints,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }
                    }
                    itemsIndexed(
                        items = state.tracks,
                        // QueueManager keeps one entry per track id, so the key survives a move
                        key = { _, track -> track.id }
                    ) { index, track ->
                        val isPlaying = track.uniqueKey == state.currentPlayingKey ||
                                (currentIdx in state.tracks.indices && state.tracks[currentIdx].id == track.id)
                        val isSmart = manager?.isSmartSuggested(track.id) == true
                        // Only tracks the user put next; the rest of a played list is just the list
                        val isNext = !isPlaying && !isSmart && index > currentIdx &&
                                manager?.isPlayNext(track.id) == true

                        val prevTrack = if (index > 0) state.tracks[index - 1] else null
                        val sectionHeader = sectionHeader(
                            index = index,
                            track = track,
                            prevTrack = prevTrack,
                            currentIdx = currentIdx,
                            source = source,
                            moodActive = moodActive,
                            manager = manager
                        )

                        if (sectionHeader != null) {
                            QueueSectionHeader(
                                title = sectionHeader.first,
                                accentColor = sectionHeader.second
                            )
                        }

                        val latestTrack by rememberUpdatedState(track)
                        val latestIndex by rememberUpdatedState(index)
                        // The dismiss state keeps its first lambda, so read the current removeTrack
                        val latestRemove by rememberUpdatedState<(Track, Int) -> Unit> { t, i -> removeTrack(t, i) }
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = {
                                if (it == SwipeToDismissBoxValue.EndToStart || it == SwipeToDismissBoxValue.StartToEnd) {
                                    latestRemove(latestTrack, latestIndex)
                                    true
                                } else {
                                    false
                                }
                            }
                        )
                        // Undo brings back the same key, which would otherwise keep its dismissed state
                        LaunchedEffect(Unit) {
                            if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) dismissState.reset()
                        }

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
                                            contentDescription = stringResource(R.string.queue_remove_from_queue),
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
                                    smartAccent = sourceAccent(source, moodActive),
                                    onClick = { onTrackClicked(track) },
                                    onRemove = { removeTrack(track, index) },
                                    totalCount = state.tracks.size,
                                    artwork = trackArtwork,
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
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun CompactQueueHeader(
    queueSize: Int,
    durationText: String,
    source: QueueManager.Source,
    activePlaylistName: String?,
    refreshing: Boolean,
    caughtUp: Boolean,
    onJumpToPlaying: () -> Unit,
    onClearQueue: () -> Unit,
    onOpenPlaylistPicker: () -> Unit,
    canClear: Boolean
) {
    val showDuration = UiLayoutPolicy.showQueueDuration(LocalConfiguration.current.screenWidthDp)
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

    val ledColor = when {
        refreshing -> MusicMateDesignTokens.Brand.GoldAmber
        source == QueueManager.Source.MANUAL -> Color(0xFF78909C)
        else -> MusicMateDesignTokens.Status.Positive
    }
    val summary = if (refreshing) {
        stringResource(R.string.queue_finding_tracks)
    } else {
        val count = pluralStringResource(R.plurals.queue_track_count, queueSize, queueSize)
        if (showDuration && durationText.isNotEmpty()) "$count • $durationText" else count
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: LED Dot + Title + Count/Duration
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
            Text(
                text = stringResource(R.string.queue_title),
                color = Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp
            )
            Text(
                text = "• $summary",
                color = Color(0xFF9E9E9E),
                fontSize = 11.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }

        // Right: Action buttons (Playlist, Focus, Clear)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            QueueHeaderAction(R.drawable.ic_baseline_playlist_play_24, stringResource(R.string.queue_action_load_playlist), onOpenPlaylistPicker)
            QueueHeaderAction(R.drawable.ic_center_focus_strong_black_24dp, stringResource(R.string.queue_action_jump_to_playing), onJumpToPlaying)
            QueueHeaderAction(R.drawable.rounded_delete_24, stringResource(R.string.queue_action_clear), onClearQueue, enabled = canClear)
        }
    }
}

@Composable
private fun CompactSourceDeck(
    selectedSource: QueueManager.Source,
    activePlaylistName: String?,
    moodActive: Boolean,
    refreshing: Boolean,
    onRefreshClick: () -> Unit,
    onSourceSelected: (QueueManager.Source) -> Unit,
    onMoodClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val moodLabel = if (moodActive && !activePlaylistName.isNullOrEmpty()) activePlaylistName else stringResource(R.string.queue_source_mood)
    val playlistLabel = if (selectedSource == QueueManager.Source.PLAYLIST && !moodActive && !activePlaylistName.isNullOrEmpty()) {
        if (activePlaylistName.length > 10) activePlaylistName.take(10) + "…" else activePlaylistName
    } else {
        stringResource(R.string.queue_source_playlist)
    }
    val sources = listOf(
        SourceOption(QueueManager.Source.MANUAL, stringResource(R.string.queue_source_manual), R.drawable.ic_round_queue_music_24),
        SourceOption(QueueManager.Source.PLAYLIST, moodLabel, R.drawable.ic_graphic_eq_black_24dp, mood = true),
        SourceOption(QueueManager.Source.NEW, stringResource(R.string.queue_source_new), R.drawable.round_auto_awesome_24),
        SourceOption(QueueManager.Source.DOWNLOADS, stringResource(R.string.queue_source_downloads), R.drawable.ic_round_download_24),
        SourceOption(QueueManager.Source.UNPLAYED, stringResource(R.string.queue_source_discover), R.drawable.ic_round_explore_24),
        SourceOption(QueueManager.Source.REDISCOVER, stringResource(R.string.queue_source_rediscover), R.drawable.rounded_music_history_24),
        SourceOption(QueueManager.Source.PLAYLIST, playlistLabel, R.drawable.ic_baseline_playlist_play_24)
    )

    val idleFill = Color(0x12FFFFFF)
    val idleLabel = Color(0xBBFFFFFF)
    val idleIcon = Color(0x99FFFFFF)
    val idleBorder = Color(0x18FFFFFF)
    // Disabled chips (while refreshing) keep their colors at the usual 38% of their alpha
    fun Color.disabled() = copy(alpha = alpha * 0.38f)

    // Seven sources never fit a phone: fade the edges that hide more chips.
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fadingEdge(
                startWidth = if (scrollState.canScrollBackward) 40.dp else 0.dp,
                endWidth = if (scrollState.canScrollForward) 40.dp else 0.dp
            )
            .horizontalScroll(scrollState)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        sources.forEach { opt ->
            val isSelected = opt.source == selectedSource && opt.mood == moodActive
            val isSelectedSmartSource = isSelected && opt.source != QueueManager.Source.MANUAL
            val accent = sourceAccent(opt.source, opt.mood)
            // Keep the selected source (and its refresh button) on screen.
            val bringIntoView = remember { BringIntoViewRequester() }
            if (isSelected) {
                LaunchedEffect(selectedSource, moodActive) { bringIntoView.bringIntoView() }
            }
            FilterChip(
                selected = isSelected,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (opt.mood) onMoodClick() else onSourceSelected(opt.source)
                },
                enabled = !refreshing,
                label = {
                    Text(
                        text = opt.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = opt.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                },
                modifier = Modifier.bringIntoViewRequester(bringIntoView),
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = idleFill,
                    labelColor = idleLabel,
                    iconColor = idleIcon,
                    selectedContainerColor = accent.copy(alpha = 0.15f),
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = accent,
                    disabledContainerColor = idleFill.disabled(),
                    disabledLabelColor = idleLabel.disabled(),
                    disabledLeadingIconColor = idleIcon.disabled(),
                    disabledSelectedContainerColor = accent.copy(alpha = 0.15f).disabled()
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = !refreshing,
                    selected = isSelected,
                    borderColor = idleBorder,
                    selectedBorderColor = accent.copy(alpha = 0.8f),
                    disabledBorderColor = idleBorder.disabled(),
                    disabledSelectedBorderColor = accent.copy(alpha = 0.8f).disabled(),
                    borderWidth = 0.5.dp,
                    selectedBorderWidth = 1.dp
                )
            )
            if (isSelectedSmartSource) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRefreshClick()
                    },
                    enabled = !refreshing,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_refresh_24),
                        contentDescription = stringResource(R.string.queue_refresh_suggestions),
                        tint = if (refreshing) MusicMateDesignTokens.Brand.GoldAmber else Color(0xFFB0BEC5),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun sectionHeader(
    index: Int,
    track: Track,
    prevTrack: Track?,
    currentIdx: Int,
    source: QueueManager.Source,
    moodActive: Boolean,
    manager: QueueManager?
): Pair<String, Color>? {
    val isSmart = manager?.isSmartSuggested(track.id) == true
    val prevSmart = prevTrack != null && manager?.isSmartSuggested(prevTrack.id) == true

    if (index == currentIdx || (currentIdx == -1 && index == 0)) {
        return stringResource(R.string.queue_section_now_playing) to MusicMateDesignTokens.Brand.Gold
    }
    if (index == currentIdx + 1 && !isSmart) {
        // "Added by you" only means something ahead of tracks a source added
        val title = stringResource(
            if (source == QueueManager.Source.MANUAL) R.string.queue_section_up_next else R.string.queue_section_up_next_yours
        )
        return title to MusicMateDesignTokens.SourceAccent.UserAdded
    }
    if (isSmart && (index == currentIdx + 1 || !prevSmart)) {
        val title = when (source) {
            QueueManager.Source.MANUAL -> stringResource(R.string.queue_section_added_automatically)
            QueueManager.Source.PLAYLIST -> stringResource(
                R.string.queue_section_added_from,
                manager.activePlaylistName?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.queue_source_playlist)
            )
            else -> stringResource(R.string.queue_section_added_from, stringResource(sourceLabel(source)))
        }
        return title to sourceAccent(source, moodActive)
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
            text = title.uppercase(),
            color = accentColor,
            fontSize = 10.sp,
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
    smartAccent: Color = MusicMateDesignTokens.SourceAccent.Discover,
    onClick: () -> Unit,
    onRemove: () -> Unit = {},
    totalCount: Int = 0,
    artwork: (@Composable (Track) -> Unit)? = null,
    onMoveTrack: ((Int, Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val artist = track.artist?.takeIf { it.isNotEmpty() } ?: track.album ?: stringResource(R.string.queue_unknown_artist)
    val durationStr = if (track.audioDuration > 0) StringUtils.formatDuration(track.audioDuration, false) else ""
    val trackTitle = track.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.queue_unknown_title)
    val removeLabel = stringResource(R.string.action_remove_queue_track, trackTitle)
    val moveUpLabel = stringResource(R.string.action_move_queue_track_up, trackTitle)
    val moveDownLabel = stringResource(R.string.action_move_queue_track_down, trackTitle)

    val rowBg = if (isPlaying) MusicMateDesignTokens.Brand.GoldContainer else Color.Transparent
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
            .semantics {
                customActions = buildList {
                    add(CustomAccessibilityAction(removeLabel) {
                        onRemove()
                        true
                    })
                    if (index > 0 && onMoveTrack != null) {
                        add(CustomAccessibilityAction(moveUpLabel) {
                            onMoveTrack(index, index - 1)
                            true
                        })
                    }
                    if (index < totalCount - 1 && onMoveTrack != null) {
                        add(CustomAccessibilityAction(moveDownLabel) {
                            onMoveTrack(index, index + 1)
                            true
                        })
                    }
                }
            }
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
                color = if (isPlaying) MusicMateDesignTokens.Brand.Gold else MusicMateDesignTokens.Text.Muted,
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
                    color = if (isPlaying) MusicMateDesignTokens.Brand.Gold else Color(0x22FFFFFF),
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (artwork != null) {
                artwork(track)
            } else {
                AsyncImage(
                    model = CoverartFetcher.builder(context, track).data(track).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x55000000)),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedEqualizerBars(
                        modifier = Modifier.size(width = 18.dp, height = 15.dp)
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
                text = trackTitle,
                color = if (isPlaying) MusicMateDesignTokens.Brand.Gold else Color.White,
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
                    ProvenanceBadge(stringResource(R.string.queue_section_now_playing), MusicMateDesignTokens.Brand.Gold)
                } else if (isNext) {
                    ProvenanceBadge(stringResource(R.string.queue_badge_play_next), MusicMateDesignTokens.SourceAccent.UserAdded)
                } else if (isSmart) {
                    ProvenanceBadge(stringResource(R.string.queue_badge_smart), smartAccent)
                }
            }
        }

        // Duration
        if (durationStr.isNotEmpty()) {
            Text(
                text = durationStr,
                color = MusicMateDesignTokens.Text.Muted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(end = 4.dp)
            )
        }

        // Vertical drag reorder: the gesture outlives each move, so it reads the latest values
        val latestIndex by rememberUpdatedState(index)
        val latestCount by rememberUpdatedState(totalCount)
        val latestOnMove by rememberUpdatedState(onMoveTrack)
        val threshold = with(LocalDensity.current) { 52.dp.toPx() }
        Icon(
            painter = painterResource(id = R.drawable.rounded_drag_indicator_24),
            contentDescription = stringResource(R.string.queue_reorder),
            tint = Color(0xFF757575),
            modifier = Modifier
                .size(48.dp)
                .padding(12.dp)
                .pointerInput(track.id, threshold) {
                    var dragAccumulatedY = 0f
                    // Where the row is now; latestIndex lags a frame behind each move
                    var position = 0
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragAccumulatedY = 0f
                            position = latestIndex
                        },
                        onDragEnd = { dragAccumulatedY = 0f },
                        onDragCancel = { dragAccumulatedY = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            dragAccumulatedY += dragAmount
                            if (dragAccumulatedY > threshold && position < latestCount - 1) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                latestOnMove?.invoke(position, position + 1)
                                position++
                                dragAccumulatedY -= threshold
                            } else if (dragAccumulatedY < -threshold && position > 0) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                latestOnMove?.invoke(position, position - 1)
                                position--
                                dragAccumulatedY += threshold
                            }
                        }
                    )
                }
        )
    }
}

@Composable
private fun ProvenanceBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.14f))
            .border(0.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = label.uppercase(),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.2.sp
        )
    }
}

@Composable
private fun QueueEmptyState(
    source: QueueManager.Source,
    refreshing: Boolean,
    onBrowseLibrary: () -> Unit,
    onSelectSource: (QueueManager.Source) -> Unit,
    onOpenPlaylistPicker: () -> Unit
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
                tint = MusicMateDesignTokens.Brand.Gold,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(
                when {
                    source == QueueManager.Source.MANUAL -> R.string.queue_empty_title_manual
                    refreshing -> R.string.queue_empty_title_finding
                    else -> R.string.queue_empty_title_nothing
                }
            ),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(
                when (source) {
                    QueueManager.Source.MANUAL -> R.string.queue_empty_body_manual
                    QueueManager.Source.REDISCOVER -> R.string.queue_empty_body_rediscover
                    else -> R.string.queue_empty_body_smart
                }
            ),
            color = Color(0xFF9E9E9E),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
        Spacer(modifier = Modifier.height(20.dp))

        QueueEmptyActions(
            source = source,
            onBrowseLibrary = onBrowseLibrary,
            onSelectDiscoveries = { onSelectSource(QueueManager.Source.UNPLAYED) },
            onOpenPlaylistPicker = onOpenPlaylistPicker
        )
    }
}

@Composable
private fun QueueEmptyActions(
    source: QueueManager.Source,
    onBrowseLibrary: () -> Unit,
    onSelectDiscoveries: () -> Unit,
    onOpenPlaylistPicker: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    val stackActions = UiLayoutPolicy.stackChoiceControls(
        windowWidthDp = configuration.screenWidthDp,
        fontScale = fontScale
    )

    if (stackActions) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QueueEmptyActionButton(
                label = stringResource(R.string.queue_source_playlist),
                onClick = onOpenPlaylistPicker,
                containerColor = Color.Transparent,
                contentColor = MusicMateDesignTokens.Text.Primary,
                borderColor = MusicMateDesignTokens.Surfaces.BorderElevated,
                modifier = Modifier.fillMaxWidth()
            )
            if (source == QueueManager.Source.MANUAL) {
                QueueEmptyActionButton(
                    label = stringResource(R.string.queue_source_discover),
                    onClick = onSelectDiscoveries,
                    containerColor = Color.Transparent,
                    contentColor = MusicMateDesignTokens.Text.Primary,
                    borderColor = MusicMateDesignTokens.Surfaces.BorderElevated,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            QueueEmptyActionButton(
                label = stringResource(R.string.queue_browse_library),
                onClick = onBrowseLibrary,
                containerColor = MusicMateDesignTokens.Brand.Gold,
                contentColor = Color.Black,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QueueEmptyActionButton(
                    label = stringResource(R.string.queue_source_playlist),
                    onClick = onOpenPlaylistPicker,
                    containerColor = Color.Transparent,
                    contentColor = MusicMateDesignTokens.Text.Primary,
                    borderColor = MusicMateDesignTokens.Surfaces.BorderElevated,
                    modifier = Modifier.weight(1f)
                )
                if (source == QueueManager.Source.MANUAL) {
                    QueueEmptyActionButton(
                        label = stringResource(R.string.queue_source_discover),
                        onClick = onSelectDiscoveries,
                        containerColor = Color.Transparent,
                        contentColor = MusicMateDesignTokens.Text.Primary,
                        borderColor = MusicMateDesignTokens.Surfaces.BorderElevated,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            QueueEmptyActionButton(
                label = stringResource(R.string.queue_browse_library),
                onClick = onBrowseLibrary,
                containerColor = MusicMateDesignTokens.Brand.Gold,
                contentColor = Color.Black,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun QueueEmptyActionButton(
    label: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    borderColor: Color? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(12.dp),
        border = borderColor?.let { BorderStroke(1.dp, it) }
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

/** Icon-only queue action: one neutral tint (no lone purple) and its label as a long-press tooltip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueHeaderAction(iconRes: Int, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = if (enabled) Color(0xFFCFD8DC) else Color(0xFF555555),
                modifier = Modifier.size(19.dp)
            )
        }
    }
}
