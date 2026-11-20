package apincer.android.mmate.audio.dsp

import android.media.AudioDeviceInfo
import android.util.Log
import apincer.android.mmate.audio.OutputGuard
import apincer.android.mmate.audio.OutputGuardState
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

class CrossfeedTest {
    private val speaker = mockk<AudioDeviceInfo> { every { type } returns AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

    @Before
    fun speakerRoute() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        CarDsp.updateRoute(listOf(speaker))
        OutputGuard.reset()
    }

    @After
    fun clear() {
        CarDsp.target = null
        OutputGuard.reset()
        unmockkStatic(Log::class)
    }

    @Test
    fun noFrequencyIsBoostedForCentredOneSidedOrAntiPhaseSines() {
        for (rate in intArrayOf(44_100, 192_000, 352_800)) {
            var frequency = 20.0
            while (frequency <= 20_000) {
                for ((left, right) in listOf(1.0 to 1.0, 1.0 to 0.0, 1.0 to -1.0)) {
                    val peaks = steadyPeaks(Crossfeed(rate)::process, rate, frequency, left, right)
                    assertTrue("$rate Hz, $frequency Hz, $left/$right: ${peaks.toList()}", peaks.all { it <= 1 + 1e-9 })
                }
                frequency *= 2.0.pow(1.0 / 3)
            }
        }
    }

    @Test
    fun bassReachesTheOtherEarAndTrebleStaysOnItsSide() {
        val bass = steadyPeaks(Crossfeed(48_000)::process, 48_000, 100.0, 1.0, 0.0)
        assertEquals(0.37, bass[1], 0.02)
        val treble = steadyPeaks(Crossfeed(48_000)::process, 48_000, 10_000.0, 1.0, 0.0)
        assertTrue(treble.toList().toString(), treble[1] < 0.05 && treble[0] > 0.78)
    }

    @Test
    fun centredBassPassesAtItsLevel() {
        val peaks = steadyPeaks(Crossfeed(48_000)::process, 48_000, 30.0, 1.0, 1.0)
        assertEquals(1.0, peaks[0], 0.005)
        assertEquals(1.0, peaks[1], 0.005)
    }

    @Test
    fun theChainCrossfeedsBeforeBalanceAndLeavesMonoAlone() {
        val plain = steadyPeaks(DspChain(emptyList(), 48_000, 2, crossfeed = true)::process, 48_000, 100.0, 1.0, 0.0)
        val balanced = steadyPeaks(DspChain(emptyList(), 48_000, 2, balanceDb = -6.0, crossfeed = true)::process,
            48_000, 100.0, 1.0, 0.0)
        assertEquals(10.0.pow(-6.0 / 20), balanced[1] / plain[1], 0.005)

        val mono = doubleArrayOf(0.5)
        DspChain(emptyList(), 48_000, 1, crossfeed = true).process(mono)
        assertEquals(0.5, mono[0], 0.0)
    }

    @Test
    fun aFullScaleAntiPhaseSquareStaysUnderTheCeilingAndLightsTheLimiter() {
        CarDsp.target = CarDspSettings(emptyList(), crossfeed = true)
        val processor = CarDspProcessor(48_000, 2)
        processor.beginBlock()
        val heard = samples24(processor.processInt24(int24(4_800) { frame, channel ->
            val level = if (frame / 20 % 2 == 0) 8_388_607 else -8_388_607
            if (channel == 0) level else -level
        }, ByteBuffer.allocateDirect(0)))
        assertTrue(heard.maxOf { abs(it) } <= 0.944 * 8_388_608 + 1)
        assertTrue(OutputGuard.state.value.limitedAtMs > 0)
        assertEquals(0L, OutputGuard.state.value.clippedAtMs)
    }

    @Test
    fun quietPlaybackLeavesTheGuardDark() {
        CarDsp.target = CarDspSettings(emptyList(), crossfeed = true)
        val processor = CarDspProcessor(48_000, 2)
        processor.beginBlock()
        processor.processInt24(int24(4_800) { frame, _ -> (sin(2 * PI * 440 * frame / 48_000) * 838_861).toInt() },
            ByteBuffer.allocateDirect(0))
        assertEquals(OutputGuardState(), OutputGuard.state.value)
    }

    private fun steadyPeaks(process: (DoubleArray) -> Unit, rate: Int, frequency: Double, left: Double, right: Double): DoubleArray {
        val frame = DoubleArray(2)
        val peaks = DoubleArray(2)
        val settle = rate / 10
        val measure = (rate / frequency * 4).toInt() + rate / 100
        for (n in 0 until settle + measure) {
            val s = sin(2 * PI * frequency * n / rate)
            frame[0] = left * s
            frame[1] = right * s
            process(frame)
            if (n >= settle) for (c in 0..1) peaks[c] = max(peaks[c], abs(frame[c]))
        }
        return peaks
    }

    private fun int24(frames: Int, sample: (Int, Int) -> Int): ByteBuffer = ByteBuffer.allocate(frames * 6).apply {
        for (frame in 0 until frames) for (channel in 0..1) {
            val value = sample(frame, channel)
            put(value.toByte()).put((value shr 8).toByte()).put((value shr 16).toByte())
        }
        flip()
    }

    private fun samples24(buffer: ByteBuffer): IntArray = IntArray(buffer.remaining() / 3) {
        val at = buffer.position() + it * 3
        (buffer.get(at).toInt() and 0xFF) or ((buffer.get(at + 1).toInt() and 0xFF) shl 8) or (buffer.get(at + 2).toInt() shl 16)
    }
}
