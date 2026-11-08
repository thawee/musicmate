package apincer.android.mmate.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import apincer.android.mmate.audio.calibration.CorrectionProfiles
import apincer.android.mmate.audio.dsp.SoundMode
import apincer.android.mmate.audio.headphones.HeadphoneBinding
import apincer.android.mmate.audio.headphones.HeadphoneEndpoint
import apincer.android.mmate.audio.headphones.HeadphoneLibrary
import apincer.android.mmate.audio.headphones.HeadphoneProfile
import apincer.android.mmate.audio.headphones.HeadphoneStatus
import apincer.android.mmate.audio.headphones.ManualHeadphoneTuning
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ManualSoundUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val profile = HeadphoneProfile.createManual("My headphones", ManualHeadphoneTuning())

    @Test fun quickTuningSelectsFiveModesAndShowsInlineCustomControls() {
        val selections = mutableListOf<SoundMode>()
        var edits = 0
        compose.setContent {
            var mode by remember { mutableStateOf(SoundMode.REFERENCE) }
            MusicMateTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    QuickTuningControls(
                        status(mode), CorrectionProfiles.choices(emptyList(), listOf(profile)),
                        onSelect = {}, onEnabled = {}, onSoundMode = { mode = it; selections.add(it) },
                        onFullTuning = { edits++ })
                }
            }
        }
        assertNoAutomaticControls()
        compose.onNodeWithText("Edit Custom").assertDoesNotExist()
        SoundMode.selectable.forEach { mode ->
            compose.onNodeWithText(mode.label).performScrollTo().performClick().assertIsSelected()
        }
        compose.runOnIdle { assertEquals(SoundMode.selectable.toList(), selections) }
        compose.onNodeWithContentDescription("Bass adjustment").performScrollTo().assertIsEnabled()
        compose.onNodeWithContentDescription("Treble adjustment").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Edit Custom").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, edits) }
        listOf("Bass", "Relax", "Night").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.onNodeWithText(SoundMode.REFERENCE.label).performScrollTo().performClick()
        compose.onNodeWithText("Edit Custom").assertDoesNotExist()
        assertNoAutomaticControls()
    }

    @Test fun fullTuningShowsToneAdjustmentsOnlyForCustom() {
        compose.setContent {
            var mode by remember { mutableStateOf(SoundMode.REFERENCE) }
            MusicMateTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HeadphoneEqControls(HeadphoneLibrary(profiles = listOf(profile)), status(mode),
                        onSoundMode = { mode = it })
                }
            }
        }
        assertNoAutomaticControls()
        compose.onNodeWithContentDescription("Bass adjustment").assertDoesNotExist()
        compose.onNodeWithText("Custom").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Bass adjustment").performScrollTo().assertIsEnabled()
        compose.onNodeWithContentDescription("Treble adjustment").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Neutral").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Bass adjustment").assertDoesNotExist()
        compose.onNodeWithContentDescription("Treble adjustment").assertDoesNotExist()
    }

    private fun status(mode: SoundMode) = HeadphoneStatus(
        HeadphoneEndpoint("wired:test", "Wired headphones", false, wired = true),
        HeadphoneBinding(profile.id, soundMode = mode), profile)

    private fun assertNoAutomaticControls() {
        listOf("Auto", "Manual", "Auto settings", "Keep for this session", "Resume Auto").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
    }
}
