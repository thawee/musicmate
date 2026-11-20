package apincer.android.mmate.audio

import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.util.Log
import androidx.media3.common.C
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import apincer.android.mmate.audio.dsp.CarDsp
import apincer.android.mmate.audio.dsp.CarDspProcessor
import apincer.android.mmate.audio.dsp.CarDspSettings
import apincer.android.mmate.audio.dsp.DspChain
import apincer.android.mmate.audio.dsp.PeqBand
import apincer.android.mmate.audio.headphones.HeadphoneDsp
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

class HeadphonePcmTest {
    private var selection = HeadphoneDsp.Selection(true, null)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        mockkObject(HeadphoneDsp)
        every { HeadphoneDsp.current } answers { selection }
        CarDsp.target = null
        CarDsp.updateRoute(listOf(mockk<AudioDeviceInfo> {
            every { type } returns AudioDeviceInfo.TYPE_USB_DEVICE
            every { address } returns "test-usb"
            every { productName } returns "Test USB"
        }))
    }

    @After
    fun tearDown() {
        CarDsp.target = null
        unmockkObject(HeadphoneDsp)
        CarDsp.updateRoute(emptyList())
        unmockkStatic(Log::class)
    }

    @Test
    fun usbDefaultsRemainByteExactDespiteAnActiveCarTarget() {
        CarDsp.target = CarDspSettings(emptyList(), requestedPreampDb = -12.0)
        for (encoding in intArrayOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT)) {
            val sink = Sink(1)
            val source = pcm(encoding, 32)
            // 16-bit is widened to 24-bit, which keeps every sample exactly
            val expected = if (encoding == C.ENCODING_PCM_16BIT) {
                bytes(IntegerPcmAudioOutput.int16ToInt24(source, ByteBuffer.allocate(0)))
            } else bytes(source)
            val output = providerOutput(sink, encoding)
            writeAll(output, source)
            assertArrayEquals(expected, sink.bytes())
        }
    }

    @Test
    fun oneUsbOutputCanEnableAndBypassHighResolutionEqDuringPlayback() {
        for (encoding in intArrayOf(C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT)) {
            selection = HeadphoneDsp.Selection(true, null)
            val sink = Sink(Int.MAX_VALUE)
            val output = providerOutput(sink, encoding)
            val first = pcm(encoding, 4_800)
            writeAll(output, first)
            assertArrayEquals(bytes(pcm(encoding, 4_800)), sink.bytes())

            selection = HeadphoneDsp.Selection(true, CarDspSettings(emptyList(), requestedPreampDb = -6.0))
            sink.clear()
            writeAll(output, pcm(encoding, 4_800))
            val processed = samples(sink.bytes(), encoding)
            val scale = if (encoding == C.ENCODING_PCM_24BIT) 8_388_608.0 else 2_147_483_648.0
            assertEquals(0.25 * scale * 10.0.pow(-6.0 / 20), processed.last().toDouble(), 1.0)

            selection = HeadphoneDsp.Selection(true, null)
            sink.clear()
            val bypass = pcm(encoding, 64)
            val original = bytes(bypass)
            writeAll(output, bypass)
            assertArrayEquals(original, sink.bytes())
        }
    }

    @Test
    fun packedHighResolutionProcessingPreservesSignsAndLimitsFullScale() {
        selection = HeadphoneDsp.Selection(true, CarDspSettings(emptyList()))
        for (encoding in intArrayOf(C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT)) {
            val scale = if (encoding == C.ENCODING_PCM_24BIT) 8_388_608L else 2_147_483_648L
            // Deliberately big-endian buffer order: packed PCM remains little-endian.
            val source = packed(longArrayOf(scale / 4, -scale / 4, scale - 1, -scale), width(encoding))
            val processor = CarDspProcessor(48_000, 1)
            assertTrue(processor.beginBlock())
            val result = if (encoding == C.ENCODING_PCM_24BIT) {
                processor.processInt24(source, ByteBuffer.allocate(0))
            } else {
                processor.processInt32(source, ByteBuffer.allocate(0))
            }
            val actual = samples(bytes(result), encoding)
            assertEquals(scale / 4, actual[0])
            assertEquals(-scale / 4, actual[1])
            assertTrue(actual[2] > 0)
            assertTrue(actual[3] < 0)
            assertTrue(actual.all { abs(it) <= (0.944 * scale).roundToLong() + 1 })
            assertEquals(0, source.position())
        }
    }

    @Test
    fun partialByteWritesMatchWholeWritesAndAdvanceOnlyConsumedSamples() {
        selection = HeadphoneDsp.Selection(true, CarDspSettings(emptyList(), requestedPreampDb = -6.0))
        for (encoding in intArrayOf(C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT)) {
            val whole = Sink(Int.MAX_VALUE)
            writeAll(wrapped(whole, encoding), pcm(encoding, 64))
            val partial = Sink(1)
            val output = wrapped(partial, encoding)
            val source = pcm(encoding, 64, prefix = width(encoding))
            val start = source.position()
            val remaining = source.remaining()
            repeat(remaining) { index ->
                assertEquals(index == remaining - 1, output.write(source, 1, 0))
                assertEquals(start + (index + 1) / width(encoding) * width(encoding), source.position())
            }
            assertArrayEquals(whole.bytes(), partial.bytes())
        }
    }

    @Test
    fun floatPartialBytesDoNotLoseInputProgress() {
        val sink = Sink(1)
        val output = IntegerPcmAudioOutput(sink.output, C.ENCODING_PCM_FLOAT, 1)
        val source = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).apply {
            putFloat(0.25f).putFloat(-0.25f).flip()
        }
        repeat(6) { index ->
            assertEquals(index == 5, output.write(source, 1, 0))
            assertEquals((index + 1) / 3 * 4, source.position())
        }
        assertEquals(6, sink.bytes().size)
    }

    @Test
    fun requestedPreampCanAddAttenuationButCannotReduceAutomaticHeadroom() {
        val bands = listOf(PeqBand(PeqBand.Type.PEAK, 1_000.0, 6.0, 1.0))
        assertEquals(-12.0, DspChain(bands, 48_000, 1, requestedPreampDb = -12.0).preampDb, 0.001)
        assertEquals(-6.0, DspChain(bands, 48_000, 1, requestedPreampDb = -3.0).preampDb, 0.01)
        assertEquals(-6.0, DspChain(bands, 48_000, 1, requestedPreampDb = 3.0).preampDb, 0.01)
        assertEquals(0.0, DspChain(emptyList(), 48_000, 1, requestedPreampDb = 3.0).preampDb, 0.0)
    }

    @Test
    fun requestedPreampSurvivesOutputFlush() {
        selection = HeadphoneDsp.Selection(true, CarDspSettings(emptyList(), requestedPreampDb = -12.0))
        val sink = Sink(Int.MAX_VALUE)
        val output = wrapped(sink, C.ENCODING_PCM_32BIT)
        output.flush()
        writeAll(output, pcm(C.ENCODING_PCM_32BIT, 4))
        val expected = 536_870_912.0 * 10.0.pow(-12.0 / 20)
        samples(sink.bytes(), C.ENCODING_PCM_32BIT).forEach { assertEquals(expected, it.toDouble(), 1.0) }
    }

    private fun providerOutput(sink: Sink, encoding: Int): AudioOutput {
        val usb = mockk<UsbOutputFormat>(relaxed = true) { every { routesToUsb() } returns true }
        val base = mockk<AudioOutputProvider> { every { getAudioOutput(any()) } returns sink.output }
        return MusicAudioOutputProvider(base, usb).getAudioOutput(AudioOutputProvider.OutputConfig.Builder()
            .setEncoding(encoding).setSampleRate(48_000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setBufferSize(32_768).build())
    }

    private fun wrapped(sink: Sink, encoding: Int) =
        IntegerPcmAudioOutput(sink.output, encoding, 1, CarDspProcessor(48_000, 1))

    private fun pcm(encoding: Int, count: Int, prefix: Int = 0): ByteBuffer {
        val sample = if (encoding == C.ENCODING_PCM_32BIT) 536_870_912L else if (encoding == C.ENCODING_PCM_24BIT) 2_097_152L else 8_192L
        return ByteBuffer.allocate(prefix + count * width(encoding)).order(ByteOrder.LITTLE_ENDIAN).apply {
            position(prefix)
            repeat(count) { repeat(width(encoding)) { byte -> put((sample shr (8 * byte)).toByte()) } }
            flip()
            position(prefix)
        }
    }

    private fun packed(values: LongArray, width: Int) = ByteBuffer.allocate(values.size * width).apply {
        values.forEach { value -> repeat(width) { put((value shr (8 * it)).toByte()) } }
        flip()
    }

    private fun width(encoding: Int) = when (encoding) {
        C.ENCODING_PCM_24BIT -> 3
        C.ENCODING_PCM_32BIT -> 4
        else -> 2
    }

    private fun samples(bytes: ByteArray, encoding: Int): LongArray {
        val width = width(encoding)
        return LongArray(bytes.size / width) { sample ->
            var value = bytes[sample * width + width - 1].toLong()
            for (byte in width - 2 downTo 0) value = (value shl 8) or (bytes[sample * width + byte].toLong() and 0xff)
            value
        }
    }

    private fun bytes(buffer: ByteBuffer) = ByteArray(buffer.remaining()).also { buffer.duplicate().get(it) }

    private fun writeAll(output: AudioOutput, source: ByteBuffer) {
        // A widened block can take more one-byte writes than the source has bytes
        val maximumWrites = source.remaining() * 2 + 1
        repeat(maximumWrites) { if (output.write(source, 1, 0)) return }
        throw AssertionError("Output did not consume the block")
    }

    private class Sink(private val maximumBytes: Int) {
        private val written = ByteArrayOutputStream()
        val output = mockk<AudioOutput>(relaxed = true)
        init {
            every { output.write(any(), any(), any()) } answers {
                val source = firstArg<ByteBuffer>()
                repeat(minOf(maximumBytes, source.remaining())) { written.write(source.get().toInt()) }
                !source.hasRemaining()
            }
        }
        fun bytes(): ByteArray = written.toByteArray()
        fun clear() = written.reset()
    }
}
