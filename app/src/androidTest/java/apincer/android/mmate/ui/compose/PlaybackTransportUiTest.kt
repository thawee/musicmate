package apincer.android.mmate.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import apincer.music.core.model.AudioTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlaybackTransportUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun compactPlaybackHasLargeSeparatedTransport() = verifyTransport(1f)
    @Test fun largeTextPreservesTransportReachability() = verifyTransport(1.5f)

    private fun verifyTransport(fontScale: Float) {
        var previous = 0
        var next = 0
        var play = 0
        var pixelsPerDp = 1f
        val state = NowPlayingState().apply {
            track.value = AudioTag(9876).apply {
                setTitle("So What"); setArtist("Miles Davis"); setAlbum("Kind of Blue")
                setUniqueKey("transport-test"); setAudioDuration(545.0)
            }
        }
        compose.setContent {
            MusicMateTheme {
                pixelsPerDp = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(pixelsPerDp, fontScale)) {
                    Box(Modifier.size(320.dp, 640.dp)) {
                        NowPlayingPage(state, onPlayPause = { play++ }, onNext = { next++ },
                            onPrevious = { previous++ }, onShuffleToggle = {}, onRepeatToggle = {},
                            onSeek = {}, onTrackClicked = {})
                    }
                }
            }
        }
        val previousButton = compose.onNodeWithContentDescription("Previous track").assertIsDisplayed()
            .assertWidthIsAtLeast(80.dp).assertHeightIsAtLeast(80.dp)
        val nextButton = compose.onNodeWithContentDescription("Next track").assertIsDisplayed()
            .assertWidthIsAtLeast(80.dp).assertHeightIsAtLeast(80.dp)
        val playButton = compose.onNodeWithContentDescription("Play").assertIsDisplayed()
            .assertWidthIsAtLeast(80.dp).assertHeightIsAtLeast(80.dp)
        val before = previousButton.fetchSemanticsNode().boundsInRoot
        val center = playButton.fetchSemanticsNode().boundsInRoot
        val after = nextButton.fetchSemanticsNode().boundsInRoot
        assertTrue("Primary controls need at least 12dp separation", center.left - before.right >= 12f * pixelsPerDp - 1f && after.left - center.right >= 12f * pixelsPerDp - 1f)
        val shuffle = compose.onNodeWithContentDescription("Shuffle, off").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Secondary controls must be outside the transport row", shuffle.top >= before.bottom || shuffle.bottom <= before.top)
        assertTrue("Transport must fit the compact viewport", before.left >= 0f && after.right <= 320f * pixelsPerDp + 1f && after.bottom <= 640f * pixelsPerDp + 1f)
        val screenshot = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/playback-transport-$fontScale.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(screenshot).use { it.readBytes() }
        previousButton.performClick()
        nextButton.performClick()
        playButton.performClick()
        compose.runOnIdle { assertEquals(1, previous); assertEquals(1, next); assertEquals(1, play) }
    }
}
