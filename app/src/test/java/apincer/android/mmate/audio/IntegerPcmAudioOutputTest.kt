package apincer.android.mmate.audio

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.AudioOutput
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class IntegerPcmAudioOutputTest {
    private fun floats(vararg values: Float): ByteBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).apply {
            values.forEach { putFloat(it) }
            flip()
        }

    private fun int24(buffer: ByteBuffer): IntArray = IntArray(buffer.remaining() / 3) { index ->
        val at = buffer.position() + index * 3
        (buffer.get(at).toInt() and 0xFF) or ((buffer.get(at + 1).toInt() and 0xFF) shl 8) or
            (buffer.get(at + 2).toInt() shl 16)
    }

    @Test
    fun twentyFourBitSamplesSurviveTheFloatRoundTrip() {
        val samples = intArrayOf(0, 1, -1, 8_388_607, -8_388_608, 1_234_567, -7_654_321)
        val source = floats(*FloatArray(samples.size) { samples[it] / 8_388_608f })
        assertArrayEquals(samples, int24(IntegerPcmAudioOutput.floatToInt24(source, ByteBuffer.allocateDirect(0))))
    }

    @Test
    fun overRangeFloatClampsInsteadOfWrapping() {
        val converted = IntegerPcmAudioOutput.floatToInt24(floats(1.0f, -1.5f, 2.0f), ByteBuffer.allocateDirect(0))
        assertArrayEquals(intArrayOf(8_388_607, -8_388_608, 8_388_607), int24(converted))
    }

    @Test
    fun partialWritesConsumeTheMatchingFloatSamples() {
        val written = ByteBuffer.allocate(64)
        val delegate = mockk<AudioOutput>(relaxed = true)
        every { delegate.write(any(), any(), any()) } answers {
            val buffer = firstArg<ByteBuffer>()
            repeat(minOf(6, buffer.remaining())) { written.put(buffer.get()) }
            !buffer.hasRemaining()
        }
        val output = IntegerPcmAudioOutput(delegate, C.ENCODING_PCM_FLOAT, 2)
        val values = floatArrayOf(0.5f, -0.5f, 0.25f, -0.25f)
        val source = floats(*values)

        assertFalse(output.write(source, 1, 0))
        assertEquals(8, source.position())
        assertTrue(output.write(source, 1, 0))
        assertFalse(source.hasRemaining())

        written.flip()
        assertArrayEquals(IntArray(values.size) { (values[it] * 8_388_608f).toInt() }, int24(written))
    }
}
