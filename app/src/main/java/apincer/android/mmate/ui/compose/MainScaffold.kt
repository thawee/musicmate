package apincer.android.mmate.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import apincer.android.mmate.R
import apincer.android.mmate.coil3.CoverartFetcher
import apincer.music.core.model.Track
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

// ── App-wide dark colour palette ────────────────────────────────────────────
private val drawerBg        = Color(0xFF121212)
private val drawerSurface   = Color(0xFF1E1E1E)
private val drawerGold      = Color(0xFFFFD700)
private val drawerWhite     = Color.White
private val drawerGray      = Color(0xFFAAAAAA)
private val drawerDivider   = Color(0xFF2C2C2C)
private val drawerSelected  = Color(0xFF2A2A2A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    drawerState: DrawerState,
    callbacks: MainScaffoldCallbacks? = null,
    state: MainScaffoldState = MainScaffoldState.get(),
    activeItemId: Int = R.id.menu_library_all_songs
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        drawerContent = {
            val haptic = LocalHapticFeedback.current
            // ── Dark-themed audiophile drawer sheet ────────────────────────────
            Box(
                modifier = Modifier
                    .width(310.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF1E1C2B), Color(0xFF131317), Color(0xFF0E0E12))
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 36.dp)
                ) {
                    // ── 1. HEADER CAPSULE ─────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 44.dp, bottom = 14.dp, start = 18.dp, end = 18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_nav_musicmate_menu),
                                contentDescription = null,
                                tint = drawerGold,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "MusicMate",
                                    color = drawerWhite,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.4).sp
                                )
                                Text(
                                    text = "v3.19.8 • Hi-Res Edition",
                                    color = drawerGold.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (state.headerStatsText.value.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = Color(0x14FFFFFF),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(0.75.dp, Color(0x1FFFFFFF)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.rounded_equalizer_24),
                                        contentDescription = null,
                                        tint = drawerGold,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = state.headerStatsText.value,
                                        color = Color(0xFFDDDDDD),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0x1AFFFFFF), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // ── 2. CORE LIBRARY (2×2 Quick-Action Grid) ───────────────
                    DrawerSectionHeader("Core Library")
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DrawerTile(
                                text = "All Songs",
                                iconResId = R.drawable.rounded_library_music_24,
                                isSelected = (activeItemId == R.id.menu_library_all_songs),
                                modifier = Modifier.weight(1f)
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                callbacks?.onNavigationItemClick(R.id.menu_library_all_songs)
                                coroutineScope.launch { drawerState.close() }
                            }
                            DrawerTile(
                                text = "Artists",
                                iconResId = R.drawable.rounded_for_you_24,
                                isSelected = (activeItemId == R.id.menu_tag_artist),
                                modifier = Modifier.weight(1f)
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                callbacks?.onNavigationItemClick(R.id.menu_tag_artist)
                                coroutineScope.launch { drawerState.close() }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DrawerTile(
                                text = "Genres",
                                iconResId = R.drawable.rounded_style_24,
                                isSelected = (activeItemId == R.id.menu_tag_genre),
                                modifier = Modifier.weight(1f)
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                callbacks?.onNavigationItemClick(R.id.menu_tag_genre)
                                coroutineScope.launch { drawerState.close() }
                            }
                            DrawerTile(
                                text = "Playlists",
                                iconResId = R.drawable.rounded_order_play_24,
                                isSelected = (activeItemId == R.id.menu_collection),
                                modifier = Modifier.weight(1f)
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                callbacks?.onNavigationItemClick(R.id.menu_collection)
                                coroutineScope.launch { drawerState.close() }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ── 3. DISCOVERY & AUDIOPHILE TOOLS ───────────────────────
                    DrawerSectionHeader("Discover & Audiophile")
                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = Color(0x12FFFFFF),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(0.75.dp, Color(0x1AFFFFFF)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column {
                            DrawerCardItem(
                                text = "Sound Grade",
                                iconResId = R.drawable.rounded_equalizer_24,
                                isSelected = (activeItemId == R.id.menu_sound_grade),
                                badge = "Hi-Res / DR",
                                badgeColor = drawerGold
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                callbacks?.onNavigationItemClick(R.id.menu_sound_grade)
                                coroutineScope.launch { drawerState.close() }
                            }
                            HorizontalDivider(color = Color(0x0FFFFFFF), thickness = 0.5.dp)
                            DrawerCardItem(
                                text = "Discover Similar",
                                iconResId = R.drawable.rounded_auto_awesome_motion_24,
                                isSelected = (activeItemId == R.id.menu_library_similar_songs)
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                callbacks?.onNavigationItemClick(R.id.menu_library_similar_songs)
                                coroutineScope.launch { drawerState.close() }
                            }
                            HorizontalDivider(color = Color(0x0FFFFFFF), thickness = 0.5.dp)
                            DrawerCardItem(
                                text = "Incoming Tracks",
                                iconResId = R.drawable.rounded_add_diamond_24,
                                isSelected = (activeItemId == R.id.menu_library_recently_added),
                                badge = "New",
                                badgeColor = Color(0xFF00E5FF)
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                callbacks?.onNavigationItemClick(R.id.menu_library_recently_added)
                                coroutineScope.launch { drawerState.close() }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ── 4. SYSTEM & PREFERENCES ───────────────────────────────
                    DrawerSectionHeader("Settings & System")
                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = Color(0x12FFFFFF),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(0.75.dp, Color(0x1AFFFFFF)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column {
                            DrawerCardItem(
                                text = "Manage Library",
                                iconResId = R.drawable.rounded_folder_managed_24,
                                isSelected = (activeItemId == R.id.menu_directories),
                                showChevron = true
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                callbacks?.onNavigationItemClick(R.id.menu_directories)
                                coroutineScope.launch { drawerState.close() }
                            }
                            HorizontalDivider(color = Color(0x0FFFFFFF), thickness = 0.5.dp)
                            DrawerCardItem(
                                text = "Settings",
                                iconResId = R.drawable.ic_round_settings_24,
                                isSelected = (activeItemId == R.id.menu_settings),
                                showChevron = true
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                callbacks?.onNavigationItemClick(R.id.menu_settings)
                                coroutineScope.launch { drawerState.close() }
                            }
                            HorizontalDivider(color = Color(0x0FFFFFFF), thickness = 0.5.dp)
                            DrawerCardItem(
                                text = "Storage Access",
                                iconResId = R.drawable.round_sd_storage_24,
                                isSelected = (activeItemId == R.id.menu_files_permission),
                                showChevron = true
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                callbacks?.onNavigationItemClick(R.id.menu_files_permission)
                                coroutineScope.launch { drawerState.close() }
                            }
                            HorizontalDivider(color = Color(0x0FFFFFFF), thickness = 0.5.dp)
                            DrawerCardItem(
                                text = "Notifications",
                                iconResId = R.drawable.ic_round_notification_add_24,
                                isSelected = (activeItemId == R.id.menu_notification_access),
                                showChevron = true
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                callbacks?.onNavigationItemClick(R.id.menu_notification_access)
                                coroutineScope.launch { drawerState.close() }
                            }
                            HorizontalDivider(color = Color(0x0FFFFFFF), thickness = 0.5.dp)
                            DrawerCardItem(
                                text = "Diagnostics",
                                iconResId = R.drawable.rounded_bug_report_24,
                                isSelected = (activeItemId == R.id.menu_about_crash),
                                showChevron = true
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                callbacks?.onNavigationItemClick(R.id.menu_about_crash)
                                coroutineScope.launch { drawerState.close() }
                            }
                            HorizontalDivider(color = Color(0x0FFFFFFF), thickness = 0.5.dp)
                            DrawerCardItem(
                                text = "About MusicMate",
                                iconResId = R.drawable.rounded_info_24,
                                isSelected = (activeItemId == R.id.menu_about_music_mate),
                                showChevron = true
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                callbacks?.onNavigationItemClick(R.id.menu_about_music_mate)
                                coroutineScope.launch { drawerState.close() }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F0F))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Top Header Search & Stats Bar (DESIGN.md §6) ─────────────
                val targetTitle = state.outputTargetSubtitle.value
                val isDlnaCast = targetTitle.contains("DLNA", ignoreCase = true) || targetTitle.contains("Renderer", ignoreCase = true) || targetTitle.contains("Streamer", ignoreCase = true)
                TopSearchBar(
                    query = state.searchQuery.value,
                    onQueryChange = { q ->
                        state.searchQuery.value = q
                        callbacks?.onSearchQueryChange(q)
                    },
                    isBackVisible = state.isBackVisible.value || state.searchQuery.value.isNotEmpty(),
                    onBackClick = {
                        state.searchQuery.value = ""
                        focusManager.clearFocus()
                        callbacks?.onSearchBackClick()
                    },
                    statsText = state.headerStatsText.value,
                    isPlaylistOverview = state.isPlaylistOverview.value,
                    isScanning = state.isScanning.value,
                    scanProgressText = state.scanProgressText.value,
                    isCastActive = isDlnaCast,
                    onCastClick = { callbacks?.onSelectPlaybackTargetClick() },
                    onAddPlaylistClick = { state.showCreateSmartPlaylistDialog.value = true }
                )

                // ── Main Song List ───────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    MusicListScreen(
                        tracks = state.tracks,
                        selectedTracks = state.selectedTracks.toSet(),
                        nowPlayingTrack = state.nowPlayingTrack.value,
                        isPlaying = state.isPlaying.value,
                        isRefreshing = state.isRefreshing.value,
                        scrollToIndex = state.scrollToIndex.intValue,
                        onScrollComplete = { state.scrollToIndex.intValue = -1 },
                        onRefresh = { callbacks?.onListRefresh() },
                        hasMoreItems = state.hasMoreMusic.value,
                        loadError = state.musicLoadError.value,
                        onLoadMore = { callbacks?.onLoadMoreMusic() },
                        onTrackClick = { track, index ->
                            callbacks?.onTrackClick(track, index)
                        },
                        onTrackLongClick = { track, index ->
                            callbacks?.onTrackLongClick(track, index)
                        },
                        onTrackMenuClick = { track, index ->
                            callbacks?.onTrackMenuClick(track, index)
                        },
                        onTrackQuickPlayClick = { track ->
                            callbacks?.onTrackQuickPlayClick(track)
                        },
                        onFolderPlayClick = { track ->
                            callbacks?.onFolderPlayClick(track)
                        },
                        onFolderEnqueueClick = { track ->
                            callbacks?.onFolderEnqueueClick(track)
                        }
                    )
                }
            }

            // ── Floating Mini-Player Dock (DESIGN.md §6A: 20dp radius, 12dp horizontal / 8dp bottom margins)
            AnimatedVisibility(
                visible = state.isFloatingDockVisible.value,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                FloatingMiniPlayerDock(
                    track = state.nowPlayingTrack.value,
                    isPlaying = state.isPlaying.value,
                    outputTarget = state.outputTargetSubtitle.value,
                    progress = state.playbackProgress.floatValue,
                    onPlayPauseClick = { callbacks?.onDockPlayPauseClick() },
                    onNextClick = { callbacks?.onDockNextClick() },
                    onPreviousClick = { callbacks?.onAudioHubPrevious() },
                    onOpenAudioHub = {
                        state.showAudioHubSheet.value = true
                    },
                    onOpenDrawer = {
                        coroutineScope.launch { drawerState.open() }
                    },
                    onScrollToPlaying = {
                        callbacks?.onDockLongClick()
                    }
                )
            }
        }
    }

    // ── Pure Compose Audio Hub Modal Bottom Sheet (DESIGN.md §8C: Fixed 65% Height)
    if (state.showAudioHubSheet.value) {
        AudioHubSheet(
            nowPlayingState = state.nowPlayingState,
            queueState = state.queueState,
            mediaServerState = state.mediaServerState,
            initialTab = state.audioHubInitialTab.intValue,
            onDismissRequest = { state.showAudioHubSheet.value = false },
            onSelectTargetPlayer = { callbacks?.onSelectPlaybackTargetClick() },
            onPlayPause = { callbacks?.onAudioHubPlayPause() },
            onNext = { callbacks?.onAudioHubNext() },
            onPrevious = { callbacks?.onAudioHubPrevious() },
            onShuffleToggle = { callbacks?.onAudioHubShuffleToggle() },
            onRepeatToggle = { callbacks?.onAudioHubRepeatToggle() },
            onSeek = { pos -> callbacks?.onAudioHubSeek(pos) },
            onVolumeDown = { callbacks?.onAudioHubVolumeDown() },
            onVolumeUp = { callbacks?.onAudioHubVolumeUp() },
            onVolumeChanged = { vol -> callbacks?.onAudioHubVolumeChanged(vol) },
            onSleepTimerSelected = { minutes, endOfTrack -> callbacks?.onAudioHubSleepTimerSelected(minutes, endOfTrack) },
            onTrackClicked = { callbacks?.onAudioHubTrackClick() },
            onQueueTrackClicked = { track -> callbacks?.onAudioHubQueueTrackClick(track) },
            onQueueTrackRemoved = { track, index -> callbacks?.onAudioHubQueueTrackRemove(track, index) },
            onQueueTrackMoved = { from, to -> callbacks?.onAudioHubQueueTrackMoved(from, to) },
            onQueueClear = { callbacks?.onAudioHubQueueClear() },
            onQueueJumpToPlaying = { callbacks?.onAudioHubQueueJumpToPlaying() },
            onQueueBrowseLibrary = {
                state.showAudioHubSheet.value = false
                callbacks?.onNavigationItemClick(R.id.menu_library_all_songs)
            },
            onEngineChanged = { engine -> callbacks?.onEngineChanged(engine) },
            onStartServerClicked = { callbacks?.onStartServerClicked() },
            onStopServerClicked = { callbacks?.onStopServerClicked() },
            onCopyUrlClicked = { callbacks?.onCopyUrlClicked() },
            onOpenUrlClicked = { callbacks?.onOpenUrlClicked() },
            onQrCodeClicked = { callbacks?.onQrCodeClicked() },
            onOpenFullscreen = {
                state.showFullscreenConsole.value = true
            }
        )
    }

    // ── Pure Compose Fullscreen Landscape Studio Console ("Hi-Fi Desk Mode") ─
    if (state.showFullscreenConsole.value) {
        FullscreenStudioConsole(
            state = state.nowPlayingState,
            queueState = state.queueState,
            onDismissRequest = { state.showFullscreenConsole.value = false },
            onPlayPause = { callbacks?.onAudioHubPlayPause() },
            onNext = { callbacks?.onAudioHubNext() },
            onPrevious = { callbacks?.onAudioHubPrevious() },
            onShuffleToggle = { callbacks?.onAudioHubShuffleToggle() },
            onRepeatToggle = { callbacks?.onAudioHubRepeatToggle() },
            onSeek = { pos -> callbacks?.onAudioHubSeek(pos) },
            onVolumeChanged = { vol -> callbacks?.onAudioHubVolumeChanged(vol) },
            onSelectTargetPlayer = { callbacks?.onSelectPlaybackTargetClick() },
            onQueueTrackClicked = { track -> callbacks?.onAudioHubQueueTrackClick(track) }
        )
    }

    // ── Pure Compose Player Picker Modal Dialog (DESIGN.md §4 & §8A) ─────────
    if (state.showPlayerPickerDialog.value) {
        PlayerPickerDialog(
            targets = state.playerTargets,
            isScanning = state.isPlayerScanning.value,
            onDismissRequest = { state.showPlayerPickerDialog.value = false },
            onTargetSelected = { target ->
                callbacks?.onPlayerTargetSelected(target)
                state.showPlayerPickerDialog.value = false
            },
            onRescanClick = { callbacks?.onRescanTargets() },
            onBluetoothOutputClick = {
                callbacks?.onOpenSystemAudioOutput()
                state.showPlayerPickerDialog.value = false
            }
        )
    }

    // ── Pure Compose Create Smart Playlist Modal Dialog ───────────────────────
    if (state.showCreateSmartPlaylistDialog.value) {
        val context = LocalContext.current
        CreateSmartPlaylistDialog(
            availableTracks = state.tracks,
            onDismiss = { state.showCreateSmartPlaylistDialog.value = false },
            onSave = { newEntry ->
                apincer.music.core.repository.PlaylistRepository.saveCustomPlaylist(context, newEntry)
                state.showCreateSmartPlaylistDialog.value = false
                callbacks?.onSmartPlaylistCreated(newEntry)
            }
        )
    }
}

// ── Top Search & Stats Bar (DESIGN.md §6 & §4) ──────────────────────────────
@Composable
private fun TopSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isBackVisible: Boolean,
    onBackClick: () -> Unit,
    statsText: String,
    isPlaylistOverview: Boolean,
    isScanning: Boolean,
    scanProgressText: String,
    isCastActive: Boolean = false,
    onCastClick: () -> Unit = {},
    onAddPlaylistClick: () -> Unit = {}
) {
    Surface(
        color = Color(0xEB161616),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isBackVisible) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Frosted Search Pill
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF242424))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_search_24dp),
                            contentDescription = null,
                            tint = Color(0x99FFFFFF),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search songs, artists…",
                                    color = Color(0x77FFFFFF),
                                    fontSize = 13.sp
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = onQueryChange,
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(drawerGold),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.round_close_24),
                                    contentDescription = "Clear",
                                    tint = Color(0x99FFFFFF),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // New Smart Playlist action when browsing playlists
                if (isPlaylistOverview) {
                    IconButton(
                        onClick = onAddPlaylistClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_playlist_add_24),
                            contentDescription = "New Smart Playlist",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Cast button on header right (§6 & §4)
                IconButton(
                    onClick = onCastClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_music_cast_24),
                        contentDescription = "Output Device Picker",
                        tint = if (isCastActive) Color(0xFFFFC107) else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Stats Subtitle & Scanning Indicator Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (statsText.isNotEmpty()) {
                    Text(
                        text = statsText,
                        color = Color(0x99FFFFFF),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Normal
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (isScanning) {
                    ScanningIndicator(scanProgressText)
                }
            }
        }
    }
}

