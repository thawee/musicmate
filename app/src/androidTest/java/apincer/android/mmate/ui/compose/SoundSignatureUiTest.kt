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

    @Test fun selectedSignatureExplainsItsSound() = verifySignatures(1f)
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
        val signatures = listOf(
            SoundMode.REFERENCE to "Balanced sound, no added tone shaping",
            SoundMode.RELAX to "Warmer, softer highs",
            SoundMode.VOCAL to "Clearer, forward voices",
            SoundMode.LIVELY to "Punchy bass, crisp detail",
            SoundMode.CUSTOM to "Your bass and treble"
        )
        signatures.forEach { (selected, description) ->
            compose.onNodeWithText(selected.label).performScrollTo().performClick().assertIsSelected()
            compose.onAllNodes(isSelected()).assertCountEquals(1)
            signatures.filter { it.first != selected }.forEach { (other, _) ->
                compose.onNodeWithText(other.label).assertIsNotSelected()
            }
            listOf(selected.label, description).forEach { expected ->
                val text = compose.onNodeWithText(expected, useUnmergedTree = true)
                    .performScrollTo().assertIsDisplayed()
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                text.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action -> action(layouts) }
                org.junit.Assert.assertTrue("Text '$expected' must have a measured layout", layouts.isNotEmpty())
                layouts.forEach { layout ->
                    val lastLine = layout.lineCount - 1
                    val metrics = "size=${layout.size}, lines=${layout.lineCount}, " +
                        "lastBottom=${layout.getLineBottom(lastLine)}, right=${layout.getLineRight(lastLine)}, widthOverflow=${layout.didOverflowWidth}"
                    org.junit.Assert.assertEquals("Measured text must match the selected label or description", expected, layout.layoutInput.text.text)
                    org.junit.Assert.assertTrue("Text '$expected' clips horizontally at ${scale}x: $metrics",
                        (0 until layout.lineCount).all { line ->
                            layout.getLineLeft(line) >= -1f && layout.getLineRight(line) <= layout.size.width + 1f
                        })
                    org.junit.Assert.assertTrue("Text '$expected' clips its last line at ${scale}x: $metrics",
                        layout.getLineBottom(lastLine) <= layout.size.height + 1f)
                    org.junit.Assert.assertFalse("Text '$expected' is ellipsized at ${scale}x: $metrics",
                        (0 until layout.lineCount).any { layout.isLineEllipsized(it) })
                }
            }
            signatures.filter { it.first != selected }.forEach { (_, otherDescription) ->
                compose.onNodeWithText(otherDescription).assertDoesNotExist()
            }
        }
    }

    @Test fun managementStaysAccessibleWhileQuickBodyScrolls() {
        compose.setContent { MusicMateTheme { QuickAudioTuningDialog {} } }
        compose.onNodeWithText("Create profile").assertDoesNotExist()
        compose.onNodeWithText("Full audio tuning").assertDoesNotExist()
        val action = compose.onNodeWithContentDescription("Manage tuning").assertIsDisplayed()
        compose.onNodeWithText("Correction").performScrollTo().performClick()
        compose.onNodeWithText("Optional headphone or speaker correction. Create and manage profiles in Manage tuning.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Create profile").assertDoesNotExist()
        compose.waitForIdle()
        val top = action.fetchSemanticsNode().boundsInRoot.top
        val body = compose.onNode(hasScrollAction())
        body.performTouchInput { swipeUp() }
        action.assertIsDisplayed()
        assertEquals(top, action.fetchSemanticsNode().boundsInRoot.top, 1f)
        body.performTouchInput { swipeDown() }
        action.assertIsDisplayed()
        assertEquals(top, action.fetchSemanticsNode().boundsInRoot.top, 1f)
        action.performClick()
        compose.onNodeWithText("Audio tuning").assertIsDisplayed()
    }
}
