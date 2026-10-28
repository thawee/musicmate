package apincer.android.mmate.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import apincer.android.mmate.audio.calibration.CalibrationOutput
import apincer.android.mmate.audio.calibration.CalibrationWizard
import apincer.android.mmate.audio.calibration.SweepRecorder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeadphoneMicrophoneUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun headphoneStartRequiresNamedFixtureConfirmationAndImportedTarget() {
        composeRule.setContent {
            var target by remember { mutableStateOf<String?>(null) }
            MusicMateTheme {
                CarCalibrationScreen(CalibrationWizard.Step.Checklist, true, {}, {}, {}, {},
                    output = CalibrationOutput.HEADPHONE,
                    microphones = listOf(SweepRecorder.Microphone(7, "USB measurement microphone")),
                    targetName = target, onImportTarget = { target = "My fixture target.csv" })
            }
        }
        composeRule.onNodeWithText("Start", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Fixture / coupler name").performScrollTo().performTextInput("IEC coupler")
        composeRule.onNodeWithText("Import fixture target").performScrollTo().performClick()
        composeRule.onNodeWithText("Start", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Confirm fixture and matching target").performScrollTo().performClick()
        composeRule.onNodeWithText("USB measurement microphone").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Start").assertIsDisplayed()
    }

    @Test fun speakerPreparationKeepsExistingStartWithoutFixtureFields() {
        composeRule.setContent {
            MusicMateTheme { CarCalibrationScreen(CalibrationWizard.Step.Checklist, true, {}, {}, {}, {}) }
        }
        composeRule.onNodeWithText("Fixture / coupler name").assertDoesNotExist()
        composeRule.onNodeWithText("Start").assertIsDisplayed()
    }
}
