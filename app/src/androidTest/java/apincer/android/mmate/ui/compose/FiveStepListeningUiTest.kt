package apincer.android.mmate.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import apincer.android.mmate.audio.dsp.SoundMode
import apincer.android.mmate.audio.dsp.CarDspSettings
import apincer.android.mmate.audio.headphones.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FiveStepListeningUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val endpoint = HeadphoneEndpoint("wired:guide", "Wired headphones", false, wired = true)
    private val profile = HeadphoneProfile.createManual("My pair", ManualHeadphoneTuning())

    @Test fun fiveMusicalStepsSaveIndependentBandsAndReachFinalMusicCheck() {
        var saved: HeadphoneProfile? = null
        val heard = mutableListOf<ListeningSample?>()
        compose.setContent {
            MusicMateTheme {
                ManualHeadphoneDialog(profile, endpoint, onDismiss = {}, onSave = { saved = it },
                    onPlay = { _, _, _, _, sample, _ -> heard += sample })
            }
        }
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Next").performClick()
        val steps = listOf("Bass impact", "Warmth", "Vocal naturalness", "Clarity", "Treble detail")
        steps.forEachIndexed { index, label ->
            compose.onNodeWithText(label).assertExists()
            compose.onNodeWithContentDescription("$label adjustment").performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it((index + 1).toFloat()) }
            compose.onNodeWithText("Next step").performScrollTo().performClick()
        }
        compose.onNodeWithText("Final music check").assertExists()
        compose.onNodeWithText("Play centered").performScrollTo().performClick()
        compose.onNodeWithText("Save profile").performClick()
        compose.runOnIdle {
            assertEquals(ListeningSample.MUSIC, heard.last())
            val tone = saved!!.manual!!.left
            assertEquals(tone, saved!!.manual!!.right)
            assertEquals(listOf(1.0, 2.0, 3.0, 4.0, 5.0), listOf(tone.bassDb, tone.warmthDb, tone.midDb, tone.clarityDb, tone.trebleDb))
        }
    }

    @Test fun originalAndAdjustedKeepTheSameDraftAndVoiceForMatching() {
        val settings = mutableListOf<CarDspSettings>()
        val originals = mutableListOf<Boolean>()
        val samples = mutableListOf<ListeningSample?>()
        val calibrated = profile.copy(manual = ManualHeadphoneTuning(left = HeadphoneTone(warmthDb = 2.0), right = HeadphoneTone(clarityDb = -2.0)))
        compose.setContent {
            MusicMateTheme {
                ManualHeadphoneDialog(calibrated, endpoint, onDismiss = {}, onSave = {}, playbackMode = SoundMode.LIVELY,
                    onPlay = { _, eq, _, _, sample, original -> settings += eq; samples += sample; originals += original })
            }
        }
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Skip step").performScrollTo().performClick()
        compose.onNodeWithText("Female voice").performScrollTo().performClick()
        compose.onNodeWithText("Warmth").performScrollTo()
        screenshot("five-step-listening")
        compose.onNodeWithText("Playback mode: Lively. This adds processing after saving. Choose Neutral in Audio tuning to hear only your calibration.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Original").performScrollTo().performClick()
        compose.onNodeWithText("Play left").performScrollTo().performClick()
        screenshot("five-step-comparison")
        compose.onNodeWithText("Adjusted").performScrollTo().performClick()
        compose.onNodeWithText("Play left").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(ListeningSample.FEMALE_VOICE, ListeningSample.FEMALE_VOICE), samples)
            assertEquals(listOf(true, false), originals)
            assertEquals(settings.first().leftBands, settings.last().leftBands)
            assertEquals(settings.first().rightBands, settings.last().rightBands)
            assertEquals(settings.first().balanceDb, settings.last().balanceDb, 0.0)
            val expected = calibrated.manual!!.settings()
            val actual = settings.last()
            assertEquals(expected.bands, actual.bands)
            assertEquals(expected.leftBands, actual.leftBands)
            assertEquals(expected.rightBands, actual.rightBands)
            assertEquals(expected.monoBands, actual.monoBands)
            assertEquals(expected.balanceDb, actual.balanceDb, 0.0)
        }
    }
    @Test fun customPlaybackExplainsThatSavingClearsExtraTrims() {
        compose.setContent {
            MusicMateTheme {
                ManualHeadphoneDialog(profile, endpoint, onDismiss = {}, onSave = {}, playbackMode = SoundMode.CUSTOM)
            }
        }
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Playback mode: Custom. Saving clears its extra bass and treble trims; your calibration remains.")
            .performScrollTo().assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        compose.onNodeWithText("Edit listening setup").captureToImage()
        val descriptor = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/$name.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

}
