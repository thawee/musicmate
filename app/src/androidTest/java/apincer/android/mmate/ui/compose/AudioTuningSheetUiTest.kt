package apincer.android.mmate.ui.compose

import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import apincer.android.mmate.audio.calibration.CalibrationOutput
import apincer.android.mmate.audio.dsp.PeqBand
import apincer.android.mmate.audio.dsp.SoundMode
import apincer.android.mmate.audio.headphones.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AudioTuningSheetUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val profile = HeadphoneProfile("preview", "Studio headphones", "Measured parametric correction", -4.0,
        listOf(PeqBand(PeqBand.Type.PEAK, 100.0, -3.0, 0.8), PeqBand(PeqBand.Type.PEAK, 3000.0, 2.0, 1.0)))

    @Test fun editorShowsCorrectionAndReachesCalibration() = verifyEditor(1f, "normal")
    @Test fun largeTextKeepsCalibrationAndCloseReachable() = verifyEditor(1.5f, "large")

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
        compose.onNodeWithText("Profile details").performScrollTo().performClick()
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
