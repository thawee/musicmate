package apincer.android.mmate.ui.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import apincer.android.mmate.ui.navigation.MainNavigationState
import apincer.android.mmate.ui.navigation.MainOverlaySceneStrategy
import apincer.android.mmate.ui.navigation.MainRoute
import apincer.android.mmate.ui.navigation.MusicCenterTab
import apincer.android.mmate.ui.navigation.LibraryDestination

@Composable
internal fun MainOverlayHost(
    navigationState: MainNavigationState,
    state: MainScaffoldState,
    callbacks: MainScaffoldCallbacks?,
) {
    // Layoutlib previews do not install a navigation-event owner. Their shell fixtures render
    // with the overlay stack closed, so there is no scene for the preview host to display.
    if (LocalNavigationEventDispatcherOwner.current == null) return

    val overlaySceneStrategy = remember { MainOverlaySceneStrategy() }
    NavDisplay(
        modifier = Modifier.fillMaxSize(),
        backStack = navigationState.navBackStack,
        onBack = { navigationState.popOverlay() },
        sceneStrategies = listOf(overlaySceneStrategy),
        entryProvider = entryProvider<NavKey> {
            entry<MainRoute.Library> { }
            entry<MainRoute.MusicCenter> { route ->
                MusicCenterOverlay(route, navigationState, state, callbacks)
            }
            entry<MainRoute.StudioConsole> {
                StudioConsoleOverlay(navigationState, state, callbacks)
            }
        }
    )
}

@Composable
private fun MusicCenterOverlay(
    route: MainRoute.MusicCenter,
    navigationState: MainNavigationState,
    state: MainScaffoldState,
    callbacks: MainScaffoldCallbacks?,
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
        onEngineChanged = { engine -> callbacks?.onEngineChanged(engine) },
        onStartServerClicked = { callbacks?.onStartServerClicked() },
        onStopServerClicked = { callbacks?.onStopServerClicked() },
        onCopyUrlClicked = { callbacks?.onCopyUrlClicked() },
        onOpenUrlClicked = { callbacks?.onOpenUrlClicked() },
        onQrCodeClicked = { callbacks?.onQrCodeClicked() },
        onOpenFullscreen = { navigationState.openStudioConsole() },
        onTabChanged = { page ->
            navigationState.updateMusicCenterTab(MusicCenterTab.fromPage(page))
        }
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
