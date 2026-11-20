package apincer.android.mmate.audio.dsp

import android.media.AudioDeviceInfo
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

class HeadphoneStereoDspTest {
    private val rate = 48_000
    private val shared = listOf(peak(1_000.0, 3.0))
    private val left = listOf(peak(1_000.0, 6.0))
    private val right = listOf(peak(4_000.0, -5.0))

    @Before
    fun speakerRoute() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        val speaker = mockk<AudioDeviceInfo> { every { type } returns AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        CarDsp.updateRoute(listOf(speaker))
    }

    @After
    fun clearSettings() {
        CarDsp.target = null
        unmockkStatic(Log::class)
    }

    @Test
    fun settingsCopyEachBandList() {
        val source = shared.toMutableList()
        val leftSource = left.toMutableList()
        val rightSource = right.toMutableList()
        val monoSource = left.toMutableList()
        val settings = CarDspSettings(source, leftBands = leftSource, rightBands = rightSource, balanceDb = 2.0, monoBands = monoSource)
        source.clear()
        leftSource.clear()
        rightSource.clear()
        monoSource.clear()
        assertEquals(shared, settings.bands)
        assertEquals(left, settings.leftBands)
        assertEquals(right, settings.rightBands)
        assertEquals(left, settings.monoBands)
        assertEquals(2.0, settings.balanceDb, 0.0)
    }

    @Test
    fun balanceAttenuatesOnlyTheOppositeEar() {
        for (balance in doubleArrayOf(-6.0, 0.0, 6.0)) {
            val chain = DspChain(emptyList(), rate, 2, balanceDb = balance)
            val frame = doubleArrayOf(0.25, 0.25)
            chain.process(frame)
            assertEquals(0.25 * 10.0.pow(-balance.coerceAtLeast(0.0) / 20), frame[0], 1e-12)
            assertEquals(0.25 * 10.0.pow(balance.coerceAtMost(0.0) / 20), frame[1], 1e-12)
            assertEquals(0.0, chain.preampDb, 0.0)
        }
    }

    @Test
    fun eachEarReceivesSharedBandsAndOnlyItsOwnCorrection() {
        for (frequency in doubleArrayOf(1_000.0, 4_000.0)) {
            val chain = DspChain(shared, rate, 2, leftBands = left, rightBands = right, balanceDb = 2.0)
            val measured = toneResponse(chain, frequency, 2)
            val sharedDb = response(shared, frequency)
            assertEquals(sharedDb + response(left, frequency) + chain.preampDb - 2.0, measured[0], 0.02)
            assertEquals(sharedDb + response(right, frequency) + chain.preampDb, measured[1], 0.02)
        }
    }

    @Test
    fun commonHeadroomCoversTheGreaterCombinedEarBoostAndImportedPreamp() {
        val sharedBoost = listOf(peak(1_000.0, 4.0))
        val boost = listOf(peak(1_000.0, 5.0))
        val cut = listOf(peak(1_000.0, -2.0))
        assertEquals(-9.0, DspChain(sharedBoost, rate, 2, leftBands = boost, rightBands = cut).preampDb, 0.01)
        assertEquals(-9.0, DspChain(sharedBoost, rate, 2, leftBands = cut, rightBands = boost).preampDb, 0.01)
        val chain = DspChain(sharedBoost, rate, 2, -15.0, boost, cut)
        assertEquals(-15.0, chain.preampDb, 0.0)
        val measured = toneResponse(chain, 1_000.0, 2)
        assertEquals(-6.0, measured[0], 0.02)
        assertEquals(-13.0, measured[1], 0.02)
    }

    @Test
    fun monoAndMultichannelIgnoreEarCorrectionsAndBalance() {
        for (channels in intArrayOf(1, 6)) {
            val base = DspChain(shared, rate, channels, -4.0)
            val adjusted = DspChain(shared, rate, channels, -4.0, left, right, 6.0)
            assertEquals(base.preampDb, adjusted.preampDb, 0.0)
            repeat(2_000) { n ->
                val frame = DoubleArray(channels) { c -> 0.2 * sin((n + c * 13) * 0.37) }
                val expected = frame.copyOf()
                base.process(expected)
                adjusted.process(frame)
                assertArrayEquals(expected, frame, 0.0)
            }
        }
    }

