package apincer.android.mmate.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import apincer.android.mmate.audio.calibration.CorrectionProfiles
import apincer.android.mmate.audio.calibration.CalibrationOutput
import apincer.android.mmate.audio.dsp.SoundMode
import apincer.android.mmate.audio.dsp.CarDspPreferences
import androidx.test.platform.app.InstrumentationRegistry
import apincer.android.mmate.audio.headphones.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QuickTuningUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val headphone = HeadphoneProfile.createManual("My headphones", ManualHeadphoneTuning())
    private val speaker = HeadphoneProfile.createManual("Living room", ManualHeadphoneTuning(), CalibrationOutput.SPEAKER)

    @Test fun selectedCustomWhileBypassedHasAnEditShortcut() {
        var full = false
        compose.setContent {
            MusicMateTheme {
                AudioTuningSheet({}, compact = true) {
                    QuickTuningControls(
                        HeadphoneStatus(HeadphoneEndpoint("usb:test", "USB DAC", true),
                            HeadphoneBinding(headphone.id, soundMode = SoundMode.CUSTOM), headphone),
                        CorrectionProfiles.choices(emptyList(), listOf(headphone)),
                        onSelect = {}, onEnabled = {}, onSoundMode = {}, onFullTuning = { full = true })
                }
            }
        }
        compose.onNodeWithText("Bypassed").assertIsDisplayed()
        compose.onNodeWithText("Custom").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Edit Custom").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(full) }
    }

    @Test fun disconnectedOutputExplainsUnavailableProcessing() {
        compose.setContent {
            MusicMateTheme {
                AudioTuningSheet({}, compact = true) {
                    QuickTuningControls(HeadphoneStatus(), emptyList(), onSelect = {}, onEnabled = {},
                        onSoundMode = {}, onFullTuning = {})
                }
            }
        }
        compose.onNodeWithText("Unavailable").assertIsDisplayed()
        compose.onNodeWithText("Connect a compatible output").assertIsDisplayed()
        compose.onNodeWithContentDescription("Enable output EQ").assertIsNotEnabled()
        compose.onNodeWithText("Create profile").assertDoesNotExist()
    }

    @Test fun speakerModeSelectionPreservesBypass() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = CarDspPreferences.state.value
        try {
            CarDspPreferences.update(context, original.copy(enabled = false, mode = SoundMode.REFERENCE))
            compose.setContent { MusicMateTheme { LegacySpeakerSoundModes(compact = true) } }
            compose.onNodeWithText("Vocal").performClick()
            compose.runOnIdle {
                assertEquals(SoundMode.VOCAL, CarDspPreferences.state.value.mode)
                assertFalse("Selecting a mode must preserve bypass", CarDspPreferences.state.value.enabled)
            }
        } finally {
            CarDspPreferences.update(context, original)
        }
    }

    @Test fun bypassPreservesSelectedProfileAndMode() {
        var enabled: Boolean? = null
        var selectionChanged = false
        compose.setContent {
            MusicMateTheme {
                AudioTuningSheet({}, compact = true) {
                    QuickTuningControls(
                        HeadphoneStatus(HeadphoneEndpoint("wired:test", "Wired headphones", false, wired = true),
                            HeadphoneBinding(headphone.id, true, soundMode = SoundMode.VOCAL), headphone, true),
                        CorrectionProfiles.choices(emptyList(), listOf(headphone)),
                        onSelect = { selectionChanged = true }, onEnabled = { enabled = it }, onSoundMode = {},
                        onFullTuning = {})
                }
            }
        }
        compose.onNode(isToggleable()).performClick()
        compose.runOnIdle { assertEquals(false, enabled); assertFalse(selectionChanged) }
        compose.onNodeWithText("My headphones").assertIsDisplayed()
        compose.onNodeWithText("Vocal").assertIsSelected()
    }

    @Test fun playbackChipOpensQuickPanelAndCanExpandToFullEditor() {
        compose.setContent { MusicMateTheme { AudioTuningChip() } }
        compose.onNodeWithContentDescription("Audio tuning").performClick()
        compose.onNodeWithText("Quick tuning").assertIsDisplayed()
        compose.onNodeWithText("Manage tuning").performClick()
        compose.onNodeWithText("Audio tuning").assertIsDisplayed()
        compose.onNodeWithText("Calibrate your sound").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Close audio tuning").performClick()
        compose.onNodeWithContentDescription("Audio tuning").assertIsDisplayed()
    }

    @Test fun managementOpensListeningDraftAndCancelReturnsToEditor() {
        compose.setContent { MusicMateTheme { QuickAudioTuningDialog {} } }
        compose.onNodeWithText("Create profile").assertDoesNotExist()
        compose.onNodeWithText("Manage tuning").performClick()
        compose.onNodeWithText("Listening").performScrollTo().performClick()
        compose.onNodeWithText("Speaker name").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Audio tuning").assertIsDisplayed()
        compose.onNodeWithText("Calibrate your sound").performScrollTo().assertIsDisplayed()
    }

    @Test fun usbSelectsEitherProfileAndModeWithoutGrantingConsent() {
        var selected: String? = null
        var mode: SoundMode? = null
        var enabled: Boolean? = null
        var full = false
        compose.setContent {
            MusicMateTheme {
                AudioTuningSheet({}, compact = true, onManage = { full = true }) {
                    QuickTuningControls(
                        HeadphoneStatus(HeadphoneEndpoint("usb:test", "USB DAC", true), HeadphoneBinding(profileId = headphone.id), headphone),
                        CorrectionProfiles.choices(emptyList(), listOf(headphone, speaker)),
                        onSelect = { selected = it }, onEnabled = { enabled = it }, onSoundMode = { mode = it },
                        onFullTuning = { full = true })
                }
            }
        }
        screenshot("quick-tuning")
        compose.onNodeWithText("My headphones").performClick()
        compose.onNodeWithText("Living room").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(speaker.id, selected); assertNull(enabled) }
        compose.onNodeWithText("Vocal").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(SoundMode.VOCAL, mode); assertNull(enabled) }
        compose.onNodeWithText("Create profile").assertDoesNotExist()
        compose.onNodeWithText("Manage tuning").performClick()
        compose.runOnIdle { assertTrue(full) }
        compose.onNodeWithText("Calibrate your sound").assertDoesNotExist()
        compose.onNodeWithText("EQ CURVE").assertDoesNotExist()
    }

    @Test fun builtInSpeakerDisablesIncompatibleProfiles() {
        compose.setContent {
            MusicMateTheme {
                AudioTuningSheet({}, compact = true) {
                    QuickTuningControls(
                        HeadphoneStatus(HeadphoneEndpoint("speaker:built-in", "Phone speaker", false, speaker = true)),
                        CorrectionProfiles.choices(emptyList(), listOf(headphone, speaker)),
                        onSelect = {}, onEnabled = {}, onSoundMode = {}, onFullTuning = {})
                }
            }
        }
        compose.onNodeWithText("No correction").performClick()
        compose.onNodeWithText("My headphones").assertIsNotEnabled()
        compose.onNodeWithText("Living room").assertIsEnabled()
        compose.onNodeWithText("Living room").performClick()
        compose.onNodeWithText("Create profile").assertDoesNotExist()
    }
    private fun screenshot(name: String) {
        compose.onNodeWithText("Quick tuning").captureToImage()
        val descriptor = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/$name.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

}
