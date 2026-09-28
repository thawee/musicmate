package apincer.android.mmate.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "Playback no track", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun MusicCenterPlaybackEmptyPreview() {
    MusicCenterPreview(
        initialTab = 0,
        nowPlayingState = remember { UxPreviewFixtures.nowPlayingEmpty() }
    )
}

@PreviewTest
@Preview(name = "Playback playing", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun MusicCenterPlaybackPlayingPreview() {
    MusicCenterPreview(
        initialTab = 0,
        nowPlayingState = remember { UxPreviewFixtures.nowPlayingPlaying() }
    )
}

@PreviewTest
@Preview(name = "Queue empty", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun MusicCenterQueueEmptyPreview() {
    MusicCenterPreview(
        initialTab = 1,
        queueState = remember { UxPreviewFixtures.emptyQueue() }
    )
}

@PreviewTest
@Preview(name = "Queue populated", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun MusicCenterQueuePopulatedPreview() {
    MusicCenterPreview(
        initialTab = 1,
        queueState = remember { UxPreviewFixtures.populatedQueue() }
    )
}

@PreviewTest
@Preview(name = "Server stopped", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun MusicCenterServerStoppedPreview() {
    MusicCenterPreview(
        initialTab = 2,
        mediaServerState = remember { UxPreviewFixtures.serverStopped() }
    )
}

@PreviewTest
@Preview(name = "Server running", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun MusicCenterServerRunningPreview() {
    MusicCenterPreview(
        initialTab = 2,
        mediaServerState = remember { UxPreviewFixtures.serverRunning() }
    )
}

@Composable
internal fun MusicCenterPreview(
    initialTab: Int,
    nowPlayingState: NowPlayingState = remember { UxPreviewFixtures.nowPlayingEmpty() },
    queueState: QueueState = remember { UxPreviewFixtures.emptyQueue() },
    mediaServerState: MediaServerState = remember { UxPreviewFixtures.serverStopped() },
    presentation: MusicCenterPresentation = MusicCenterPresentation.PREVIEW,
) {
    MusicMateTheme {
        AudioHubSheet(
            nowPlayingState = nowPlayingState,
            queueState = queueState,
            mediaServerState = mediaServerState,
            initialTab = initialTab,
            onDismissRequest = {},
            onSelectTargetPlayer = {},
            onPlayPause = {},
            onNext = {},
            onPrevious = {},
            onShuffleToggle = {},
            onRepeatToggle = {},
            onSeek = {},
            onVolumeDown = {},
            onVolumeUp = {},
            onVolumeChanged = {},
            onSleepTimerSelected = { _, _ -> },
            onTrackClicked = {},
            onQueueTrackClicked = {},
            onQueueTrackRemoved = { _, _ -> },
            onQueueTrackMoved = { _, _ -> },
            onQueueClear = {},
            onQueueJumpToPlaying = {},
            onQueueBrowseLibrary = {},
            onEngineChanged = {},
            onStartServerClicked = {},
            onStopServerClicked = {},
            onCopyUrlClicked = {},
            onOpenUrlClicked = {},
            onQrCodeClicked = {},
            onOpenFullscreen = {},
            showGestureHints = false,
            trackArtwork = { track -> PreviewTrackArtwork(track) },
            presentation = presentation
        )
    }
}