    @Test
    fun monoCorrectionAppliesOnlyToMonoIncludingCommonHeadroom() {
        val monoCut = listOf(peak(1_000.0, -6.0))
        for (channels in intArrayOf(1, 2, 6)) {
            val chain = DspChain(shared, rate, channels, monoBands = monoCut)
            val measured = toneResponse(chain, 1_000.0, channels)
            val expected = if (channels == 1) -3.0 else 0.0
            for (gain in measured) assertEquals(expected, gain, 0.02)
            assertEquals(if (channels == 1) 0.0 else -3.0, chain.preampDb, 0.01)
        }
        val boosted = DspChain(shared, rate, 1, monoBands = left)
        assertEquals(-9.0, boosted.preampDb, 0.01)
    }

    @Test
    fun defaultStereoSettingsPreserveSharedChainOutputExactly() {
        val default = DspChain(shared, rate, 2, -4.0)
        val explicit = DspChain(shared, rate, 2, -4.0, emptyList(), emptyList(), 0.0)
        assertEquals(default.preampDb, explicit.preampDb, 0.0)
        repeat(2_000) { n ->
            val frame = doubleArrayOf(sin(n * 0.37) * 0.2, sin(n * 0.23) * 0.2)
            val expected = frame.copyOf()
            default.process(expected)
            explicit.process(frame)
            assertArrayEquals(expected, frame, 0.0)
        }
    }

    @Test
    fun processorPassesStereoSettingsOnConstructionChangeAndReset() {
        CarDsp.target = CarDspSettings(emptyList(), balanceDb = 6.0)
        val processor = CarDspProcessor(rate, 2)
        assertTrue(processor.beginBlock())
        assertEquals(-6.0, processorBalance(processor), 0.001)
        CarDsp.target = CarDspSettings(emptyList(), balanceDb = -6.0)
        assertTrue(processor.beginBlock())
        assertEquals(6.0, processorBalance(processor), 0.001)
        CarDsp.target = CarDspSettings(emptyList(), balanceDb = 3.0)
        processor.reset()
        assertEquals(-3.0, processorBalance(processor), 0.001)
    }

    private fun processorBalance(processor: CarDspProcessor): Double {
        val source = ByteBuffer.allocate(2_000 * 2 * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(2_000 * 2) { source.putFloat(0.1f) }
        source.flip()
        val output = processor.processFloatToInt24(source, ByteBuffer.allocate(0))
        val position = output.limit() - 6
        val left = pcm24(output, position)
        val right = pcm24(output, position + 3)
        return 20 * log10(left.toDouble() / right)
    }

    private fun pcm24(source: ByteBuffer, position: Int): Int =
        (source.get(position).toInt() and 0xff) or
            ((source.get(position + 1).toInt() and 0xff) shl 8) or
            (source.get(position + 2).toInt() shl 16)

    private fun toneResponse(chain: DspChain, frequency: Double, channels: Int): DoubleArray {
        val outputPower = DoubleArray(channels)
        val frame = DoubleArray(channels)
        var inputPower = 0.0
        repeat(12_000) { n ->
            val input = 0.1 * sin(2 * PI * frequency * n / rate)
            frame.fill(input)
            chain.process(frame)
            if (n >= 2_400) {
                inputPower += input * input
                for (channel in frame.indices) outputPower[channel] += frame[channel] * frame[channel]
            }
        }
        return DoubleArray(channels) { 10 * log10(outputPower[it] / inputPower) }
    }

    private fun response(bands: List<PeqBand>, frequency: Double): Double =
        bands.sumOf { Biquad.design(it, rate).magnitudeDb(frequency, rate) }

    private fun peak(frequency: Double, gain: Double) = PeqBand(PeqBand.Type.PEAK, frequency, gain, 1.0)
}
