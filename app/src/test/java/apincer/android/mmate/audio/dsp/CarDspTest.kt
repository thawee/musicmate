package apincer.android.mmate.audio.dsp

import android.media.AudioDeviceInfo
import android.util.Log
import androidx.media3.common.C
import androidx.media3.exoplayer.audio.AudioOutput
import apincer.android.mmate.audio.IntegerPcmAudioOutput
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin

class CarDspTest {
    private val rates = intArrayOf(44_100, 48_000, 96_000, 192_000)
    private val presence = CarDspSettings(listOf(
        PeqBand(PeqBand.Type.LOW_SHELF, 70.0, 3.0, 0.707),
        PeqBand(PeqBand.Type.PEAK, 1_000.0, 3.0, 1.0)
    ))

    private val speaker = mockk<AudioDeviceInfo> { every { type } returns AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

    @Before
    fun speakerRoute() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        CarDsp.updateRoute(listOf(speaker))
    }

    @After
    fun clearCarDsp() {
        CarDsp.target = null
        CarDsp.updateRoute(listOf(speaker))
        unmockkStatic(Log::class)
    }

    @Test
    fun bandsReachTheirDesignGainAtEverySampleRate() {
        for (rate in rates) {
            val peak = Biquad.design(PeqBand(PeqBand.Type.PEAK, 2_500.0, 2.5, 1.0), rate)
            assertEquals(2.5, peak.magnitudeDb(2_500.0, rate), 0.1)
            val low = Biquad.design(PeqBand(PeqBand.Type.LOW_SHELF, 70.0, 3.0, 0.707), rate)
            assertEquals(3.0, low.magnitudeDb(1.0, rate), 0.1)
            val high = Biquad.design(PeqBand(PeqBand.Type.HIGH_SHELF, 8_000.0, 2.0, 0.707), rate)
            assertEquals(2.0, high.magnitudeDb(rate * 0.4999, rate), 0.1)
        }
    }

    @Test
    fun aSineThroughTheChainMatchesTheDesignedResponse() {
        for (rate in intArrayOf(48_000, 192_000)) {
            for (frequency in doubleArrayOf(200.0, 1_000.0)) {
                val chain = DspChain(presence.bands, rate, 1)
                val period = (rate / frequency).toInt()
                val frame = DoubleArray(1)
                var inPower = 0.0
                var outPower = 0.0
                val warmUp = rate
                for (n in 0 until warmUp + period * 50) {
                    val x = 0.25 * sin(2 * PI * frequency * n / rate)
                    frame[0] = x
                    chain.process(frame)
                    if (n >= warmUp) {
                        inPower += x * x
                        outPower += frame[0] * frame[0]
                    }
                }
                val measured = 10 * log10(outPower / inPower)
                assertEquals("$frequency Hz at $rate", chain.responseDb(frequency) + chain.preampDb, measured, 0.1)
            }
        }
    }

    @Test
    fun preampCancelsTheLargestBoostAndNeverAddsGain() {
        val narrow = DspChain(listOf(PeqBand(PeqBand.Type.PEAK, 1_000.0, 3.0, 10.0)), 48_000, 2)
        assertEquals(-3.0, narrow.preampDb, 0.01)
        assertTrue(DspChain(presence.bands, 48_000, 2).preampDb <= -3.0)
        val cutsOnly = DspChain(listOf(PeqBand(PeqBand.Type.PEAK, 160.0, -4.0, 1.0)), 48_000, 2)
        assertEquals(0.0, cutsOnly.preampDb, 0.0)
    }

    @Test
    fun limiterKeepsFullScaleBelowTheCeiling() {
        CarDsp.target = CarDspSettings(emptyList())
        val processor = CarDspProcessor(48_000, 2, seed = 7)
        assertTrue(processor.beginBlock())
        val source = floats(FloatArray(4_800) { if (it % 4 < 2) 1.0f else -1.0f })
        val peak = int24(processor.processFloatToInt24(source, ByteBuffer.allocate(0))).maxOf { kotlin.math.abs(it) }
        assertTrue("peak $peak", peak <= (0.944 * 8_388_608).toInt() + 1)
    }

