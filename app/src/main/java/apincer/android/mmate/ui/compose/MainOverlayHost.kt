package apincer.android.mmate.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.SupportingPaneSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberSupportingPaneSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import apincer.android.mmate.ui.navigation.MainNavigationState
import apincer.android.mmate.ui.navigation.MainOverlaySceneStrategy
import apincer.android.mmate.ui.navigation.MainRoute
import apincer.android.mmate.ui.navigation.MusicCenterTab
import apincer.android.mmate.ui.navigation.LibraryDestination

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun MainOverlayHost(
    navigationState: MainNavigationState,
    state: MainScaffoldState,
    callbacks: MainScaffoldCallbacks?,
    libraryContent: @Composable () -> Unit,
) {
    // Layoutlib previews do not install a navigation-event owner, so render the root directly.
    if (LocalNavigationEventDispatcherOwner.current == null) {
        libraryContent()
        return
    }

    val useSupportingPane = UiLayoutPolicy.useMusicCenterSupportingPane(
        LocalConfiguration.current.screenWidthDp
    )
    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    val paneDirective = remember(windowAdaptiveInfo) {
        calculatePaneScaffoldDirective(windowAdaptiveInfo).copy(
            horizontalPartitionSpacerSize = 0.dp,
            verticalPartitionSpacerSize = 0.dp,
        )
    }
    val supportingPaneStrategy = rememberSupportingPaneSceneStrategy<NavKey>(
        backNavigationBehavior = BackNavigationBehavior.PopLatest,
        directive = paneDirective,
    )
    val overlaySceneStrategy = remember(useSupportingPane) {
        MainOverlaySceneStrategy(useMusicCenterOverlay = !useSupportingPane)
    }
    NavDisplay(
        modifier = Modifier.fillMaxSize(),
        backStack = navigationState.navBackStack,
        onBack = { navigationState.popOverlay() },
        sceneStrategies = listOf(overlaySceneStrategy, supportingPaneStrategy),
        entryProvider = entryProvider<NavKey> {
            entry<MainRoute.Library>(
                metadata = SupportingPaneSceneStrategy.mainPane()
            ) {
                libraryContent()
            }
            entry<MainRoute.MusicCenter>(
                metadata = SupportingPaneSceneStrategy.supportingPane()
            ) { route ->
                MusicCenterOverlay(
                    route = route,
                    navigationState = navigationState,
                    state = state,
                    callbacks = callbacks,
                    renderAsSupportingPane = useSupportingPane,
                )
            }
            entry<MainRoute.StudioConsole> {
                StudioConsoleOverlay(navigationState, state, callbacks)
            }
        }
    )
    BackHandler(
        enabled = useSupportingPane && navigationState.overlayRoute is MainRoute.MusicCenter
    ) {
        navigationState.dismissMusicCenter()
    }
}

@Composable
private fun MusicCenterOverlay(
    route: MainRoute.MusicCenter,
    navigationState: MainNavigationState,
    state: MainScaffoldState,
    callbacks: MainScaffoldCallbacks?,
    renderAsSupportingPane: Boolean,
) {
    AudioHubSheet(
        nowPlayingState = state.nowPlayingState,
        queueState = state.queueState,
        mediaServerState = state.mediaServerState,
        initialTab = route.tab.ordinal,
        onDismissRequest = { navigationState.dismissMusicCenter() },
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
        onSleepTimerSelected = { minutes, endOfTrack ->
            callbacks?.onAudioHubSleepTimerSelected(minutes, endOfTrack)
        },
        onTrackClicked = { callbacks?.onAudioHubTrackClick() },
        onQueueTrackClicked = { track -> callbacks?.onAudioHubQueueTrackClick(track) },
        onQueueTrackRemoved = { track, index ->
            callbacks?.onAudioHubQueueTrackRemove(track, index)
        },
        onQueueTrackMoved = { from, to -> callbacks?.onAudioHubQueueTrackMoved(from, to) },
        onQueueClear = { callbacks?.onAudioHubQueueClear() },
        onQueueJumpToPlaying = { callbacks?.onAudioHubQueueJumpToPlaying() },
        onQueueBrowseLibrary = {
            val destinationChanged = navigationState.selectedLibraryDestination !=
                LibraryDestination.ALL_SONGS
            navigationState.selectLibrary(LibraryDestination.ALL_SONGS)
            if (!destinationChanged) {
                callbacks?.onLibraryDestinationChanged(LibraryDestination.ALL_SONGS)
            }
        },
        onStartServerClicked = { callbacks?.onStartServerClicked() },
        onStopServerClicked = { callbacks?.onStopServerClicked() },
        onCopyUrlClicked = { callbacks?.onCopyUrlClicked() },
        onOpenUrlClicked = { callbacks?.onOpenUrlClicked() },
        onQrCodeClicked = { callbacks?.onQrCodeClicked() },
        onOpenFullscreen = { navigationState.openStudioConsole() },
        onTabChanged = { page ->
            navigationState.updateMusicCenterTab(MusicCenterTab.fromPage(page))
        },
        presentation = if (renderAsSupportingPane) {
            MusicCenterPresentation.SUPPORTING_PANE
        } else {
            MusicCenterPresentation.MODAL
        },
    )
}

@Composable
private fun StudioConsoleOverlay(
    navigationState: MainNavigationState,
    state: MainScaffoldState,
    callbacks: MainScaffoldCallbacks?,
) {
    FullscreenStudioConsole(
        state = state.nowPlayingState,
        queueState = state.queueState,
        onDismissRequest = { navigationState.dismissStudioConsole() },
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
