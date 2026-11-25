package apincer.android.mmate.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.performScrollTo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import apincer.android.mmate.audio.dsp.PeqBand
import apincer.android.mmate.audio.calibration.CalibrationOutput
import apincer.android.mmate.audio.calibration.CorrectionProfileChoice
import apincer.android.mmate.audio.headphones.HeadphoneBinding
import apincer.android.mmate.audio.headphones.HeadphoneEndpoint
import apincer.android.mmate.audio.headphones.HeadphoneLibrary
import apincer.android.mmate.audio.headphones.HeadphoneProfile
import apincer.android.mmate.audio.headphones.HeadphoneProfileParser
import apincer.android.mmate.audio.headphones.HeadphoneStatus
import apincer.android.mmate.audio.dsp.SoundMode
import apincer.android.mmate.audio.headphones.ManualHeadphoneTuning
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeadphoneEqUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sharedDropdownLabelsMethodsAndPreventsChoosingTheOtherOutput() {
        var selected: String? = null
        composeRule.setContent {
            TuningTestContent {
                CorrectionProfileSelector(
                    listOf(
                        CorrectionProfileChoice("speaker", "Living room", CalibrationOutput.SPEAKER, "Microphone"),
                        CorrectionProfileChoice("pair", "DIY pair", CalibrationOutput.HEADPHONE, "Listening")
                    ), null, null, CalibrationOutput.HEADPHONE, true, { selected = it }
                )
            }
        }
        composeRule.onNodeWithText("No correction").performClick()
        composeRule.onNodeWithText("Speaker · Microphone", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Living room").assertIsNotEnabled()
        composeRule.onNodeWithText("Headphone / earphone · Listening").assertIsDisplayed()
        composeRule.onNodeWithText("DIY pair").performClick()
        composeRule.runOnIdle { assertEquals("pair", selected) }
    }

    @Test
    fun speakerAutoEqExplainsMeasuredImportAndOffersAllMethods() {
        var imported = false
        composeRule.setContent {
            TuningTestContent {
                HeadphoneEqControls(HeadphoneLibrary(), HeadphoneStatus(), output = CalibrationOutput.SPEAKER,
                    onFile = { imported = true })
            }
        }
        composeRule.onNodeWithText("Correction").performScrollTo().performClick()
        composeRule.onNodeWithText("Microphone").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Listening").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("AutoEQ").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(true, imported) }
    }

    @Test
    fun soundModesKeepTheSelectedCalibrationAndEqSwitch() {
        composeRule.setContent {
            var binding by remember { mutableStateOf(HeadphoneBinding(profile.id, enabled = true, usbEqAllowed = true)) }
            TuningTestContent {
                HeadphoneEqControls(
                    library = HeadphoneLibrary(listOf(profile)),
                    status = HeadphoneStatus(endpoint, binding, profile, active = true),
                    onSoundMode = { binding = binding.copy(soundMode = it) }
                )
            }
        }
        SoundMode.selectable.forEach { mode ->
            composeRule.onNodeWithText(mode.label).performScrollTo().performClick().assertIsSelected()
        }
        composeRule.onNodeWithText("Neutral").performScrollTo().performClick().assertIsSelected()
        composeRule.onNodeWithText("My headphones").performScrollTo().assertIsDisplayed()
        composeRule.onNode(isToggleable() and hasText("Enable MateTune for USB")).assertIsOn()
    }

    private val profile = HeadphoneProfile("test", "My headphones", "AutoEq.txt", -5.0,
        listOf(PeqBand(PeqBand.Type.PEAK, 1000.0, -3.0, 1.0)))
    private val endpoint = HeadphoneEndpoint("usb:test", "USB DAC", usb = true)

    @Test
    fun usbEqStartsBypassedAndRequiresExplicitToggle() {
        composeRule.setContent {
            var enabled by remember { mutableStateOf(false) }
            TuningTestContent {
                HeadphoneEqControls(
                    library = HeadphoneLibrary(listOf(profile)),
                    status = HeadphoneStatus(endpoint,
                        HeadphoneBinding(profile.id, enabled = enabled, usbEqAllowed = enabled), profile, enabled),
                    onEnabled = { enabled = it }
                )
            }
        }
        composeRule.onNodeWithText("USB DAC").assertIsDisplayed()
        composeRule.onNodeWithText("Enable MateTune for USB").assertIsDisplayed()
        composeRule.onNodeWithText("Enable USB EQ to allow sample processing").assertIsDisplayed()
        composeRule.onNode(isToggleable() and hasText("Enable MateTune for USB")).assertIsOff().performClick().assertIsOn()
        composeRule.onNodeWithText("On").assertIsDisplayed()
        composeRule.onNode(isToggleable() and hasText("Enable MateTune for USB")).performClick().assertIsOff()
        composeRule.onNodeWithText("Enable MateTune for USB").assertIsDisplayed()
        composeRule.onNodeWithText("Enable USB EQ to allow sample processing").assertIsDisplayed()
    }

    @Test
    fun removingCorrectionKeepsEqAndSoundModesAvailable() {
        composeRule.setContent {
            var selected by remember { mutableStateOf<HeadphoneProfile?>(profile) }
            TuningTestContent {
                HeadphoneEqControls(
                    library = HeadphoneLibrary(listOf(profile)),
                    status = HeadphoneStatus(endpoint, HeadphoneBinding(selected?.id, enabled = true, usbEqAllowed = true), selected, active = true),
                    onSelect = { selected = if (it == profile.id) profile else null }
                )
            }
        }
        composeRule.onNodeWithText("Correction").performScrollTo().performClick()
        composeRule.onNodeWithText("CORRECTION PROFILE").performScrollTo().performClick()
        composeRule.onNodeWithText("No correction").performClick()
        composeRule.onNode(isToggleable() and hasText("Enable MateTune for USB")).assertIsEnabled().assertIsOn()
        composeRule.onNodeWithText("Vocal").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithContentDescription("Night listening").performScrollTo().assertIsEnabled()
    }

    @Test
    fun pasteFlowRequiresModelNameAndImportsRealParametricText() {
        var imported: HeadphoneProfile? = null
        composeRule.setContent {
            var paste by remember { mutableStateOf(false) }
            TuningTestContent {
                HeadphoneEqControls(library = HeadphoneLibrary(), status = HeadphoneStatus(), onPaste = { paste = true })
                if (paste) HeadphoneImportDialog(
                    draft = HeadphoneImportDraft(), busy = false, error = null,
                    onDismiss = { paste = false },
                    onImport = { text, name, source -> imported = HeadphoneProfileParser.parse(text, name, source) }
                )
            }
        }
        composeRule.onNodeWithText("Correction").performScrollTo().performClick()
        composeRule.onNodeWithText("Manage profiles").performScrollTo().performClick()
        composeRule.onNodeWithText("Paste profile").performScrollTo().performClick()
        composeRule.onNodeWithText("Import profile").assertIsNotEnabled()
        composeRule.onNodeWithText("Headphone model / name").performTextInput("Test headphones")
        composeRule.onNodeWithText("Parametric EQ text").performTextInput("Preamp: -5 dB\nFilter 1: ON PK Fc 1000 Hz Gain -3 dB Q 1")
        composeRule.onNodeWithText("Import profile").performClick()
        composeRule.runOnIdle {
            assertEquals("Test headphones", imported?.name)
            assertEquals(-5.0, imported!!.preampDb, 0.0)
            assertEquals(1, imported!!.bands.size)
        }
    }

    @Test
    fun malformedPasteShowsParserErrorAndPreservesTheDraft() {
        composeRule.setContent {
            var failure by remember { mutableStateOf<String?>(null) }
            TuningTestContent {
                HeadphoneImportDialog(
                    draft = HeadphoneImportDraft("Filter 1: ON UNKNOWN Fc 1000 Hz Gain -3 dB Q 1"),
                    busy = false, error = failure, onDismiss = {},
                    onImport = { text, name, source ->
                        try {
                            HeadphoneProfileParser.parse(text, name, source)
                        } catch (invalid: IllegalArgumentException) {
                            failure = invalid.message
                        }
                    }
                )
            }
        }
        composeRule.onNodeWithText("Headphone model / name").performTextInput("Test headphones")
        composeRule.onNodeWithText("Import profile").performClick()
        composeRule.onNodeWithText("unsupported active filter", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Parametric EQ text").assertTextContains("Filter 1: ON UNKNOWN Fc 1000 Hz Gain -3 dB Q 1")
    }

    @Test
    fun aSavedDiyProfileCanBeDeletedWithNoHeadphonesConnected() {
        val pair = HeadphoneProfile.createManual("DIY earbuds", ManualHeadphoneTuning())
        var deleted: String? = null
        composeRule.setContent {
            TuningTestContent {
                HeadphoneEqControls(library = HeadphoneLibrary(listOf(pair)), status = HeadphoneStatus(), onDelete = { deleted = it })
            }
        }
        composeRule.onNodeWithText("Correction").performScrollTo().performClick()
        composeRule.onNodeWithText("Manage profiles").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Delete DIY earbuds").performScrollTo().performClick()
        composeRule.onNodeWithText("Delete DIY earbuds?").assertIsDisplayed()
        composeRule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        composeRule.runOnIdle { assertEquals(pair.id, deleted) }
    }
}

@androidx.compose.runtime.Composable
internal fun TuningTestContent(content: @androidx.compose.runtime.Composable () -> Unit) {
    MusicMateTheme { Column(Modifier.verticalScroll(rememberScrollState())) { content() } }
}