// ── Pulsing Scanning Indicator ───────────────────────────────────────────────
@Composable
private fun ScanningIndicator(progressText: String) {
    val transition = rememberInfiniteTransition(label = "scan_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_alpha"
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(drawerGold)
                .alpha(alpha)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = progressText.ifEmpty { "Scanning…" },
            color = drawerGold,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ── Floating Mini-Player Dock (DESIGN.md §6A) ─────────────────────────────────
@Composable
private fun FloatingMiniPlayerDock(
    track: Track?,
    isPlaying: Boolean,
    outputTarget: String,
    progress: Float,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPreviousClick: () -> Unit = {},
    onOpenAudioHub: () -> Unit,
    onOpenDrawer: () -> Unit,
    onScrollToPlaying: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val currentScrollToPlaying by rememberUpdatedState(onScrollToPlaying)
    val currentOpenAudioHub by rememberUpdatedState(onOpenAudioHub)

    Surface(
        shape = RoundedCornerShape(20.dp), // DESIGN.md §6A: 20dp corner radius
        color = Color(0xEB1E1E1E),
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .border(1.2.dp, Color(0x26FFFFFF), RoundedCornerShape(20.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Far Left: Album Art thumbnail (Click opens Audio Hub, Long-press jumps to playing song in list)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2A2A2A))
                        .semantics {
                            role = Role.Button
                            onClick("Open Music Center") { currentOpenAudioHub(); true }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { currentOpenAudioHub() },
                                onLongPress = {
                                    if (track != null) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        currentScrollToPlaying()
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (track != null) {
                        AsyncImage(
                            model = CoverartFetcher.builder(context, track).data(track).build(),
                            contentDescription = "Now Playing Artwork",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_now_playing_idle),
                            contentDescription = null,
                            tint = Color(0x66FFFFFF),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Center: Track Title & Subtitle with Swipe Gestures & Long-Press Jump
                var dragTotalX by remember { mutableFloatStateOf(0f) }
                var dragTotalY by remember { mutableFloatStateOf(0f) }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            role = Role.Button
                            onClick("Open Music Center") { currentOpenAudioHub(); true }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { currentOpenAudioHub() },
                                onLongPress = {
                                    if (track != null) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        currentScrollToPlaying()
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    dragTotalX = 0f
                                    dragTotalY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragTotalX += dragAmount.x
                                    dragTotalY += dragAmount.y
                                },
                                onDragEnd = {
                                    val threshold = 36.dp.toPx()
                                    if (dragTotalY < -threshold && kotlin.math.abs(dragTotalY) > kotlin.math.abs(dragTotalX)) {
                                        onOpenAudioHub()
                                    } else if (dragTotalX < -threshold) {
                                        onNextClick()
                                    } else if (dragTotalX > threshold) {
                                        onPreviousClick()
                                    }
                                    dragTotalX = 0f
                                    dragTotalY = 0f
                                },
                                onDragCancel = {
                                    dragTotalX = 0f
                                    dragTotalY = 0f
                                }
                            )
                        }
                ) {
                    Text(
                        text = track?.title ?: "MusicMate",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(iterations = Int.MAX_VALUE, velocity = 28.dp)
                            .fadingEdge(startWidth = 8.dp, endWidth = 10.dp)
                    )

                    val cleanTarget = remember(outputTarget) { sanitizeTargetDeviceTitle(outputTarget) }
                    val isLocal = cleanTarget.equals("Local Audio", ignoreCase = true) || cleanTarget.isEmpty()
                    val artistName = track?.artist?.takeIf { it.isNotBlank() }

                    val subtitleText = remember(artistName, cleanTarget, isLocal) {
                        buildAnnotatedString {
                            if (artistName != null) {
                                append(artistName)
                            }
                            if (!isLocal) {
                                if (artistName != null) {
                                    append(" • ")
                                }
                                withStyle(
                                    SpanStyle(
                                        color = drawerGold.copy(alpha = 0.95f),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                ) {
                                    append(cleanTarget)
                                }
                            } else if (artistName == null) {
                                append("High-Fidelity Audio")
                            }
                        }
                    }

                    Text(
                        text = subtitleText,
                        color = Color(0x99FFFFFF),
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(iterations = Int.MAX_VALUE, velocity = 24.dp)
                            .fadingEdge(startWidth = 8.dp, endWidth = 10.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Transport Controls: Circular Tactile Play / Pause with Gold Accent Rim
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                        .border(1.dp, drawerGold.copy(alpha = 0.5f), CircleShape)
                        .clickable(onClick = onPlayPauseClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isPlaying) R.drawable.ic_baseline_pause_24 else R.drawable.ic_baseline_play_arrow_24
                        ),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onNextClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_skip_next_24),
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Far Right: MusicMate Drawer Menu Button (DESIGN.md §6A: 48dp target for thumb ergonomics)
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_nav_musicmate_menu),
                        contentDescription = "MusicMate Menu",
                        tint = drawerGold,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Hairline Progress Bar along the bottom
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(2.dp)
                        .align(Alignment.BottomStart)
                        .background(drawerGold)
                )
            }
        }
    }
}