    @Test
    fun offLeavesBothPathsUntouched() {
        val pcm16 = shorts(ShortArray(64) { (it * 997 - 30_000).toShort() })
        val passed = mutableListOf<Taken>()
        val delegate = recordingOutput(Int.MAX_VALUE, passed)
        IntegerPcmAudioOutput(delegate, C.ENCODING_PCM_16BIT, 2, CarDspProcessor(48_000, 2, seed = 1)).write(pcm16, 1, 0)
        assertSame(pcm16, passed.single().source)

        val floats = floats(FloatArray(64) { (it - 32) / 40f })
        val written = mutableListOf<Taken>()
        IntegerPcmAudioOutput(recordingOutput(Int.MAX_VALUE, written), C.ENCODING_PCM_FLOAT, 2, CarDspProcessor(48_000, 2, seed = 1))
            .write(native(floats), 1, 0)
        assertArrayEquals(bytes(IntegerPcmAudioOutput.floatToInt24(floats, ByteBuffer.allocate(0))), joined(written))
    }

    @Test
    fun otherEncodingsAreNeverProcessed() {
        CarDsp.target = presence
        val pcm24 = ByteBuffer.allocateDirect(60)
        val passed = mutableListOf<Taken>()
        IntegerPcmAudioOutput(recordingOutput(Int.MAX_VALUE, passed), C.ENCODING_PCM_24BIT, 2, CarDspProcessor(48_000, 2, seed = 1))
            .write(pcm24, 1, 0)
        assertSame(pcm24, passed.single().source)
    }

    @Test
    fun usbRouteIsNeverProcessed() {
        CarDsp.target = presence
        val dac = mockk<AudioDeviceInfo> { every { type } returns AudioDeviceInfo.TYPE_USB_DEVICE }
        CarDsp.updateRoute(listOf(dac))
        assertFalse(CarDspProcessor(48_000, 2, seed = 1).beginBlock())
        CarDsp.updateRoute(listOf(speaker))
        assertTrue(CarDspProcessor(48_000, 2, seed = 1).beginBlock())
    }

