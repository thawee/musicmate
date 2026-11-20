package apincer.android.mmate.audio.dsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import java.util.Random

class TruePeakLimiterTest {
    @Test
    fun independentReconstructionStaysBelowMinusOneDbtp() {
        var largest = 0.0
        val failures = mutableListOf<String>()
        for (rate in intArrayOf(44_100, 48_000, 96_000, 192_000)) {
            val length = (rate * 0.025).toInt()
            val signals = mutableListOf<DoubleArray>()
            for (position in intArrayOf(0, 1, length / 2, length - 2, length - 1)) {
                signals += DoubleArray(length).also { it[position] = 2.0 }
            }
            for (frequency in doubleArrayOf(0.25, 0.45, 0.49)) {
                for (phase in doubleArrayOf(0.0, PI / 4, PI / 2)) {
                    signals += DoubleArray(length) { 1.4 * sin(2 * PI * frequency * it + phase) }
                }
            }
            signals += DoubleArray(length) { if (it % 4 < 2) 1.2 else -1.2 }
            signals += DoubleArray(length) {
                val level = if ((it / 73) % 3 == 0) 0.02 else 1.8
                level * (sin(it * 0.37) + 0.7 * sin(it * 1.71) + 0.5 * sin(it * 3.01))
            }
            val noise = Random(1770)
            signals += DoubleArray(length) {
                val level = if ((it / 101) % 2 == 0) 0.02 else 3.0
                level * (noise.nextDouble() * 2 - 1)
            }
            for ((index, input) in signals.withIndex()) {
                val output = render(rate, 1, input)
                val peak = reconstructedPeak(output, 1)
                largest = maxOf(largest, peak)
                if (peak > CEILING + 1e-6) failures += "$rate Hz signal $index: ${20 * log10(peak)} dBTP"
            }
        }
        println("Independent 16x/192-tap output reconstruction maximum: ${20 * log10(largest)} dBTP")
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }

    @Test
    fun quietAudioIsBitExactWithoutLeadingZerosOrLostTail() {
        for (rate in intArrayOf(44_100, 48_000, 96_000, 192_000)) {
            for (length in intArrayOf(0, 1, 7, rate / 100, rate / 50)) {
                val input = DoubleArray(length * 2) { 0.1 + 0.05 * sin(it * 0.37) }
                assertArrayEquals(input, render(rate, 2, input), 0.0)
            }
        }
    }

    @Test
    fun channelsShareGainAndReleaseSmoothly() {
        val rate = 48_000
        val input = DoubleArray(rate / 5 * 2) { index ->
            val frame = index / 2
            val left = if (frame < rate / 50) 1.8 else 0.1
            if (index % 2 == 0) left else left * -0.25
        }
        val output = render(rate, 2, input)
        var previousGain = 0.0
        for (frame in 0 until input.size / 2) {
            assertEquals(output[frame * 2] * -0.25, output[frame * 2 + 1], 0.0)
            val gain = output[frame * 2] / input[frame * 2]
            if (frame > rate / 20) {
                assertTrue(gain >= previousGain - 1e-12)
                assertTrue(gain - previousGain < 0.001)
            }
            previousGain = gain
        }
        assertTrue(previousGain > 0.95)
    }

    @Test
    fun streamingChunkBoundariesAndAliasedBuffersDoNotChangeOutput() {
        val input = DoubleArray(8192) { 1.3 * sin(it * 0.77) }
        val whole = render(48_000, 2, input)
        assertArrayEquals(whole, render(48_000, 2, input, intArrayOf(1, 7, 64, 3, 1024), true), 0.0)
    }

    @Test
    fun queuedBypassFlagsApplyToTheirOriginalFrames() {
        val limiter = TruePeakLimiter(48_000, 1)
        val frame = doubleArrayOf(2.0)
        val output = DoubleArray(1)
        var emitted = 0
        repeat(1000) { index ->
            val enabled = index % 3 != 0
            if (limiter.process(frame, output, enabled)) {
                if (emitted % 3 == 0) assertEquals(2.0, output[0], 0.0)
                else assertTrue(output[0] < CEILING)
                emitted++
            }
        }
        while (limiter.drain(output)) {
            if (emitted % 3 == 0) assertEquals(2.0, output[0], 0.0)
            else assertTrue(output[0] < CEILING)
            emitted++
        }
        assertEquals(1000, emitted)
    }

