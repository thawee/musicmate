package apincer.android.mmate.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.*
import org.junit.Test

class AudioLevelProcessorTest {

    @Test
    fun testRmsToDbConversion() {
        assertEquals(0f, AudioLevelProcessor.rmsToDb(1.0f), 0.01f)
        assertEquals(-6.02f, AudioLevelProcessor.rmsToDb(0.5f), 0.05f)
        assertEquals(-20.0f, AudioLevelProcessor.rmsToDb(0.1f), 0.05f)
        assertEquals(-100f, AudioLevelProcessor.rmsToDb(0.0f), 0.01f)
    }

    @Test
    fun testDbToVuNormCalibration() {
        // -34 dBFS or below is zero on the -20 dB to +3 dB VU scale
        assertEquals(0.0f, AudioLevelProcessor.dbToVuNorm(-35f), 0.01f)
        assertEquals(0.0f, AudioLevelProcessor.dbToVuNorm(-34f), 0.01f)

        // Standard VU calibration points
        assertEquals(0.23f, AudioLevelProcessor.dbToVuNorm(-24f), 0.02f) // -10 dB mark
        assertEquals(0.57f, AudioLevelProcessor.dbToVuNorm(-17f), 0.02f) // -3 dB mark
        assertEquals(0.76f, AudioLevelProcessor.dbToVuNorm(-14f), 0.02f) // 0 VU mark
        assertEquals(1.00f, AudioLevelProcessor.dbToVuNorm(-11f), 0.02f) // +3 dB overload mark

        // Extreme peaks cap gracefully
        assertTrue(AudioLevelProcessor.dbToVuNorm(0f) <= 1.05f)
        assertTrue(AudioLevelProcessor.dbToVuNorm(0f) >= 1.00f)
    }

    @Test
    fun testAudioTelemetryManagerLifecycle() {
        AudioTelemetryManager.reset()
        val initial = AudioTelemetryManager.levels.value
        assertFalse(initial.isRealtime)
        assertEquals(0f, initial.levelL, 0.001f)
        assertEquals(0f, initial.levelR, 0.001f)

        AudioTelemetryManager.updateLevels(0.76f, 0.65f, 0.85f, 0.75f)
        val updated = AudioTelemetryManager.levels.value
        assertTrue(updated.isRealtime)
        assertEquals(0.76f, updated.levelL, 0.001f)
        assertEquals(0.65f, updated.levelR, 0.001f)
        assertEquals(0.85f, updated.peakL, 0.001f)
        assertEquals(0.75f, updated.peakR, 0.001f)

        AudioTelemetryManager.reset()
        val resetState = AudioTelemetryManager.levels.value
        assertFalse(resetState.isRealtime)
        assertEquals(0f, resetState.levelL, 0.001f)
        assertEquals(0f, resetState.levelR, 0.001f)
    }

    @Test
    fun testAudioLevelProcessorConfiguresPcmFormats() {
        val processor = AudioLevelProcessor()

        val format16Bit = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        val out16 = processor.configure(format16Bit)
        assertEquals(format16Bit, out16)

        val formatFloat = AudioProcessor.AudioFormat(96000, 2, C.ENCODING_PCM_FLOAT)
        val outFloat = processor.configure(formatFloat)
        assertEquals(formatFloat, outFloat)

        val format24Bit = AudioProcessor.AudioFormat(192000, 2, C.ENCODING_PCM_24BIT)
        val out24 = processor.configure(format24Bit)
        assertEquals(format24Bit, out24)
    }
}
