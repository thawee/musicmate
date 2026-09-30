package apincer.android.mmate.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "MQA compact", widthDp = 360, heightDp = 400)
@Preview(name = "MQA wider", widthDp = 390, heightDp = 400)
@Preview(name = "MQA large text", widthDp = 360, heightDp = 600, fontScale = 2f)
@Composable
fun MqaBadgesPreview() {
    MusicMateTheme {
        Column(Modifier.fillMaxWidth().background(Color(0xFF101010)).verticalScroll(rememberScrollState()).padding(8.dp)) {
            for (studio in listOf(false, true)) {
                val track = remember(studio) { UxPreviewFixtures.mqaTrack(studio) }
                TrackListItem(
                    track = track, isSelected = false, isNowPlaying = false, isPlaying = false,
                    onClick = {}, onLongClick = {}, onMenuClick = {},
                    artwork = { PreviewTrackArtwork(it) }
                )
                QualityBadge(track, expanded = true)
                QualityBadge(labelStr = track.qualityInd, expanded = true)
            }
        }
    }
}

@PreviewTest
@Preview(name = "MQA playback", widthDp = 360, heightDp = 800)
@Composable
fun MqaPlaybackPreview() {
    MusicCenterPreview(initialTab = 0, nowPlayingState = remember { UxPreviewFixtures.mqaPlaying() })
}

@PreviewTest
@Preview(name = "MQA audio details", widthDp = 360, heightDp = 800)
@Preview(name = "MQA audio details large text", widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
fun MqaAudioDetailsPreview() {
    MusicMateTheme {
        NowPlayingPage(
            state = remember { UxPreviewFixtures.mqaPlaying() },
            onPlayPause = {}, onNext = {}, onPrevious = {}, onShuffleToggle = {},
            onRepeatToggle = {}, onSeek = {}, onTrackClicked = {},
            trackArtwork = { PreviewTrackArtwork(it) },
            showAudioDetailsInitially = true
        )
    }
}

@PreviewTest
@Preview(name = "MQA full screen", widthDp = 900, heightDp = 480)
@Preview(name = "MQA full screen large text", widthDp = 900, heightDp = 480, fontScale = 2f)
@Composable
fun MqaFullscreenPreview() {
    MusicMateTheme {
        FullscreenStudioConsole(
            state = remember { UxPreviewFixtures.mqaPlaying() },
            queueState = remember { UxPreviewFixtures.emptyQueue() },
            onDismissRequest = {}, onPlayPause = {}, onNext = {}, onPrevious = {},
            onShuffleToggle = {}, onRepeatToggle = {}, onSeek = {}, onVolumeChanged = {},
            onSelectTargetPlayer = {}
        )
    }
}
