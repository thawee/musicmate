package apincer.android.mmate.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import apincer.android.mmate.audio.headphones.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QuietListeningUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun switchAndStrengthWorkWithoutCorrectionAndPreserveNightAndMode() {
        var binding by mutableStateOf(HeadphoneBinding(enabled = true, nightListening = true,
            soundMode = apincer.android.mmate.audio.dsp.SoundMode.REFERENCE))
        compose.setContent {
            MusicMateTheme {
                AudioTuningSheet({}, compact = true) {
                    QuickTuningControls(HeadphoneStatus(
                        HeadphoneEndpoint("wired", "Wired headphones", false, wired = true), binding, active = true),
                        emptyList(), onSelect = {}, onEnabled = {}, onSoundMode = {}, onFullTuning = {},
                        onQuietListening = { binding = binding.copy(quietListening = it) })
                }
            }
        }
        compose.onNodeWithContentDescription("Quiet listening").performScrollTo().assertIsOff().performClick()
        compose.runOnIdle {
            assertEquals(0.5, binding.quietListening, 0.0)
            assertTrue(binding.nightListening)
        }
        compose.onNodeWithContentDescription("Quiet listening strength").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.8f) }
        compose.runOnIdle {
            assertEquals(0.8, binding.quietListening, 0.001)
            assertEquals(apincer.android.mmate.audio.dsp.SoundMode.REFERENCE, binding.soundMode)
        }
        compose.onNodeWithContentDescription("Quiet listening").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0.0, binding.quietListening, 0.0) }
        compose.onNodeWithContentDescription("Quiet listening strength").assertDoesNotExist()
    }

    @Test fun bypassShowsSavedStateAndAllowsManualStrength() {
        var value by mutableStateOf(0.5)
        compose.setContent { MusicMateTheme { QuietListeningControl(value, active = false) { value = it } } }
        compose.onNodeWithContentDescription("Quiet listening").assertIsOn()
        compose.onNodeWithText("Applies when tuning is on for this output").assertIsDisplayed()
        compose.onNodeWithContentDescription("Quiet listening strength").assertIsEnabled()
    }

    @Test fun disconnectedControlCannotEnableCompensation() {
        var called = false
        compose.setContent { MusicMateTheme { QuietListeningControl(0.0, enabled = false) { called = true } } }
        compose.onNodeWithContentDescription("Quiet listening").assertIsOff().assertIsNotEnabled()
        compose.runOnIdle { assertFalse(called) }
    }
}
