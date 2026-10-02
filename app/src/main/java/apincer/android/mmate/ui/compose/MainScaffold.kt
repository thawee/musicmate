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
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import apincer.android.mmate.ui.navigation.MainNavigationInterop
import apincer.android.mmate.ui.navigation.MainNavigationState
import apincer.android.mmate.ui.navigation.LibraryDestination
import apincer.android.mmate.ui.navigation.LibraryDestinationMenuMapping
import apincer.android.mmate.ui.navigation.MusicCenterTab
import apincer.android.mmate.ui.navigation.rememberMainNavigationState
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
    initialLibraryDestination: LibraryDestination = LibraryDestination.ALL_SONGS,
    navigationState: MainNavigationState = rememberMainNavigationState(initialLibraryDestination),
    showGestureHints: Boolean = true,
    trackArtwork: (@Composable (Track) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val appVersion = remember(context) {
        // versionName is "3.21.0-261002"; the build date is not for the drawer.
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()?.substringBefore('-').orEmpty()
    }
    val focusManager = LocalFocusManager.current
    val activeItemId = LibraryDestinationMenuMapping.toMenuItemId(
        navigationState.selectedLibraryDestination
    )
    val onNavigationItemClick: (Int) -> Unit = { itemId ->
        val libraryDestination = LibraryDestinationMenuMapping.fromMenuItemId(itemId)
        if (libraryDestination != null) {
            if (libraryDestination == navigationState.selectedLibraryDestination) {
                callbacks?.onLibraryDestinationChanged(libraryDestination)
            } else {
                navigationState.selectLibrary(libraryDestination)
            }
        } else {
            callbacks?.onNavigationItemClick(itemId)
        }
    }
    var dispatchedDestination by remember { mutableStateOf(initialLibraryDestination) }
    LaunchedEffect(navigationState.selectedLibraryDestination) {
        val selectedDestination = navigationState.selectedLibraryDestination
        if (selectedDestination != dispatchedDestination) {
            dispatchedDestination = selectedDestination
            callbacks?.onLibraryDestinationChanged(selectedDestination)
        }
    }
    DisposableEffect(navigationState) {
        MainNavigationInterop.attach(navigationState)
        onDispose { MainNavigationInterop.detach(navigationState) }
    }

    MainOverlayHost(
        navigationState = navigationState,
        state = state,
        callbacks = callbacks,
        libraryContent = {
    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        drawerContent = {
            val haptic = LocalHapticFeedback.current
            val drawerConfiguration = LocalConfiguration.current
            val useStackedDrawer = UiLayoutPolicy.stackChoiceControls(
                windowWidthDp = drawerConfiguration.screenWidthDp,
                fontScale = LocalDensity.current.fontScale
            )
            val drawerWidth = if (useStackedDrawer) {
                (drawerConfiguration.screenWidthDp * 0.92f).dp
            } else {
                310.dp
            }
            // ── Dark-themed audiophile drawer sheet ────────────────────────────
            Box(
                modifier = Modifier
                    .width(drawerWidth)
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
                                    text = "v$appVersion • Hi-Res Edition",
                                    color = drawerGold.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                    DrawerSectionHeader(stringResource(R.string.nav_section_core_library))
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val coreDestinations = listOf(
                            Triple(R.id.menu_library_all_songs, R.drawable.rounded_library_music_24, R.string.nav_all_songs),
                            Triple(R.id.menu_tag_artist, R.drawable.rounded_for_you_24, R.string.nav_artists),
                            Triple(R.id.menu_tag_genre, R.drawable.rounded_style_24, R.string.nav_genres),
                            Triple(R.id.menu_collection, R.drawable.rounded_order_play_24, R.string.nav_playlists)
                        )

                        if (useStackedDrawer) {
                            coreDestinations.forEach { (itemId, iconResId, labelResId) ->
                                DrawerTile(
                                    text = stringResource(labelResId),
                                    iconResId = iconResId,
                                    isSelected = activeItemId == itemId,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNavigationItemClick(itemId)
                                    coroutineScope.launch { drawerState.close() }
                                }
                            }
                        } else {
                            coreDestinations.chunked(2).forEach { rowDestinations ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowDestinations.forEach { (itemId, iconResId, labelResId) ->
                                        DrawerTile(
                                            text = stringResource(labelResId),
                                            iconResId = iconResId,
                                            isSelected = activeItemId == itemId,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onNavigationItemClick(itemId)
                                            coroutineScope.launch { drawerState.close() }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ── 3. DISCOVERY & AUDIOPHILE TOOLS ───────────────────────
                    DrawerSectionHeader(stringResource(R.string.nav_section_discover_audiophile))
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
                                text = stringResource(R.string.nav_audio_quality),
                                iconResId = R.drawable.rounded_equalizer_24,
                                isSelected = (activeItemId == R.id.menu_sound_grade),
                                badge = "Hi-Res / DR",
                                badgeColor = drawerGold
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigationItemClick(R.id.menu_sound_grade)
                                coroutineScope.launch { drawerState.close() }
                            }
                            HorizontalDivider(color = Color(0x0FFFFFFF), thickness = 0.5.dp)
                            DrawerCardItem(
                                text = stringResource(R.string.nav_similar_tracks),
                                iconResId = R.drawable.rounded_auto_awesome_motion_24,
                                isSelected = (activeItemId == R.id.menu_library_similar_songs)
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigationItemClick(R.id.menu_library_similar_songs)
                                coroutineScope.launch { drawerState.close() }
                            }
                            HorizontalDivider(color = Color(0x0FFFFFFF), thickness = 0.5.dp)
                            DrawerCardItem(
                                text = stringResource(R.string.nav_incoming_tracks),
                                iconResId = R.drawable.rounded_add_diamond_24,
                                isSelected = (activeItemId == R.id.menu_library_recently_added),
                                badge = "New",
                                badgeColor = Color(0xFF00E5FF)
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigationItemClick(R.id.menu_library_recently_added)
                                coroutineScope.launch { drawerState.close() }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ── 4. SYSTEM & PREFERENCES ───────────────────────────────
                    DrawerSectionHeader(stringResource(R.string.nav_section_settings_system))
                    Spacer(modifier = Modifier.height(6.dp))

                    val systemAccess = state.systemAccess.value
                    val accessSummary = when (systemAccess.summary) {
                        SystemAccessSummary.STORAGE_NEEDED -> stringResource(R.string.system_access_summary_storage_needed)
                        SystemAccessSummary.OPTIONAL_ACCESS_OFF -> stringResource(R.string.system_access_summary_optional_off)
                        SystemAccessSummary.READY -> stringResource(R.string.system_access_summary_ready)
                    }
                    val accessSummaryColor = when (systemAccess.summary) {
                        SystemAccessSummary.STORAGE_NEEDED -> Color(0xFFFFB300)
                        SystemAccessSummary.OPTIONAL_ACCESS_OFF -> Color(0xFF90A4AE)
                        SystemAccessSummary.READY -> Color(0xFF63D890)
                    }

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
                                text = stringResource(R.string.nav_music_folders_scan),
                                iconResId = R.drawable.rounded_folder_managed_24,
                                isSelected = (activeItemId == R.id.menu_directories),
                                showChevron = true
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigationItemClick(R.id.menu_directories)
                                coroutineScope.launch { drawerState.close() }
                            }
                            HorizontalDivider(color = Color(0x0FFFFFFF), thickness = 0.5.dp)
                            DrawerCardItem(
                                text = stringResource(R.string.nav_settings),
                                iconResId = R.drawable.ic_round_settings_24,
                                isSelected = (activeItemId == R.id.menu_settings),
                                showChevron = true
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigationItemClick(R.id.menu_settings)
                                coroutineScope.launch { drawerState.close() }
                            }
                            HorizontalDivider(color = Color(0x0FFFFFFF), thickness = 0.5.dp)
                            DrawerCardItem(
                                text = stringResource(R.string.nav_system_access),
                                iconResId = R.drawable.round_sd_storage_24,
                                isSelected = false,
                                badge = accessSummary,
                                badgeColor = accessSummaryColor,
                                showChevron = true
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigationItemClick(R.id.menu_system_access)
                                coroutineScope.launch { drawerState.close() }
                            }
                            HorizontalDivider(color = Color(0x0FFFFFFF), thickness = 0.5.dp)
                            DrawerCardItem(
                                text = stringResource(R.string.nav_diagnostics),
                                iconResId = R.drawable.rounded_bug_report_24,
                                isSelected = (activeItemId == R.id.menu_about_crash),
                                showChevron = true
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigationItemClick(R.id.menu_about_crash)
                                coroutineScope.launch { drawerState.close() }
                            }
                            HorizontalDivider(color = Color(0x0FFFFFFF), thickness = 0.5.dp)
                            DrawerCardItem(
                                text = stringResource(R.string.nav_about_music_mate),
                                iconResId = R.drawable.rounded_info_24,
                                isSelected = (activeItemId == R.id.menu_about_music_mate),
                                showChevron = true
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigationItemClick(R.id.menu_about_music_mate)
                                coroutineScope.launch { drawerState.close() }
                            }
                        }
                    }
                }
            }
        }
    ) {
        val useExpandedNavigation = UiLayoutPolicy.useExpandedNavigation(
            LocalConfiguration.current.screenWidthDp
        )
        Row(modifier = Modifier.fillMaxSize()) {
            if (useExpandedNavigation) {
                ExpandedNavigationRail(
                    activeItemId = activeItemId,
                    onNavigationItemClick = onNavigationItemClick
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF0F0F0F))
            ) boxContent@ {
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
                        focusManager.clearFocus()
                        callbacks?.onSearchBackClick()
                    },
                    onMenuClick = {
                        coroutineScope.launch { drawerState.open() }
                    },
                    showMenuButton = !useExpandedNavigation,
                    statsText = state.headerStatsText.value,
                    // Containers (artists, genres, folders) are not playable as one list.
                    canPlayResults = !state.isPlaylistOverview.value &&
                        state.tracks.firstOrNull()?.isContainer == false,
                    onPlayResults = { shuffle -> callbacks?.onPlayResults(shuffle) },
                    isPlaylistOverview = state.isPlaylistOverview.value,
                    isScanning = state.isScanning.value,
                    scanProgressText = state.scanProgressText.value,
                    isPlaybackTargetActive = isDlnaCast,
                    onMusicCenterClick = {
                        focusManager.clearFocus(force = true)
                        navigationState.openMusicCenter(MusicCenterTab.NOW_PLAYING)
                    },
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
                        listKey = state.musicListKey.value,
                        playbackAvailable = state.isPlaybackAvailable.value,
                        listenerTapMode = state.listenerTapMode.value,
                        selectedTracks = state.selectedTracks.toSet(),
                        nowPlayingTrack = state.nowPlayingTrack.value,
                        isPlaying = state.isPlaying.value,
                        isRefreshing = state.isRefreshing.value,
                        scrollToIndex = state.scrollToIndex.intValue,
                        onScrollComplete = { state.scrollToIndex.intValue = -1 },
                        onRefresh = { callbacks?.onListRefresh() },
                        hasMoreItems = state.hasMoreMusic.value,
                        loadError = state.musicLoadError.value,
                        libraryEmpty = state.libraryEmpty.value,
                        hasActiveFilters = state.hasActiveMusicFilters.value,
                        onDiscoverMusic = { callbacks?.onDiscoverMusicFolders() },
                        onClearFilters = { callbacks?.onClearMusicFilters() },
                        onBrowseAllMusic = { callbacks?.onBrowseAllMusic() },
                        onLoadMore = { callbacks?.onLoadMoreMusic() },
                        onTrackClick = { track, index ->
                            callbacks?.onTrackClick(track, index)
                        },
                        onTrackLongClick = { track, index ->
                            callbacks?.onTrackLongClick(track, index)
                        },
                        onTrackMenuAction = { track, index, actionId ->
                            callbacks?.onTrackMenuAction(track, index, actionId)
                        },
                        onTrackQuickPlayClick = { track ->
                            callbacks?.onTrackQuickPlayClick(track)
                        },
                        onFolderPlayClick = { track ->
                            callbacks?.onFolderPlayClick(track)
                        },
                        onFolderEnqueueClick = { track ->
                            callbacks?.onFolderEnqueueClick(track)
                        },
                        showGestureHints = showGestureHints,
                        trackArtwork = trackArtwork
                    )
                }
            }

            // ── Floating Mini-Player Dock (DESIGN.md §6A: 20dp radius, 12dp horizontal / 8dp bottom margins)
            androidx.compose.animation.AnimatedVisibility(
                visible = UiLayoutPolicy.showFloatingDock(
                    isRequested = state.isFloatingDockVisible.value,
                    hasTrack = state.nowPlayingTrack.value != null
                ),
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
                        focusManager.clearFocus(force = true)
                        navigationState.openMusicCenter(MusicCenterTab.NOW_PLAYING)
                    },
                    onScrollToPlaying = {
                        callbacks?.onDockLongClick()
                    }
                )
            }
        }
        }
    }
        }
    )

    // ── Pure Compose Player Picker Modal Dialog (DESIGN.md §4 & §8A) ─────────
    if (state.showPlayerPickerDialog.value) {
        PlayerPickerDialog(
            targets = state.playerTargets,
            isScanning = state.isPlayerScanning.value,
            hasExternalPlayerAccess = state.systemAccess.value.hasExternalPlayerAccess,
            onDismissRequest = { state.showPlayerPickerDialog.value = false },
            onTargetSelected = { target ->
                callbacks?.onPlayerTargetSelected(target)
                state.showPlayerPickerDialog.value = false
            },
            onRescanClick = { callbacks?.onRescanTargets() },
            onBluetoothOutputClick = {
                callbacks?.onOpenSystemAudioOutput()
                state.showPlayerPickerDialog.value = false
            },
            onExternalPlayerAccessClick = {
                callbacks?.onEnableExternalPlayerAccess()
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

@Composable
private fun ExpandedNavigationRail(
    activeItemId: Int,
    onNavigationItemClick: (Int) -> Unit
) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val destinations = listOf(
        Triple(R.id.menu_library_all_songs, R.drawable.rounded_library_music_24, R.string.nav_all_songs),
        Triple(R.id.menu_tag_artist, R.drawable.rounded_for_you_24, R.string.nav_artists),
        Triple(R.id.menu_tag_genre, R.drawable.rounded_style_24, R.string.nav_genres),
        Triple(R.id.menu_collection, R.drawable.rounded_order_play_24, R.string.nav_playlists),
        Triple(R.id.menu_settings, R.drawable.ic_round_settings_24, R.string.nav_settings)
    )

    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        containerColor = drawerBg,
        header = {
            Icon(
                painter = painterResource(id = R.drawable.ic_nav_musicmate_menu),
                contentDescription = null,
                tint = drawerGold,
                modifier = Modifier
                    .padding(vertical = 18.dp)
                    .size(28.dp)
            )
        }
    ) {
        destinations.forEach { (itemId, iconResId, labelResId) ->
            NavigationRailItem(
                selected = activeItemId == itemId,
                onClick = { onNavigationItemClick(itemId) },
                icon = {
                    Icon(
                        painter = painterResource(id = iconResId),
                        contentDescription = if (largeText) stringResource(labelResId) else null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = if (largeText) null else {
                    {
                        Text(
                            text = stringResource(labelResId),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                colors = androidx.compose.material3.NavigationRailItemDefaults.colors(
                    selectedIconColor = Color.Black,
                    selectedTextColor = drawerGold,
                    indicatorColor = drawerGold,
                    unselectedIconColor = drawerGray,
                    unselectedTextColor = drawerGray
                )
            )
        }
    }
}

// ── Top Search & Stats Bar (DESIGN.md §6 & §4) ──────────────────────────────
@Composable
private fun TopSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isBackVisible: Boolean,
    onBackClick: () -> Unit,
    onMenuClick: () -> Unit,
    showMenuButton: Boolean,
    statsText: String,
    canPlayResults: Boolean = false,
    onPlayResults: (shuffle: Boolean) -> Unit = {},
    isPlaylistOverview: Boolean,
    isScanning: Boolean,
    scanProgressText: String,
    isPlaybackTargetActive: Boolean = false,
    onMusicCenterClick: () -> Unit = {},
    onAddPlaylistClick: () -> Unit = {}
) {
    val searchContentDescription = stringResource(R.string.cd_search_music)
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
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isBackVisible) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                            contentDescription = stringResource(R.string.cd_back),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                } else if (showMenuButton) {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_nav_musicmate_menu),
                            contentDescription = stringResource(R.string.nav_content_description),
                            tint = drawerGold,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Frosted Search Pill
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF242424))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
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
                                    text = stringResource(R.string.search_music_hint),
                                    color = Color(0x77FFFFFF),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics {
                                        contentDescription = searchContentDescription
                                    }
                            )
                        }

                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.round_close_24),
                                    contentDescription = stringResource(R.string.cd_clear_search),
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
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_playlist_add_24),
                            contentDescription = stringResource(R.string.cd_new_smart_playlist),
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Premium Music Center Action Button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onMusicCenterClick),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF383838), Color(0xFF1A1A1A))
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = if (isPlaybackTargetActive) drawerGold.copy(alpha = 0.6f) else Color(0x33FFFFFF),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_equalizer_24),
                            contentDescription = stringResource(R.string.cd_open_music_center),
                            tint = if (isPlaybackTargetActive) drawerGold else Color(0xE5FFFFFF),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (isPlaybackTargetActive) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 6.dp, end = 6.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(drawerGold)
                                .border(1.5.dp, Color(0xFF1A1A1A), CircleShape)
                        )
                    }
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
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (isScanning) {
                    ScanningIndicator(scanProgressText)
                } else if (canPlayResults) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlayResultsPill(R.drawable.ic_baseline_play_arrow_24, "Play") { onPlayResults(false) }
                        PlayResultsPill(R.drawable.ic_baseline_shuffle_24, "Shuffle") { onPlayResults(true) }
                    }
                }
            }
        }
    }
}

// ── Play / Shuffle the current list ──────────────────────────────────────────
@Composable
private fun PlayResultsPill(iconRes: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClickLabel = "$label all", onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x1FFFD700))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = Color(0xFFFFD700),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, color = Color(0xFFFFD700), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
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
            .height(76.dp)
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
                        .size(48.dp)
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
                        .size(48.dp)
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
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_skip_next_24),
                        contentDescription = "Next",
                        tint = Color.White,
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
            .heightIn(min = 48.dp)
            .selectable(
                selected = isSelected,
                role = Role.Tab,
                onClick = onClick
            )
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
    val largeText = LocalDensity.current.fontScale >= 1.3f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .heightIn(min = 48.dp)
            .semantics {
                if (badge != null) stateDescription = badge
            }
            .selectable(
                selected = isSelected,
                role = Role.Tab,
                onClick = onClick
            )
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                color = contentColor,
                fontSize = 13.5.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = if (largeText) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
            if (badge != null && largeText) {
                Text(
                    text = badge,
                    color = badgeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clearAndSetSemantics { }
                )
            }
        }

        if (badge != null && !largeText) {
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
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clearAndSetSemantics { }
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
