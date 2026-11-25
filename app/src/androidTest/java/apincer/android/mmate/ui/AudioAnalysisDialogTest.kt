package apincer.android.mmate.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import apincer.android.mmate.ui.compose.AudioAnalysisDialog
import apincer.music.core.model.AudioTag
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

class AudioAnalysisDialogTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun measuresRealFileAndKeepsFidelitySeparate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, "analysis-fixture.wav")
        writeTone(file)
        val verified = AtomicBoolean(false)
        compose.activityRule.scenario.onActivity { activity ->
            AudioAnalysisDialog.show(activity, AudioTag().apply {
                path = file.absolutePath
                title = "Analysis fixture"
                drScore = 14.0
                audioDuration = 1.0
            }, Runnable { verified.set(true) })
        }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("LUFS", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("DR 14").assertIsDisplayed()
        compose.onNodeWithText("Technical details").performClick()
        compose.onNodeWithText("dBTP", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Hide technical details").performClick()
        compose.onNodeWithText("Verify lossless source").performClick()
        compose.runOnIdle { assertTrue(verified.get()) }
    }

    private fun writeTone(file: File) {
        val sampleRate = 44100
        val dataSize = sampleRate * 2
        val wav = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + dataSize)
        wav.put("WAVEfmt ".toByteArray()).putInt(16)
        wav.putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2)
        wav.putShort(2).putShort(16)
        wav.put("data".toByteArray()).putInt(dataSize)
        repeat(sampleRate) { sample ->
            wav.putShort((8192 * sin(2 * PI * 440 * sample / sampleRate)).toInt().toShort())
        }
        file.writeBytes(wav.array())
    }

    @Test
    fun unreadableFileAllowsRetryAndClose() {
        compose.activityRule.scenario.onActivity { activity ->
            AudioAnalysisDialog.show(activity, AudioTag().apply {
                path = File(activity.cacheDir, "missing-analysis.flac").absolutePath
                title = "Missing file"
            }, Runnable {})
        }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Not measured").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Analyze audio").assertDoesNotExist()
    }
}
