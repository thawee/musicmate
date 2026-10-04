package apincer.android.mmate.ui.compose

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import apincer.music.core.model.AudioTag
import apincer.music.core.playback.PlaybackState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicCenterAccessibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun noTrack_hasNamedDisabledPlaybackControls() {
        composeRule.setContent {
            MusicMateTheme {
                NowPlayingPage(
                    state = NowPlayingState(),
                    onPlayPause = {}, onNext = {}, onPrevious = {},
                    onShuffleToggle = {}, onRepeatToggle = {}, onSeek = {},
                    onTrackClicked = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Playback position").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Play").assertIsNotEnabled()
        runAccessibilityChecks()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun playing_hasNamedTransportAndToggleStates() {
        val state = NowPlayingState().apply {
            track.value = testTrack(101, "So What")
            playbackState.value = PlaybackState().apply { currentState = PlaybackState.State.PLAYING }
            progressMs.value = 183_000L
            durationMs.value = 545_000L
            isShuffle.value = true
            repeatMode.value = 2
            isSleepTimerActive.value = true
            sleepTimerText.value = "15m"
        }
        composeRule.setContent {
            MusicMateTheme {
                NowPlayingPage(
                    state = state,
                    onPlayPause = {}, onNext = {}, onPrevious = {},
                    onShuffleToggle = {}, onRepeatToggle = {}, onSeek = {},
                    onTrackClicked = {},
                    trackArtwork = { Box(Modifier.fillMaxSize().background(Color.DarkGray)) }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Pause").assertExists()
        composeRule.onNodeWithContentDescription("Shuffle, on").assertExists()
        composeRule.onNodeWithContentDescription("Repeat one").assertExists()
        composeRule.onNodeWithContentDescription("Sleep timer, 15m remaining").assertExists()
        composeRule.onNodeWithContentDescription("Playback position, 3:03 of 9:05").assertExists()
        composeRule.onNodeWithContentDescription("Volume, 0 percent").assertExists()
        composeRule.onNodeWithContentDescription("Volume down").assertExists()
        composeRule.onNodeWithContentDescription("Volume up").assertExists()
        runAccessibilityChecks()
    }

    @Test
    fun musicCenter_tabsExposeSelectionCountAndServerState() {
        composeRule.setContent {
            MusicMateTheme {
                AudioHubSheet(
                    nowPlayingState = NowPlayingState(),
                    queueState = QueueState(listOf(testTrack(101, "So What")), null),
                    mediaServerState = MediaServerState().apply { isServerRunning = true },
                    initialTab = 0,
                    onDismissRequest = {}, onSelectTargetPlayer = {},
                    onPlayPause = {}, onNext = {}, onPrevious = {},
                    onShuffleToggle = {}, onRepeatToggle = {}, onSeek = {},
                    onVolumeDown = {}, onVolumeUp = {}, onVolumeChanged = {},
                    onTrackClicked = {}, onQueueTrackClicked = {},
                    onQueueTrackRemoved = { _, _ -> }, onQueueClear = {},
                    onQueueJumpToPlaying = {}, onQueueBrowseLibrary = {},
                    onStartServerClicked = {},
                    onStopServerClicked = {}, onCopyUrlClicked = {},
                    onOpenUrlClicked = {}, onQrCodeClicked = {},
                    showGestureHints = false,
                    presentation = MusicCenterPresentation.PREVIEW
                )
            }
        }

        composeRule.onNodeWithText("Playback").assertIsSelected()
        composeRule.onNodeWithContentDescription("Queue, 1 track").assertExists()
        composeRule.onNodeWithContentDescription("Server, running").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun queue_exposesRemoveAndReorderAlternatives() {
        val first = testTrack(101, "So What")
        val second = testTrack(102, "Blue in Green")
        composeRule.setContent {
            MusicMateTheme {
                QueuePage(
                    state = QueueState(listOf(first, second), first.uniqueKey),
                    onTrackClicked = {}, onTrackRemoved = { _, _ -> },
                    onClearQueue = {}, onJumpToPlaying = {}, onBrowseLibrary = {},
                    onMoveTrack = { _, _ -> }, showGestureHints = false,
                    trackArtwork = { Box(Modifier.fillMaxSize().background(Color.DarkGray)) }
                )
            }
        }

        val actions = composeRule.onNodeWithText("So What")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .map { it.label }
        assertTrue(actions.contains("Remove So What from queue"))
        assertTrue(actions.contains("Move So What down"))
        runAccessibilityChecks()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun server_exposesStatusUrlAndQr() {
        val state = MediaServerState().apply {
            isServerRunning = true
            serverStatusText = "MusicMate Server"
            serverUrl = "http://192.168.1.42:9000"
            broadcastInfo = "DLNA 1.5 • Wi-Fi • Port 9000"
            qrCodeBitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        }
        composeRule.setContent {
            MusicMateTheme {
                MediaServerPage(
                    state = state, onStartClicked = {},
                    onStopClicked = {}, onCopyUrlClicked = {}, onOpenUrlClicked = {},
                    onQrCodeClicked = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Media server, running").assertExists()
        composeRule.onNodeWithContentDescription("Copy server URL, http://192.168.1.42:9000").assertExists()
        composeRule.onNodeWithContentDescription("Enlarge WebUI QR code").assertExists()
        runAccessibilityChecks()
    }

    private fun testTrack(id: Long, title: String) = AudioTag(id).apply {
        setUniqueKey("accessibility-track-$id")
        setTitle(title)
        setArtist("Miles Davis")
        setAlbum("Kind of Blue")
        setAudioDuration(545.0)
        setAudioEncoding("FLAC")
    }

    @OptIn(ExperimentalTestApi::class)
    private fun runAccessibilityChecks() {
        composeRule.enableAccessibilityChecks()
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }
}