    @Test
    fun gainStartsFallingBeforeAnAttackWithoutASingleFrameJump() {
        val input = DoubleArray(2000) { if (it < 1000) 0.2 else 2.0 }
        val output = render(48_000, 1, input)
        assertEquals(1.0, output[500] / input[500], 0.0)
        assertTrue(output[900] / input[900] < 0.9)
        var previous = output[500] / input[500]
        for (index in 501 until 1500) {
            val gain = output[index] / input[index]
            assertTrue("Gain discontinuity at $index", abs(gain - previous) < 0.025)
            previous = gain
        }
    }

    @Test
    fun resetRestoresFreshStateAndDrainingStopsAtOriginalLength() {
        val limiter = TruePeakLimiter(48_000, 1)
        val frame = doubleArrayOf(3.0)
        val output = DoubleArray(1)
        repeat(500) { limiter.process(frame, output) }
        assertTrue(limiter.gainReduction > 0.0)
        limiter.reset()
        assertEquals(0, limiter.pendingFrames)
        assertEquals(0.0, limiter.gainReduction, 0.0)
        repeat(limiter.latencyFrames) {
            assertFalse(limiter.process(doubleArrayOf(0.2), output))
        }
        assertEquals(limiter.latencyFrames, limiter.pendingFrames)
        assertTrue(limiter.process(doubleArrayOf(0.2), output))
        assertEquals(0.2, output[0], 0.0)
        var count = 0
        while (limiter.drain(output)) {
            assertEquals(0.2, output[0], 0.0)
            count++
        }
        assertEquals(limiter.latencyFrames, count)
        assertEquals(0, limiter.pendingFrames)
        assertFalse(limiter.drain(output))
    }

    private fun render(
        rate: Int,
        channels: Int,
        input: DoubleArray,
        chunks: IntArray = intArrayOf(Int.MAX_VALUE),
        inPlace: Boolean = false
    ): DoubleArray {
        val limiter = TruePeakLimiter(rate, channels)
        val result = DoubleArray(input.size)
        val frame = DoubleArray(channels)
        val output = if (inPlace) frame else DoubleArray(channels)
        var consumed = 0
        var emitted = 0
        var chunk = 0
        while (consumed < input.size / channels) {
            val count = minOf(chunks[chunk++ % chunks.size], input.size / channels - consumed)
            repeat(count) {
                input.copyInto(frame, 0, consumed * channels, (consumed + 1) * channels)
                consumed++
                if (limiter.process(frame, output)) {
                    output.copyInto(result, emitted * channels)
                    emitted++
                }
            }
        }
        while (limiter.drain(output)) {
            assertTrue("Emitted extra frames", emitted < input.size / channels)
            output.copyInto(result, emitted * channels)
            emitted++
        }
        assertEquals(input.size / channels, emitted)
        return result
    }

    /** Independent Blackman-windowed sinc, 16 phases and 192 taps, including zero-padded edges. */
    private fun reconstructedPeak(input: DoubleArray, channels: Int): Double {
        var peak = 0.0
        val length = input.size / channels
        for (frame in -96 until length + 96) {
            for (phase in 0 until 16) {
                for (channel in 0 until channels) {
                    var value = 0.0
                    for (tap in 0 until 192) {
                        val index = frame + tap - 95
                        if (index !in 0 until length) continue
                        value += input[index * channels + channel] * RECONSTRUCTION[phase][tap]
                    }
                    peak = maxOf(peak, abs(value))
                }
            }
        }
        return peak
    }

    private companion object {
        val CEILING = 10.0.pow(-1.0 / 20)
        val RECONSTRUCTION = Array(16) { phase ->
            DoubleArray(192) { tap ->
                val distance = tap - 95 - phase / 16.0
                val sinc = if (abs(distance) < 1e-12) 1.0 else sin(PI * distance) / (PI * distance)
                val window = if (abs(distance) >= 96) 0.0 else
                    0.42 + 0.5 * cos(PI * distance / 96) + 0.08 * cos(2 * PI * distance / 96)
                sinc * window
            }
        }
    }
}
