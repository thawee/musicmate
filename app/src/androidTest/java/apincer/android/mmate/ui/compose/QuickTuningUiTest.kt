package apincer.android.mmate.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
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

    @Test fun badgeShowsNightWithEveryPresetOnlyWhileProcessingIsActive() {
        var mode by mutableStateOf(SoundMode.REFERENCE)
        var enabled by mutableStateOf(true)
        var unavailable by mutableStateOf(false)
        var night by mutableStateOf(true)
        compose.setContent {
            MusicMateTheme { AudioTuningBadge(mode, enabled, unavailable, night, "My headphones") {} }
        }
        SoundMode.selectable.forEach { selected ->
            compose.runOnIdle { mode = selected }
            compose.onNodeWithText("${selected.label} · On").assertIsDisplayed()
            compose.onNodeWithContentDescription("Night listening active", useUnmergedTree = true).assertIsDisplayed()
        }
        compose.runOnIdle { enabled = false }
        compose.onNodeWithText("Custom · Bypassed").assertIsDisplayed()
        compose.onNodeWithContentDescription("Night listening active", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("MateTune").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                "Bypassed. Custom is selected; correction and sound processing are not applied. Night listening saved, inactive. Crossfeed off. My headphones"))
        compose.runOnIdle { enabled = true; unavailable = true }
        compose.onNodeWithText("Custom · Unavailable").assertIsDisplayed()
        compose.onNodeWithContentDescription("Night listening active", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { unavailable = false; night = false }
        compose.onNodeWithText("Custom · On").assertIsDisplayed()
        compose.onNodeWithContentDescription("Night listening active", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun badgeKeepsCorrectionVisibleWithLargeTextAndWhileBypassed() {
        var enabled by mutableStateOf(true)
        var profile by mutableStateOf("Sony WH-1000XM5 personal listening correction")
        var clicked = false
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                    androidx.compose.ui.platform.LocalDensity.current.density, fontScale = 1.5f)) {
                MusicMateTheme {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(280.dp)) {
                        AudioTuningBadge(SoundMode.VOCAL, enabled, false, true, profile) { clicked = true }
                    }
                }
            }
        }
        compose.onNodeWithText(profile, useUnmergedTree = true).assertIsDisplayed()
        val primary = compose.onNodeWithText("Vocal · On", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val secondary = compose.onNodeWithText(profile, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Correction belongs below preset and status", secondary.top >= primary.bottom)
        compose.onNodeWithContentDescription("MateTune").performClick()
        compose.runOnIdle { assertTrue(clicked); enabled = false }
        compose.onNodeWithText("Vocal · Bypassed").assertIsDisplayed()
        compose.onNodeWithText(profile).assertIsDisplayed()
        compose.runOnIdle { profile = "No correction" }
        compose.onNodeWithText("No correction").assertIsDisplayed()
    }

    @Test fun selectedCustomWhileBypassedHasInlineControls() {
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
        compose.onNodeWithText("Enable MateTune for USB").assertIsDisplayed()
        compose.onNodeWithText("Enable USB EQ to allow sample processing").assertIsDisplayed()
        compose.onNodeWithText("Custom").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Edit Custom").assertDoesNotExist()
        compose.onNodeWithContentDescription("Bass adjustment").performScrollTo().assertIsEnabled()
        compose.onNodeWithContentDescription("Treble adjustment").performScrollTo().assertIsEnabled()
        compose.runOnIdle { assertFalse(full) }
    }

    @Test fun noCorrectionAllowsModeNightAndEnableControls() {
        var mode: SoundMode? = null
        var night = false
        var enabled = false
        compose.setContent {
            MusicMateTheme {
                AudioTuningSheet({}, compact = true) {
                    QuickTuningControls(
                        HeadphoneStatus(HeadphoneEndpoint("wired", "Wired headphones", false, wired = true)),
                        emptyList(), onSelect = {}, onEnabled = { enabled = it },
                        onSoundMode = { mode = it }, onFullTuning = {}, onNightListening = { night = it })
                }
            }
        }
        compose.onNode(isToggleable() and hasText("Enable MateTune")).assertIsEnabled().performClick()
        compose.onNodeWithText("Vocal").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Night listening").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(enabled); assertTrue(night); assertEquals(SoundMode.VOCAL, mode) }
    }

    @Test fun remotePlayerExplainsThatTuningIsNotApplied() {
        compose.setContent {
            MusicMateTheme {
                AudioTuningSheet({}, compact = true) {
                    QuickTuningControls(
                        HeadphoneStatus(HeadphoneEndpoint("wired", "Wired headphones", false, wired = true),
                            HeadphoneBinding(enabled = true, soundMode = SoundMode.CUSTOM), active = true),
                        emptyList(), onSelect = {}, onEnabled = {}, onSoundMode = {}, onFullTuning = {},
                        remoteTarget = "Living Room")
                }
            }
        }
        compose.onNodeWithText("Playing on Living Room. Tuning applies only when Music Mate plays on this phone.").assertIsDisplayed()
        compose.onNodeWithText("Not applied to the current player").assertIsDisplayed()
        compose.onNodeWithText("On").assertDoesNotExist()
        compose.onNodeWithText("Compare levels").performScrollTo().assertIsNotEnabled()
    }

    @Test fun disconnectedOutputExplainsUnavailableProcessingAndKeepsManagementReachable() {
        var manage = false
        compose.setContent {
            MusicMateTheme {
                AudioTuningSheet({}, compact = true, onManage = { manage = true }) {
                    QuickTuningControls(HeadphoneStatus(), emptyList(), onSelect = {}, onEnabled = {},
                        onSoundMode = {}, onFullTuning = { manage = true })
                }
            }
        }
        compose.onNodeWithText("Enable MateTune").assertIsDisplayed()
        compose.onNodeWithText("Connect a compatible output").assertIsDisplayed()
        compose.onNode(isToggleable() and hasText("Enable MateTune")).assertIsNotEnabled()
        compose.onNodeWithText("Create profile").assertDoesNotExist()
        compose.onNodeWithContentDescription("Manage MateTune").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(manage) }
    }

    @Test fun speakerModeSelectionPreservesBypass() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = CarDspPreferences.state.value
        try {
            CarDspPreferences.update(context, original.copy(enabled = false, mode = SoundMode.REFERENCE))
            compose.setContent { MusicMateTheme {
                androidx.compose.foundation.layout.Column { LegacySpeakerSoundModes(compact = true) }
            } }
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
        compose.onNode(isToggleable() and hasText("Enable MateTune")).performClick()
        compose.runOnIdle { assertEquals(false, enabled); assertFalse(selectionChanged) }
        compose.onNodeWithText("Vocal").assertIsSelected()
        compose.onNodeWithText("My headphones").performScrollTo().assertIsDisplayed()
    }

    @Test fun playbackChipOpensQuickPanelAndCanExpandToFullEditor() {
        compose.setContent { MusicMateTheme { AudioTuningChip() } }
        compose.onNodeWithContentDescription("MateTune").performClick()
        compose.onNodeWithText("MateTune · Quick").assertIsDisplayed()
        compose.onNodeWithContentDescription("Manage MateTune").performClick()
        compose.onAllNodesWithText("MateTune").onLast().assertIsDisplayed()
        compose.onNodeWithText("Correction").performScrollTo().performClick()
        compose.onNodeWithText("Calibrate your sound").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Close MateTune").performClick()
        compose.onNodeWithContentDescription("MateTune").assertIsDisplayed()
    }

    @Test fun manageTuningOpensListeningDraftAndCancelReturnsToFullEditor() {
        compose.setContent { MusicMateTheme { QuickAudioTuningDialog {} } }
        compose.onNodeWithText("Create profile").assertDoesNotExist()
        compose.onNodeWithContentDescription("Manage MateTune").performClick()
        compose.onNodeWithText("Correction").performScrollTo().performClick()
        compose.onNodeWithText("Listening").performScrollTo().performClick()
        compose.onNodeWithText("Create personal tuning").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onAllNodesWithText("MateTune").onLast().assertIsDisplayed()
        compose.onNodeWithText("MateTune · Quick").assertDoesNotExist()
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
        compose.onNodeWithText("Correction").performScrollTo().performClick()
        compose.onNodeWithText("CORRECTION PROFILE").performScrollTo().performClick()
        compose.onNodeWithText("Living room").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(speaker.id, selected); assertNull(enabled) }
        compose.onNodeWithText("Vocal").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(SoundMode.VOCAL, mode); assertNull(enabled) }
        compose.onNodeWithText("Create profile").assertDoesNotExist()
        compose.onNodeWithContentDescription("Manage MateTune").performClick()
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
        compose.onNodeWithText("Correction").performScrollTo().performClick()
        compose.onNodeWithText("CORRECTION PROFILE").performScrollTo().performClick()
        compose.onNodeWithText("My headphones").assertIsNotEnabled()
        compose.onNodeWithText("Living room").assertIsEnabled()
        compose.onNodeWithText("Other profiles").assertExists()
        compose.onNode(hasText("No correction") and isSelected()).assertExists()
        assertTrue("Compatible profiles should precede incompatible ones",
            compose.onNodeWithText("Living room").fetchSemanticsNode().boundsInRoot.top <
                compose.onNodeWithText("My headphones").fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithText("Living room").performClick()
        compose.onNodeWithText("Create profile").assertDoesNotExist()
    }
    @Test fun compactPresetsAndNightAreVisibleWithoutScrolling() = verifyCompactPresets(1f)

    @Test fun largeTextWrapsPresetsAndKeepsEveryModeAndNightReachable() = verifyCompactPresets(1.5f)

    private fun verifyCompactPresets(scale: Float) {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                    androidx.compose.ui.platform.LocalDensity.current.density, fontScale = scale)) {
                MusicMateTheme {
                    AudioTuningSheet({}, compact = true) {
                        QuickTuningControls(
                            HeadphoneStatus(HeadphoneEndpoint("wired", "Wired headphones", false, wired = true),
                                HeadphoneBinding(enabled = true, soundMode = SoundMode.VOCAL), active = true),
                            emptyList(), onSelect = {}, onEnabled = {}, onSoundMode = {}, onFullTuning = {})
                    }
                }
            }
        }
        compose.onNodeWithText("Vocal").assertIsSelected()
        compose.onNodeWithText("Vocal presence at 2.5 kHz · adds to your correction").assertIsDisplayed()
        compose.onNodeWithText("CORRECTION PROFILE").assertDoesNotExist()
        compose.onNodeWithText("Create profile").assertDoesNotExist()
        if (scale == 1f) {
            SoundMode.selectable.forEach { compose.onNodeWithText(it.label).assertIsDisplayed() }
            compose.onNodeWithContentDescription("Night listening").assertIsDisplayed()
            screenshot("tuning-simple")
        } else {
            val firstTop = compose.onNodeWithText(SoundMode.selectable.first().label)
                .fetchSemanticsNode().boundsInRoot.top
            val lastTop = compose.onNodeWithText(SoundMode.selectable.last().label)
                .fetchSemanticsNode().boundsInRoot.top
            assertTrue("Large text presets should wrap", lastTop > firstTop)
            SoundMode.selectable.forEach { compose.onNodeWithText(it.label).performScrollTo().assertIsDisplayed() }
            compose.onNodeWithContentDescription("Night listening").performScrollTo().assertIsDisplayed()
        }
    }

    private fun screenshot(name: String, title: String = "MateTune · Quick") {
        compose.onNodeWithText(title).captureToImage()
        val descriptor = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/$name.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

}
