package apincer.android.mmate.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import apincer.android.mmate.audio.calibration.CorrectionProfiles
import apincer.android.mmate.audio.dsp.SoundMode
import apincer.android.mmate.audio.headphones.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CustomSoundControlsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun draggingUpdatesSignatureAndPreviewBeforeGestureEnds() {
        val previews = mutableListOf<Pair<Double, Double>>()
        val saves = mutableListOf<Pair<Double, Double>>()
        compose.setContent {
            MusicMateTheme {
                CustomSoundControls("test", 0.0, 0.0, false,
                    onAdjust = { bass, treble -> previews.add(bass to treble) },
                    onAdjustFinished = { bass, treble -> saves.add(bass to treble) })
            }
        }
        compose.onNodeWithText("Balanced").assertExists()
        compose.onNodeWithContentDescription("Bass adjustment").performTouchInput {
            down(center); moveTo(centerRight)
        }
        compose.onNodeWithText("Fuller").assertExists()
        compose.runOnIdle { assertTrue(previews.last().first > 0.0); assertTrue(saves.isEmpty()) }
        compose.onNodeWithContentDescription("Bass adjustment").performTouchInput { up() }
        compose.runOnIdle { assertEquals(previews.last(), saves.last()) }
    }

    @Test fun resetRetainsNightAndLeavingCustomClearsComparisonAndPreview() {
        var visible by mutableStateOf(true)
        val comparisons = mutableListOf<Boolean>()
        var cleared = 0
        var trims: Pair<Double, Double>? = null
        compose.setContent {
            MusicMateTheme {
                if (visible) CustomSoundControls("test", 3.0, -2.0, true,
                    onAdjust = { bass, treble -> trims = bass to treble },
                    onCompare = { comparisons.add(it) }, onPreviewEnd = { cleared++ })
            }
        }
        compose.onNodeWithText("Warm and full").assertExists()
        compose.onNodeWithText("Compare").performClick()
        compose.onNodeWithText("Comparing: correction without Custom adjustments").assertExists()
        compose.runOnIdle { assertTrue(comparisons.last()) }
        compose.onNodeWithText("Reset").performClick()
        compose.onNodeWithText("Balanced").assertExists()
        compose.onNodeWithText("Compare").assertExists()
        compose.runOnIdle { assertEquals(0.0 to 0.0, trims); assertFalse(comparisons.last()) }
        compose.onNodeWithText("Compare").performClick()
        compose.runOnIdle { visible = false }
        compose.runOnIdle { assertFalse(comparisons.last()); assertEquals(1, cleared) }
    }

    @Test fun nightRemainsIndependentOfPresetAndCustomTrims() {
        val profile = HeadphoneProfile.createManual("Test headphones", ManualHeadphoneTuning())
        var binding by mutableStateOf(HeadphoneBinding(profile.id, soundMode = SoundMode.RELAX,
            bassDb = 2.0, trebleDb = -1.0))
        compose.setContent {
            MusicMateTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    QuickTuningControls(HeadphoneStatus(HeadphoneEndpoint("wired:test", "Headphones", false, wired = true), binding, profile),
                        CorrectionProfiles.choices(emptyList(), listOf(profile)), onSelect = {}, onEnabled = {},
                        onSoundMode = { binding = binding.copy(soundMode = it) }, onFullTuning = {},
                        onNightListening = { binding = binding.copy(nightListening = it) })
                }
            }
        }
        compose.onNodeWithContentDescription("Night listening").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithText("Warm").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Custom").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Night listening").performScrollTo().assertIsOn()
        compose.runOnIdle {
            assertEquals(2.0, binding.bassDb, 0.0); assertEquals(-1.0, binding.trebleDb, 0.0)
        }
    }
}
