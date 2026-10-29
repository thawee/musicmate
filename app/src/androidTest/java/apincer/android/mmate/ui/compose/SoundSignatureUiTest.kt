package apincer.android.mmate.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import apincer.android.mmate.audio.dsp.SoundMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SoundSignatureUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun signaturesExplainTheirSoundBeforeSelection() = verifySignatures(1f)
    @Test fun largeTextKeepsEverySignatureReadable() = verifySignatures(1.5f)

    private fun verifySignatures(scale: Float) {
        compose.setContent {
            MusicMateTheme {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        var mode by remember { mutableStateOf(SoundMode.REFERENCE) }
                        TuningSoundModes(mode, onSelect = { mode = it })
                    }
                }
            }
        }
        listOf("Your calibration unchanged", "Deeper, punchier bass", "Clearer, forward voices",
            "Punchy bass, crisp detail", "Warmer, softer highs", "Tames loud moments", "Your bass and treble").forEach {
            val text = compose.onNodeWithText(it, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            text.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action -> action(layouts) }
            org.junit.Assert.assertTrue("Signature $it must wrap without clipping: ${layouts.map { layout -> "${layout.size}, lines=${layout.lineCount}, last=${layout.getLineBottom(layout.lineCount - 1)}, widthOverflow=${layout.didOverflowWidth}, heightOverflow=${layout.didOverflowHeight}, input=${layout.layoutInput.constraints}" }}", layouts.none { layout -> layout.hasVisualOverflow })
        }
        compose.onNodeWithText("Vocal").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Neutral").performScrollTo().assertIsNotSelected()
    }

    @Test fun managementStaysAccessibleWithoutPromotingProfileCreation() {
        compose.setContent { MusicMateTheme { QuickAudioTuningDialog {} } }
        compose.onNodeWithText("Create profile").assertDoesNotExist()
        compose.onNodeWithText("Full audio tuning").assertDoesNotExist()
        val action = compose.onNodeWithText("Manage tuning").assertIsDisplayed()
        val top = action.fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("Custom").performScrollTo()
        action.assertIsDisplayed()
        assertEquals(top, action.fetchSemanticsNode().boundsInRoot.top, 1f)
        action.performClick()
        compose.onNodeWithText("Audio tuning").assertIsDisplayed()
    }
}