@Composable
private fun DrawerSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = drawerGold,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun DrawerTile(
    text: String,
    iconResId: Int,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) Color(0x2BFFD700) else Color(0x12FFFFFF)
    val borderColor = if (isSelected) drawerGold.copy(alpha = 0.65f) else Color(0x1AFFFFFF)
    val contentColor = if (isSelected) drawerGold else drawerWhite
    val iconColor = if (isSelected) drawerGold else Color(0xFFBDBDBD)

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(0.75.dp, borderColor),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DrawerCardItem(
    text: String,
    iconResId: Int,
    isSelected: Boolean = false,
    badge: String? = null,
    badgeColor: Color = Color.Unspecified,
    showChevron: Boolean = false,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) Color(0x22FFD700) else Color.Transparent
    val contentColor = if (isSelected) drawerGold else drawerWhite
    val iconColor = if (isSelected) drawerGold else Color(0xFFBDBDBD)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = contentColor,
            fontSize = 13.5.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )

        if (badge != null) {
            Surface(
                color = badgeColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, badgeColor.copy(alpha = 0.4f)),
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(
                    text = badge,
                    color = badgeColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        if (showChevron) {
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = Color(0x4DFFFFFF),
                modifier = Modifier.size(16.dp)
            )
        } else if (isSelected) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(drawerGold)
            )
        }
    }
}
