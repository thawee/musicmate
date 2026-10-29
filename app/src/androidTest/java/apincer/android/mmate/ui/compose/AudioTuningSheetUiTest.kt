package apincer.android.mmate.ui.compose

import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import apincer.android.mmate.audio.calibration.CalibrationOutput
import apincer.android.mmate.audio.dsp.PeqBand
import apincer.android.mmate.audio.dsp.SoundMode
import apincer.android.mmate.audio.headphones.*
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AudioTuningSheetUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val profile = HeadphoneProfile("preview", "Studio headphones", "Measured parametric correction", -4.0,
        listOf(PeqBand(PeqBand.Type.PEAK, 100.0, -3.0, 0.8), PeqBand(PeqBand.Type.PEAK, 3000.0, 2.0, 1.0)))

    @Test fun editorShowsCorrectionAndReachesCalibration() = verifyEditor(1f, "normal")
    @Test fun largeTextKeepsCalibrationAndCloseReachable() = verifyEditor(1.5f, "large")

    @Test fun quickSheetStaysAnchoredDuringBodySwipes() = verifySwipe(true)
    @Test fun fullSheetStaysAnchoredDuringBodySwipes() = verifySwipe(false)

    @Test fun creationTypeDoesNotChangeCurrentListeningControls() {
        var chosenMode: SoundMode? = null
        compose.setContent {
            MusicMateTheme {
                AudioTuningSheet({}) {
                    var output by remember { mutableStateOf(CalibrationOutput.HEADPHONE) }
                    HeadphoneEqControls(HeadphoneLibrary(listOf(profile)),
                        HeadphoneStatus(HeadphoneEndpoint("wired:test", "Wired headphones", false, wired = true),
                            HeadphoneBinding(profile.id, true), profile, true),
                        output = output, onOutput = { output = it }, onSoundMode = { chosenMode = it })
                }
            }
        }
        compose.onNodeWithText("Speaker").performScrollTo().performClick()
        compose.onNodeWithText("Vocal").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(SoundMode.VOCAL, chosenMode) }
        compose.onNodeWithText("Studio headphones").performScrollTo().assertIsDisplayed()
    }

    private fun verifySwipe(compact: Boolean) {
        val firstRowPositions = mutableListOf<Float>()
        compose.setContent {
            MusicMateTheme {
                AudioTuningSheet(onDismissRequest = {}, compact = compact) {
                    repeat(40) { index ->
                        Text("Tuning row $index", Modifier.padding(vertical = 20.dp).then(
                            if (index == 0) Modifier.onGloballyPositioned { firstRowPositions.add(it.positionInWindow().y) }
                            else Modifier))
                    }
                }
            }
        }
        val title = compose.onNodeWithText(if (compact) "Quick tuning" else "Audio tuning")
        val top = title.fetchSemanticsNode().boundsInRoot.top
        val body = compose.onNode(hasScrollAction())
        // Record movement during a complete edge swipe, including its spring-back frames.
        val firstRowTop = compose.runOnIdle { firstRowPositions.last().also { firstRowPositions.clear() } }
        body.performTouchInput { swipeDown() }
        compose.runOnIdle {
            assertTrue("Body swipe moved the sheet: $firstRowPositions",
                firstRowPositions.all { kotlin.math.abs(it - firstRowTop) <= 1f })
        }
        repeat(3) {
            body.performTouchInput { swipeUp() }
            assertEquals(top, title.fetchSemanticsNode().boundsInRoot.top, 1f)
        }
        repeat(3) {
            body.performTouchInput { swipeDown() }
            assertEquals(top, title.fetchSemanticsNode().boundsInRoot.top, 1f)
        }
        body.performScrollToNode(hasText("Tuning row 39"))
        compose.onNodeWithText("Tuning row 39").assertIsDisplayed()
        compose.onNodeWithContentDescription(if (compact) "Close quick tuning" else "Close audio tuning").assertIsDisplayed()
    }

    private fun verifyEditor(scale: Float, name: String) {
        var listening = false
        var closed = false
        compose.setContent {
            MusicMateTheme {
                val density = LocalDensity.current.density
                AudioTuningSheet(onDismissRequest = { closed = true }) {
                    CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                        var binding by remember { mutableStateOf(HeadphoneBinding(profile.id, true, soundMode = SoundMode.REFERENCE)) }
                        HeadphoneEqControls(HeadphoneLibrary(listOf(profile)),
                            HeadphoneStatus(HeadphoneEndpoint("wired:test", "Wired headphones", false, wired = true), binding, profile, true),
                            onSoundMode = { binding = binding.copy(soundMode = it) }, onManual = { listening = true })
                    }
                }
            }
        }
        compose.onNodeWithText("Studio headphones").assertIsDisplayed()
        compose.waitForIdle()
        screenshot("dsp-$name")
        compose.onNodeWithContentDescription("Combined correction and sound mode EQ curve").performScrollTo()
        screenshot("dsp-$name-response")
        compose.onNodeWithText("Bass").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Manage profiles").performScrollTo().performClick()
        compose.onNodeWithText("Delete profile").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Listening").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(listening) }
        screenshot("dsp-$name-calibration")
        compose.onNodeWithContentDescription("Close audio tuning").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

    private fun screenshot(name: String) {
        compose.onNodeWithText("Audio tuning").captureToImage()
        val result = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/$name.png")
        ParcelFileDescriptor.AutoCloseInputStream(result).use { it.readBytes() }
    }
}
