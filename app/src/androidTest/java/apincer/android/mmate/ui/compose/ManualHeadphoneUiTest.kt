package apincer.android.mmate.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import apincer.android.mmate.audio.headphones.HeadphoneBinding
import apincer.android.mmate.audio.headphones.HeadphoneEndpoint
import apincer.android.mmate.audio.headphones.HeadphoneKind
import apincer.android.mmate.audio.headphones.HeadphoneLibrary
import apincer.android.mmate.audio.headphones.HeadphoneProfile
import apincer.android.mmate.audio.headphones.HeadphoneStatus
import apincer.android.mmate.audio.headphones.HeadphoneTonePreset
import apincer.android.mmate.audio.headphones.HeadphoneTone
import apincer.android.mmate.audio.headphones.HeadphoneTestSignal
import apincer.android.mmate.audio.headphones.HeadphoneTestBand
import apincer.android.mmate.audio.dsp.SoundMode
import apincer.android.mmate.audio.headphones.ManualHeadphoneTuning
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManualHeadphoneUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val endpoint = HeadphoneEndpoint("usb:test", "USB DAC", usb = true)

    @Test fun changingRouteDoesNotChangeTheDraftsOutputType() {
        val selected = mutableStateOf(apincer.android.mmate.audio.calibration.CalibrationOutput.HEADPHONE)
        var saved: HeadphoneProfile? = null
        composeRule.setContent {
            TuningTestContent {
                ManualHeadphoneDialog(null, endpoint, onDismiss = {}, onSave = { saved = it }, output = selected.value)
            }
        }
        composeRule.onNodeWithText("Pair name").performTextInput("Unplugged pair")
        composeRule.runOnIdle { selected.value = apincer.android.mmate.audio.calibration.CalibrationOutput.SPEAKER }
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Save profile").performClick()
        composeRule.runOnIdle { assertEquals(apincer.android.mmate.audio.calibration.CalibrationOutput.HEADPHONE, saved!!.output) }
    }

    @Test fun diyCreationWorksWithoutConnectedOutput() {
        var requested = false
        composeRule.setContent {
            TuningTestContent {
                HeadphoneEqControls(HeadphoneLibrary(), HeadphoneStatus(), onManual = { requested = true })
            }
        }
        composeRule.onNodeWithText("Listening").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(requested) }
    }

    @Test fun guidedSetupSavesNameTypeBalanceAndIndependentEarTones() {
        var saved: HeadphoneProfile? = null
        composeRule.setContent {
            TuningTestContent {
                ManualHeadphoneDialog(profile = null, endpoint = endpoint, onDismiss = {}, onSave = { saved = it })
            }
        }
        composeRule.onNodeWithText("Next").assertIsNotEnabled()
        composeRule.onNodeWithText("Pair name").performTextInput("Workshop pair")
        composeRule.onNodeWithText("Earbuds").performClick()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithContentDescription("Left-right balance").performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Manual controls").performClick()
        composeRule.onNodeWithText("Left").performClick()
        composeRule.onNodeWithContentDescription("Bass adjustment").performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
        composeRule.onNodeWithText("Right").performClick()
        composeRule.onNodeWithContentDescription("Mids adjustment").performSemanticsAction(SemanticsActions.SetProgress) { it(-2f) }
        composeRule.onNodeWithContentDescription("Treble adjustment").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        composeRule.onNodeWithText("Save profile").performClick()
        composeRule.runOnIdle {
            assertEquals("Workshop pair", saved?.name)
            val tuning = requireNotNull(saved?.manual)
            assertEquals(HeadphoneKind.EARBUDS, tuning.kind)
            assertEquals(2.0, tuning.balanceDb, 0.01)
            assertEquals(3.0, tuning.left.bassDb, 0.01)
            assertEquals(0.0, tuning.right.bassDb, 0.01)
            assertEquals(-2.0, tuning.right.midDb, 0.01)
            assertEquals(1.0, tuning.right.trebleDb, 0.01)
        }
    }

    @Test fun presetChipSetsBothEarsAndShowsSelected() {
        var saved: HeadphoneProfile? = null
        composeRule.setContent {
            TuningTestContent {
                ManualHeadphoneDialog(profile = null, endpoint = endpoint, onDismiss = {}, onSave = { saved = it })
            }
        }
        composeRule.onNodeWithText("Pair name").performTextInput("Preset pair")
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Manual controls").performClick()
        composeRule.onNodeWithText("Flat").performScrollTo().assertIsSelected()
        composeRule.onNodeWithText("V-shape").performScrollTo().performClick()
        composeRule.onNodeWithText("V-shape").assertIsSelected()
        composeRule.onNodeWithText("Flat").assertIsNotSelected()
        composeRule.onNodeWithText("Save profile").performClick()
        composeRule.runOnIdle {
            val tuning = requireNotNull(saved?.manual)
            assertEquals(HeadphoneTonePreset.V_SHAPE.tone, tuning.left)
            assertEquals(HeadphoneTonePreset.V_SHAPE.tone, tuning.right)
        }
    }

    @Test fun cancellingEditDoesNotSaveOrMutateOriginal() {
        val original = HeadphoneProfile.createManual("Original", ManualHeadphoneTuning())
        var saved: HeadphoneProfile? = null
        var dismissed = false
        composeRule.setContent {
            TuningTestContent {
                ManualHeadphoneDialog(original, endpoint, onDismiss = { dismissed = true }, onSave = { saved = it })
            }
        }
        composeRule.onNodeWithText("Pair name").performTextInput(" changed")
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle {
            assertTrue(dismissed)
            assertNull(saved)
            assertEquals("Original", original.name)
        }
    }

    @Test fun selectedManualProfileKeepsCalibrationWhenSwitchingSoundModes() {
        val profile = HeadphoneProfile.createManual("DIY pair", ManualHeadphoneTuning())
        var editing: HeadphoneProfile? = null
        composeRule.setContent {
            var preset by remember { mutableStateOf(SoundMode.CUSTOM) }
            TuningTestContent {
                HeadphoneEqControls(HeadphoneLibrary(listOf(profile)),
                    HeadphoneStatus(endpoint, HeadphoneBinding(profile.id, soundMode = preset), profile),
                    onManual = { editing = it }, onSoundMode = { preset = it })
            }
        }
        composeRule.onNodeWithText("Custom").performScrollTo().assertIsSelected()
        composeRule.onNodeWithContentDescription("Bass adjustment").assertExists()
        composeRule.onNodeWithText("Bass").performClick().assertIsSelected()
        composeRule.onNodeWithText("Neutral").performScrollTo().performClick().assertIsSelected()
        composeRule.onNodeWithContentDescription("Bass adjustment").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Treble adjustment").assertDoesNotExist()
        composeRule.onNodeWithText("Edit listening setup").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(profile.id, editing?.id) }
    }

    @Test fun leavingListeningPageStopsInjectedTestAudioAndBypassIsFlat() {
        var stopped = 0
        var playedFlat = false
        composeRule.setContent {
            TuningTestContent {
                ManualHeadphoneDialog(null, endpoint, onDismiss = {}, onSave = {},
                    onPlay = { _, settings, _, _ ->
                        playedFlat = settings.bands.isEmpty()
                        awaitCancellation()
                    }, onStop = { stopped++ })
            }
        }
        composeRule.onNodeWithText("Pair name").performTextInput("Test pair")
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Bypass draft EQ").performScrollTo().performClick()
        composeRule.onNodeWithText("Play centered").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(playedFlat) }
        var before = 0
        composeRule.runOnIdle { before = stopped }
        composeRule.onNodeWithText("Next").performClick()
        composeRule.runOnIdle { assertTrue(stopped > before) }
    }

    @Test fun guidedTonesProgressLowToHighAndPreserveSkippedAndOppositeEarValues() {
        val original = HeadphoneProfile.createManual("Guide pair", ManualHeadphoneTuning(
            left = HeadphoneTone(midDb = -1.0), right = HeadphoneTone(trebleDb = 2.0)))
        var saved: HeadphoneProfile? = null
        val heard = mutableListOf<Pair<HeadphoneTestSignal, HeadphoneTestBand>>()
        composeRule.setContent {
            TuningTestContent {
                ManualHeadphoneDialog(original, endpoint, onDismiss = {}, onSave = { saved = it },
                    onPlay = { signal, _, _, band -> heard += signal to band })
            }
        }
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Bass · 100 Hz").assertExists()
        composeRule.onNodeWithText("Left").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Bass adjustment").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
        composeRule.onNodeWithText("Play left").performScrollTo().performClick()
        composeRule.onNodeWithText("Next tone").performScrollTo().performClick()
        composeRule.onNodeWithText("Mids · 1 kHz").assertExists()
        composeRule.onNodeWithText("Skip tone").performScrollTo().performClick()
        composeRule.onNodeWithText("Treble · 8 kHz").assertExists()
        composeRule.onNodeWithText("Play right").performScrollTo().performClick()
        composeRule.onNodeWithText("Finish tones").performScrollTo().performClick()
        composeRule.onNodeWithText("Save profile").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(HeadphoneTestSignal.LEFT to HeadphoneTestBand.BASS,
                HeadphoneTestSignal.RIGHT to HeadphoneTestBand.TREBLE), heard)
            assertEquals(original.manual!!.left.copy(bassDb = 3.0), saved!!.manual!!.left)
            assertEquals(original.manual!!.right, saved!!.manual!!.right)
        }
    }
}
