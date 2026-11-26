package apincer.android.mmate.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import apincer.music.core.Settings
import apincer.music.core.model.AudioTag
import apincer.music.core.playback.ReplayGainManager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ReplayGainSheetUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val track = AudioTag().apply {
        title = "Take On Me"
        artist = "a-ha"
        album = "Hunting High and Low"
        path = "/music/replaygain-ui-fixture.flac"
    }

    @Test fun missingTagsOffersDirectRepairAndBodySwipesKeepSheetOpen() {
        var measured = false
        var album = false
        var dismissed = false
        compose.setContent {
            MusicMateTheme {
                AudioTuningSheet(onDismissRequest = { dismissed = true }, compact = true,
                    title = "ReplayGain", closeLabel = "Close ReplayGain") {
                    ReplayGainSheetContent(track, null, ReplayGainTags(true), ReplayGainMeasurement.State(),
                        { measured = true }, { album = true }, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("Track gain missing").assertIsDisplayed()
        compose.onNodeWithText("Measure this track").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(measured) }
        screenshot("missing")
        compose.onNodeWithText("Measure complete album").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(album) }
        val scroll = hasScrollAction() and hasAnyDescendant(hasText("Missing ReplayGain in library"))
        repeat(2) { compose.onNode(scroll).performTouchInput { swipeUp() } }
        compose.onNodeWithText("Missing ReplayGain in library").performScrollTo().performClick()
        compose.onNodeWithText("Find missing tags").performScrollTo().assertIsDisplayed()
        compose.onNode(scroll).performTouchInput { swipeDown() }
        compose.runOnIdle { assertFalse("Body swipes must not dismiss the sheet", dismissed) }
        compose.onNodeWithContentDescription("Close ReplayGain").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun statusButtonOpensRepairWhileLevelingIsOff() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val mode = Settings.getReplayGainMode(context)
        try {
            Settings.setReplayGainMode(context, "off")
            compose.setContent { MusicMateTheme { LevelingStatusButton(track, null) } }
            compose.onNodeWithContentDescription("ReplayGain tags and volume leveling").performClick()
            compose.onNodeWithText("Track gain missing").assertIsDisplayed()
            compose.onNodeWithText("Measure this track").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Close ReplayGain").performClick()
        } finally { Settings.setReplayGainMode(context, mode) }
    }

    @Test fun largeTextWrapsAndMissingLibraryRepairRemainsReachable() {
        var fixed = false
        val missing = (1..8).map { index -> AudioTag().apply {
            title = "Missing track $index"; artist = "Long artist name"; path = "/music/$index.flac"
        } }
        compose.setContent {
                MusicMateTheme {
                    AudioTuningSheet(onDismissRequest = {}, compact = true, title = "ReplayGain", closeLabel = "Close ReplayGain") {
                        ReplayGainSheetContent(track, null, ReplayGainTags(true),
                            ReplayGainMeasurement.State(libraryScanned = true, missingTracks = missing),
                            {}, {}, {}, { fixed = true }, {}, {})
                    }
                }
        }
        compose.onNodeWithText("Track gain missing").assertIsDisplayed()
        screenshot("large-text")
        compose.onNodeWithText("Missing ReplayGain in library").performScrollTo().performClick()
        compose.onNodeWithText("8 tracks missing ReplayGain").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Measure missing tracks").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(fixed) }
        compose.onNodeWithText("Measure missing tags while charging").performScrollTo().assertIsDisplayed()
    }

    @Test fun progressCancellationFailureAndTagPresenceStayDistinct() {
        var cancelled = false
        var retried = false
        var state by mutableStateOf(ReplayGainMeasurement.State(phase = ReplayGainMeasurement.Phase.MEASURING,
            progress = .5f, total = 2, currentTrack = track))
        val info = ReplayGainManager.ReplayGainInfo().apply { hasTrackGain = true; trackGainDb = -5.1 }
        compose.setContent {
            MusicMateTheme {
                AudioTuningSheet(onDismissRequest = {}, compact = true, title = "ReplayGain", closeLabel = "Close ReplayGain") {
                    ReplayGainSheetContent(track, "Living room", ReplayGainTags(true, info), state,
                        {}, {}, {}, {}, { cancelled = true }, { retried = true })
                }
            }
        }
        compose.onNodeWithText("Tags present").assertIsDisplayed()
        compose.onNodeWithText("1 of 2 tracks").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancel measurement").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(cancelled); state = state.copy(cancelRequested = true) }
        compose.onNodeWithText("Cancel measurement").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(phase = ReplayGainMeasurement.Phase.COMPLETE,
            succeeded = 1, failedTracks = listOf(track), error = "file access") }
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(retried) }
        compose.onNodeWithText("1 tagged · 1 failed").assertIsDisplayed()
        compose.onNodeWithText("ACTIVE").assertDoesNotExist()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "replaygain-$name.png")
            .outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
