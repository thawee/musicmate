package apincer.android.mmate.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import apincer.android.mmate.audio.calibration.CalibrationWizard
import apincer.android.mmate.audio.calibration.CarProfile
import apincer.android.mmate.audio.calibration.CarTarget
import apincer.android.mmate.audio.calibration.PeqFitter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalibrationNoiseUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun partialResultExplainsNoiseAndCanBeSaved() {
        val fit = PeqFitter.fit(CarTarget.CURVE, excludedBandsHz = listOf(157.49))
        var saved: String? = null
        composeRule.setContent {
            MusicMateTheme {
                CarCalibrationScreen(
                    step = CalibrationWizard.Step.Result(fit, "Engine on"), unprocessedMic = true,
                    onStart = {}, onMeasure = {}, onSave = { saved = it }, onClose = {}
                )
            }
        }
        composeRule.onNodeWithText("Partial calibration:", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Save and check").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("Engine on", saved) }
    }

    @Test
    fun aFailedSaveIsExplainedAndCanBeRetried() {
        val fit = PeqFitter.fit(CarTarget.CURVE)
        var saves = 0
        composeRule.setContent {
            MusicMateTheme {
                CarCalibrationScreen(
                    step = CalibrationWizard.Step.Result(fit, "Car", problem = "The correction could not be saved."),
                    unprocessedMic = true, onStart = {}, onMeasure = {}, onSave = { saves++ }, onClose = {}
                )
            }
        }
        composeRule.onNodeWithText("The correction could not be saved.").assertIsDisplayed()
        composeRule.onNodeWithText("Save and check").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, saves) }
    }

    @Test
    fun partialVerificationDoesNotClaimAllFrequenciesPassed() {
        val profile = CarProfile(
            id = "test", name = "Car", createdAtMillis = 1, bands = emptyList(), fromHz = 30.0,
            measuredDb = emptyList(), targetDb = emptyList(), predictedDb = emptyList(),
            micSource = "UNPROCESSED", approximate = false, verified = true,
            worstDeviationDb = 1.0, excludedBandsHz = listOf(157.49)
        )
        composeRule.setContent {
            MusicMateTheme {
                CarCalibrationScreen(
                    step = CalibrationWizard.Step.Done(profile), unprocessedMic = true,
                    onStart = {}, onMeasure = {}, onSave = {}, onClose = {}
                )
            }
        }
        composeRule.onNodeWithText("Reliable frequencies within ±3 dB").assertIsDisplayed()
        composeRule.onNodeWithText("Partial calibration:", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Done").assertIsDisplayed()
    }
}
