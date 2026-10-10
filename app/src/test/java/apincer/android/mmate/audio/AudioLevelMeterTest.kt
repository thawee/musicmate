package apincer.android.mmate.audio

import androidx.media3.common.C
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioLevelMeterTest {

    @Test
    fun testRmsToDbConversion() {
        assertEquals(0f, AudioLevelMeter.rmsToDb(1.0f), 0.01f)
        assertEquals(-6.02f, AudioLevelMeter.rmsToDb(0.5f), 0.05f)
        assertEquals(-20.0f, AudioLevelMeter.rmsToDb(0.1f), 0.05f)
        assertEquals(-100f, AudioLevelMeter.rmsToDb(0.0f), 0.01f)
    }

    @Test
    fun testDbToVuNormCalibration() {
        // -34 dBFS or below is zero on the -20 dB to +3 dB VU scale
        assertEquals(0.0f, AudioLevelMeter.dbToVuNorm(-35f), 0.01f)
        assertEquals(0.0f, AudioLevelMeter.dbToVuNorm(-34f), 0.01f)

        // Standard VU calibration points
        assertEquals(0.23f, AudioLevelMeter.dbToVuNorm(-24f), 0.02f) // -10 dB mark
        assertEquals(0.57f, AudioLevelMeter.dbToVuNorm(-17f), 0.02f) // -3 dB mark
        assertEquals(0.76f, AudioLevelMeter.dbToVuNorm(-14f), 0.02f) // 0 VU mark
        assertEquals(1.00f, AudioLevelMeter.dbToVuNorm(-11f), 0.02f) // +3 dB overload mark

        // Extreme peaks cap gracefully
        assertTrue(AudioLevelMeter.dbToVuNorm(0f) <= 1.05f)
        assertTrue(AudioLevelMeter.dbToVuNorm(0f) >= 1.00f)
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
    fun floatOutputMovesTheMetersOnlyForTheMeasuredRange() {
        AudioTelemetryManager.reset()
        Thread.sleep(20)
        val buffer = ByteBuffer.allocate(4 * 2 * 64).order(ByteOrder.nativeOrder())
        repeat(64) { buffer.putFloat(0.5f).putFloat(0f) }
        buffer.position(8)

        AudioLevelMeter.measure(buffer, 8, buffer.limit(), C.ENCODING_PCM_FLOAT, 2)

        val levels = AudioTelemetryManager.levels.value
        assertTrue(levels.isRealtime)
        assertEquals(AudioLevelMeter.dbToVuNorm(AudioLevelMeter.rmsToDb(0.5f)), levels.levelL, 0.01f)
        assertEquals(0f, levels.levelR, 0.001f)
        assertEquals(8, buffer.position())
    }
}
