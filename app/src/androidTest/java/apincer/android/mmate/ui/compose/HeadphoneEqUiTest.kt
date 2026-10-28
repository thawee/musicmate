package apincer.android.mmate.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import apincer.android.mmate.audio.dsp.PeqBand
import apincer.android.mmate.audio.headphones.HeadphoneBinding
import apincer.android.mmate.audio.headphones.HeadphoneEndpoint
import apincer.android.mmate.audio.headphones.HeadphoneLibrary
import apincer.android.mmate.audio.headphones.HeadphoneProfile
import apincer.android.mmate.audio.headphones.HeadphoneProfileParser
import apincer.android.mmate.audio.headphones.HeadphoneStatus
import apincer.android.mmate.audio.headphones.ManualHeadphoneTuning
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeadphoneEqUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val profile = HeadphoneProfile("test", "My headphones", "AutoEq.txt", -5.0,
        listOf(PeqBand(PeqBand.Type.PEAK, 1000.0, -3.0, 1.0)))
    private val endpoint = HeadphoneEndpoint("usb:test", "USB DAC", usb = true)

    @Test
    fun usbEqStartsBypassedAndRequiresExplicitToggle() {
        composeRule.setContent {
            var enabled by remember { mutableStateOf(false) }
            MusicMateTheme {
                HeadphoneEqControls(
                    library = HeadphoneLibrary(listOf(profile)),
                    status = HeadphoneStatus(endpoint,
                        HeadphoneBinding(profile.id, enabled = enabled, usbEqAllowed = enabled), profile, enabled),
                    onEnabled = { enabled = it }
                )
            }
        }
        composeRule.onNodeWithText("USB DAC").assertIsDisplayed()
        composeRule.onNodeWithText("EQ bypassed").assertIsDisplayed()
        composeRule.onNodeWithText("Enable USB EQ (changes samples)").assertIsOff().performClick().assertIsOn()
        composeRule.onNodeWithText("EQ active").assertIsDisplayed()
        composeRule.onNodeWithText("Enable USB EQ (changes samples)").performClick().assertIsOff()
        composeRule.onNodeWithText("EQ bypassed").assertIsDisplayed()
    }

    @Test
    fun choosingFlatDisablesEqUntilAProfileIsSelected() {
        composeRule.setContent {
            var selected by remember { mutableStateOf<HeadphoneProfile?>(profile) }
            MusicMateTheme {
                HeadphoneEqControls(
                    library = HeadphoneLibrary(listOf(profile)),
                    status = HeadphoneStatus(endpoint, HeadphoneBinding(selected?.id), selected),
                    onSelect = { selected = if (it == profile.id) profile else null }
                )
            }
        }
        composeRule.onNodeWithText("My headphones").performClick()
        composeRule.onNodeWithText("Flat / bypass").performClick()
        composeRule.onNodeWithText("Enable USB EQ (changes samples)").assertIsNotEnabled().assertIsOff()
    }

    @Test
    fun pasteFlowRequiresModelNameAndImportsRealParametricText() {
        var imported: HeadphoneProfile? = null
        composeRule.setContent {
            var paste by remember { mutableStateOf(false) }
            MusicMateTheme {
                HeadphoneEqControls(library = HeadphoneLibrary(), status = HeadphoneStatus(), onPaste = { paste = true })
                if (paste) HeadphoneImportDialog(
                    draft = HeadphoneImportDraft(), busy = false, error = null,
                    onDismiss = { paste = false },
                    onImport = { text, name, source -> imported = HeadphoneProfileParser.parse(text, name, source) }
                )
            }
        }
        composeRule.onNodeWithText("Paste profile").performClick()
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
            MusicMateTheme {
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
            MusicMateTheme {
                HeadphoneEqControls(library = HeadphoneLibrary(listOf(pair)), status = HeadphoneStatus(), onDelete = { deleted = it })
            }
        }
        composeRule.onNodeWithContentDescription("Delete DIY earbuds").performClick()
        composeRule.onNodeWithText("Delete DIY earbuds?").assertIsDisplayed()
        composeRule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        composeRule.runOnIdle { assertEquals(pair.id, deleted) }
    }
}