    @Test
    fun partialWritesGiveTheSameOutputAsWholeWrites() {
        CarDsp.target = presence
        for (encoding in intArrayOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)) {
            val whole = mutableListOf<Taken>()
            val partial = mutableListOf<Taken>()
            writeAll(IntegerPcmAudioOutput(recordingOutput(Int.MAX_VALUE, whole), encoding, 2, CarDspProcessor(48_000, 2, seed = 3)), music(encoding, 480))
            writeAll(IntegerPcmAudioOutput(recordingOutput(6, partial), encoding, 2, CarDspProcessor(48_000, 2, seed = 3)), music(encoding, 480))
            assertArrayEquals(joined(whole), joined(partial))
            assertNotEquals(0, joined(whole).size)
        }
    }

    @Test
    fun filterStateCarriesAcrossBuffers() {
        CarDsp.target = presence
        val one = mutableListOf<Taken>()
        val two = mutableListOf<Taken>()
        writeAll(IntegerPcmAudioOutput(recordingOutput(Int.MAX_VALUE, one), C.ENCODING_PCM_16BIT, 2, CarDspProcessor(48_000, 2, seed = 5)), music(C.ENCODING_PCM_16BIT, 960))
        val split = IntegerPcmAudioOutput(recordingOutput(Int.MAX_VALUE, two), C.ENCODING_PCM_16BIT, 2, CarDspProcessor(48_000, 2, seed = 5))
        val all = music(C.ENCODING_PCM_16BIT, 960)
        writeAll(split, native(all).apply { limit(all.capacity() / 2) })
        writeAll(split, native(all).apply { position(all.capacity() / 2) })
        assertArrayEquals(joined(one), joined(two))
    }

    @Test
    fun switchingOnFadesInToExactlyTheNewChain() {
        val frames = 48_000 / 10
        val fadeFrames = (48_000 * 0.03).toInt()
        val fading = CarDspProcessor(48_000, 1, seed = 1)
        CarDsp.target = presence
        val direct = CarDspProcessor(48_000, 1, seed = 1)
        assertTrue(fading.beginBlock())
        assertTrue(direct.beginBlock())
        val input = FloatArray(frames) { (0.2 * sin(2 * PI * 440 * it / 48_000)).toFloat() }
        val faded = int24(fading.processFloatToInt24(floats(input), ByteBuffer.allocate(0)))
        val steady = int24(direct.processFloatToInt24(floats(input), ByteBuffer.allocate(0)))
        assertNotEquals(steady[fadeFrames / 2], faded[fadeFrames / 2])
        assertArrayEquals(steady.copyOfRange(fadeFrames, frames), faded.copyOfRange(fadeFrames, frames))
    }

    @Test
    fun switchingOffReturnsToTheUntouchedPathAfterTheFade() {
        CarDsp.target = presence
        val taken = mutableListOf<Taken>()
        val output = IntegerPcmAudioOutput(recordingOutput(Int.MAX_VALUE, taken), C.ENCODING_PCM_16BIT, 2, CarDspProcessor(48_000, 2, seed = 1))
        writeAll(output, music(C.ENCODING_PCM_16BIT, 480))
        CarDsp.target = null
        repeat(4) { writeAll(output, music(C.ENCODING_PCM_16BIT, 480)) }
        val after = music(C.ENCODING_PCM_16BIT, 480)
        writeAll(output, after)
        assertSame(after, taken.last().source)
    }

    @Test
    fun aSeekRestartsLikeAFreshProcessorWithoutAFade() {
        CarDsp.target = presence
        val seeked = mutableListOf<Taken>()
        val fresh = mutableListOf<Taken>()
        val output = IntegerPcmAudioOutput(recordingOutput(Int.MAX_VALUE, seeked), C.ENCODING_PCM_FLOAT, 2, CarDspProcessor(48_000, 2, seed = 1))
        writeAll(output, music(C.ENCODING_PCM_FLOAT, 480))
        output.flush()
        seeked.clear()
        writeAll(output, music(C.ENCODING_PCM_FLOAT, 480))
        writeAll(IntegerPcmAudioOutput(recordingOutput(Int.MAX_VALUE, fresh), C.ENCODING_PCM_FLOAT, 2, CarDspProcessor(48_000, 2, seed = 1)), music(C.ENCODING_PCM_FLOAT, 480))
        assertArrayEquals(joined(fresh), joined(seeked))
    }

    private fun writeAll(output: IntegerPcmAudioOutput, buffer: ByteBuffer) {
        var guard = 0
        while (!output.write(buffer, 1, 0)) check(++guard < 10_000)
    }

    private fun music(encoding: Int, frames: Int): ByteBuffer {
        val values = DoubleArray(frames * 2) { 0.5 * sin(2 * PI * 300 * (it / 2) / 48_000) }
        return if (encoding == C.ENCODING_PCM_FLOAT) floats(FloatArray(values.size) { values[it].toFloat() })
        else shorts(ShortArray(values.size) { (values[it] * 32_767).toInt().toShort() })
    }

    /** What one write handed to the AudioTrack: the buffer itself and a copy of the bytes taken. */
    private class Taken(val source: ByteBuffer, val bytes: ByteArray)

    /** Takes at most [chunk] bytes per write. */
    private fun recordingOutput(chunk: Int, taken: MutableList<Taken>): AudioOutput = mockk(relaxed = true) {
        every { write(any(), any(), any()) } answers {
            val buffer = firstArg<ByteBuffer>()
            val copy = ByteArray(minOf(chunk, buffer.remaining()))
            buffer.get(copy)
            taken += Taken(buffer, copy)
            !buffer.hasRemaining()
        }
    }

    private fun joined(parts: List<Taken>): ByteArray = parts.fold(ByteArray(0)) { all, part -> all + part.bytes }

    /** duplicate() resets the byte order to big-endian; Media3 hands over native order. */
    @Test
    fun sleepCompressorTamesLoudPassagesAndLeavesQuietOnesAlone() {
        CarDsp.target = CarDspSettings(emptyList(), compress = true)
        // -1 dBFS is 19 dB over the -20 dBFS threshold: 2:1 takes about 9.5 dB off
        val loud = settledPeak(CarDspProcessor(48_000, 2, seed = 3), amplitude = 0.89)
        assertEquals(-10.5, 20 * log10(loud), 0.5)
        // -30 dBFS sits below the knee and passes untouched
        val quiet = settledPeak(CarDspProcessor(48_000, 2, seed = 3), amplitude = 0.0316)
        assertEquals(0.0316, quiet, 0.0005)
    }

    @Test
    fun otherModesAreNotCompressed() {
        CarDsp.target = CarDspSettings(emptyList())
        assertEquals(0.89, settledPeak(CarDspProcessor(48_000, 2, seed = 3), amplitude = 0.89), 0.005)
    }

    @Test
    fun leavingSleepReleasesTheCompressorBeforeBypassing() {
        CarDsp.target = CarDspSettings(emptyList(), compress = true)
        val processor = CarDspProcessor(48_000, 2, seed = 3)
        settledPeak(processor, amplitude = 0.89)
        CarDsp.target = null
        assertTrue("still releasing", processor.beginBlock())
        val recovered = settledPeak(processor, amplitude = 0.89, seconds = 3)
        assertEquals(0.89, recovered, 0.005)
        assertFalse("bypassed once released", processor.beginBlock())
    }

    @Test
    fun aUsbRouteEndsTheSleepReleaseAtOnce() {
        CarDsp.target = CarDspSettings(emptyList(), compress = true)
        val processor = CarDspProcessor(48_000, 2, seed = 3)
        settledPeak(processor, amplitude = 0.89)
        CarDsp.updateRoute(listOf(mockk<AudioDeviceInfo> { every { type } returns AudioDeviceInfo.TYPE_USB_DEVICE }))
        assertTrue("the 30 ms fade still runs", processor.beginBlock())
        processor.processFloatToInt24(floats(FloatArray(4_800 * 2)), ByteBuffer.allocate(0))
        assertFalse(processor.beginBlock())
    }

    @Test
    fun nonFiniteInputBecomesSilenceAndPlaybackGoesOn() {
        every { Log.w(any<String>(), any<String>()) } returns 0
        CarDsp.target = presence
        val processor = CarDspProcessor(44_100, 2, seed = 3)
        assertTrue(processor.beginBlock())
        val bad = floats(floatArrayOf(0.5f, Float.NaN, Float.POSITIVE_INFINITY, -0.5f, Float.NEGATIVE_INFINITY, 0.25f))
        val out = int24(processor.processFloatToInt24(bad, ByteBuffer.allocate(0)))
        assertEquals(6, out.size)
        // The filters keep working on the next block instead of carrying NaN forward
        assertTrue(settledPeak(processor, amplitude = 0.25) in 0.05..0.3)
        val plain = int24(IntegerPcmAudioOutput.floatToInt24(floats(floatArrayOf(Float.NaN, 0.5f)), ByteBuffer.allocate(0)))
        assertEquals(0, plain[0])
    }

    /** Plays a 1 kHz stereo sine for [seconds] and returns the output peak over the last 100 ms. */
    private fun settledPeak(processor: CarDspProcessor, amplitude: Double, seconds: Int = 1): Double {
        val rate = 48_000
        val frames = rate * seconds
        var peak = 0
        val block = 4_800
        for (start in 0 until frames step block) {
            processor.beginBlock()
            val samples = FloatArray(block * 2) { (amplitude * sin(2 * PI * 1_000 * (start + it / 2) / rate)).toFloat() }
            val out = int24(processor.processFloatToInt24(floats(samples), ByteBuffer.allocate(0)))
            if (start >= frames - rate / 10) peak = maxOf(peak, out.maxOf { kotlin.math.abs(it) })
        }
        return peak / 8_388_608.0
    }

    private fun native(buffer: ByteBuffer): ByteBuffer = buffer.duplicate().order(ByteOrder.nativeOrder())

    private fun bytes(buffer: ByteBuffer): ByteArray = ByteArray(buffer.remaining()).also { buffer.duplicate().get(it) }

    private fun floats(values: FloatArray): ByteBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).apply {
            values.forEach { putFloat(it) }
            flip()
        }

    private fun shorts(values: ShortArray): ByteBuffer =
        ByteBuffer.allocateDirect(values.size * 2).order(ByteOrder.nativeOrder()).apply {
            values.forEach { putShort(it) }
            flip()
        }

    private fun int24(buffer: ByteBuffer): IntArray = IntArray(buffer.remaining() / 3) { index ->
        val at = buffer.position() + index * 3
        (buffer.get(at).toInt() and 0xFF) or ((buffer.get(at + 1).toInt() and 0xFF) shl 8) or (buffer.get(at + 2).toInt() shl 16)
    }
}
