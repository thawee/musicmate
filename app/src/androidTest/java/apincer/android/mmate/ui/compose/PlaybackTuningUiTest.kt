package apincer.android.mmate.ui.compose

import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import apincer.music.core.model.AudioTag
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlaybackTuningUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun playbackEntryFitsBesideLongDeviceName() = verifyEntry(1f, "normal")
    @Test fun playbackEntryRemainsReachableAtLargeText() = verifyEntry(1.5f, "large")
    @Test fun rendererEntryExplainsTuningIsNotApplied() = verifyEntry(1f, "renderer", "Living Room")
    @Test fun rendererEntryRemainsReachableAtLargeText() = verifyEntry(1.5f, "renderer-large", "Living Room")

    private fun verifyEntry(scale: Float, name: String, renderer: String? = null) {
        var outputClicks = 0
        val deviceBadge = if (renderer != null) "DLNA" else "USB AUDIO"
        val state = NowPlayingState().apply {
            track.value = AudioTag(9876).apply {
                setTitle("So What"); setArtist("Miles Davis"); setAlbum("Kind of Blue")
                setUniqueKey("tuning-playback-test"); setAudioDuration(545.0)
            }
            targetTitle.value = if (renderer != null) "$renderer streamer with a very long renderer name"
                else "Reference USB DAC with a very long output name"
            targetBadge.value = deviceBadge
            remoteTarget.value = renderer
        }
        compose.setContent {
            MusicMateTheme {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                    NowPlayingPage(state, onPlayPause = {}, onNext = {}, onPrevious = {},
                        onShuffleToggle = {}, onRepeatToggle = {}, onSeek = {}, onTrackClicked = {},
                        onSelectTargetPlayer = { outputClicks++ })
                }
            }
        }
        compose.onNodeWithText("So What", useUnmergedTree = true).assertIsDisplayed()
        if (scale > 1f) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("screencap -p /sdcard/Download/dsp-playback-$name-initial.png")
                .let { ParcelFileDescriptor.AutoCloseInputStream(it).use { s -> s.readBytes() } }
            compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
            compose.onNodeWithText("Tap to flip back").assertDoesNotExist()
            compose.onNodeWithContentDescription("Play").assertIsDisplayed()
        }
        val output = compose.onNodeWithContentDescription("Output").performScrollTo().assertIsDisplayed().assertHasClickAction()
        output.assertHeightIsAtLeast(48.dp)
        output.performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, outputClicks) }
        val chip = compose.onNodeWithContentDescription("MateTune").performScrollTo().assertIsDisplayed()
        val label = compose.onNode(SemanticsMatcher("compact mode and status label") { node ->
            node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.any {
                it.text.matches(Regex("^(Neutral|Warm|Vocal|Lively|Custom) · (On|Bypassed|Unavailable)$"))
            } == true
        }, useUnmergedTree = true).assertIsDisplayed()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        label.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        // Check rendered line bounds: paragraph width can retain the larger measurement
        // constraint even when Text wraps its layout to the shorter intrinsic width.
        assertTrue("Mode and status must wrap without clipping", layout.lineCount <= 2 &&
            !layout.didOverflowHeight && (0 until layout.lineCount).all { line ->
                !layout.isLineEllipsized(line) && layout.getLineLeft(line) >= -1f &&
                    layout.getLineRight(line) <= layout.size.width + 1f
            })
        val bounds = chip.fetchSemanticsNode().boundsInRoot
        val outputBounds = output.fetchSemanticsNode().boundsInRoot
        assertTrue("Output and MateTune share their width", kotlin.math.abs(outputBounds.width - bounds.width) < 2f)
        if (outputBounds.top < bounds.top) assertTrue("Stacked destinations must not overlap", outputBounds.bottom <= bounds.top)
        else assertTrue("Side-by-side destinations share their height", kotlin.math.abs(outputBounds.height - bounds.height) < 2f)
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Tuning entry must fit inside playback", bounds.left >= root.left && bounds.right <= root.right)
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        assertTrue("Tuning entry needs a 48dp target", bounds.height >= 48f * density - 1f)
        val badge = compose.onNodeWithText(deviceBadge, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        if (renderer != null) compose.onNodeWithText("Not applied to this player", useUnmergedTree = true).assertIsDisplayed()
        assertTrue("Device badge must remain a single readable line", badge.height <= bounds.height)
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/dsp-playback-$name.png")
        ParcelFileDescriptor.AutoCloseInputStream(screenshot).use { it.readBytes() }
        chip.performClick()
        compose.onNodeWithText("MateTune · Quick").assertIsDisplayed()
        if (renderer != null) {
            compose.onNodeWithText("Playing on $renderer. Tuning applies only when Music Mate plays on this phone.").assertIsDisplayed()
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("screencap -p /sdcard/Download/dsp-quick-$name.png")
                .let { ParcelFileDescriptor.AutoCloseInputStream(it).use { s -> s.readBytes() } }
        }
    }
}
