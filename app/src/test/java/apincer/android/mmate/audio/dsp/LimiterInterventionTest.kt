package apincer.android.mmate.audio.dsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

class LimiterInterventionTest {
    @Test
    fun quietTonesRemainExactThroughLookaheadAndDrain() {
        for (rate in intArrayOf(44_100, 48_000, 96_000, 192_000)) {
            for (frequency in doubleArrayOf(1000.0, rate * 0.25, rate * 0.45)) {
                val input = DoubleArray(rate / 50) {
                    10.0.pow(-12.0 / 20.0) * sin(2 * PI * frequency * it / rate + PI / 4)
                }
                val measurement = render(rate, input)
                assertArrayEquals("$rate Hz, $frequency Hz tone", input, measurement.output, 0.0)
                assertEquals(0.0, measurement.maximumReductionDb, 0.0)
                assertEquals(0, measurement.attenuatedFrames)
            }
        }
    }

    @Test
    fun measureConservativeMarginAgainstHeadroomAndTransientFixtures() {
        val rate = 48_000
        val length = rate / 10
        val fixtures = linkedMapOf<String, DoubleArray>()
        for (level in doubleArrayOf(-6.0, -3.0, -2.2, -1.0, 0.0)) {
            fixtures["1kHz ${level}dBFS"] = DoubleArray(length) {
                10.0.pow(level / 20.0) * sin(2 * PI * 1000 * it / rate)
            }
        }
        fixtures["12kHz intersample"] = DoubleArray(length) { sin(PI * it / 2 + PI / 4) }
        fixtures["+6dBFS impulse"] = DoubleArray(length).also { it[length / 2] = 2.0 }
        println("Fixture | input sample dBFS | output sample dBFS | output reconstructed dBTP | max applied reduction dB | attenuated frames %")
        for ((name, input) in fixtures) {
            val measurement = render(rate, input)
            val reconstructed = reconstructedPeak(measurement.output)
            println(String.format(Locale.ROOT, "%s | %.4f | %.4f | %.4f | %.4f | %.2f",
                name, db(input.maxOf { abs(it) }), db(measurement.output.maxOf { abs(it) }),
                db(reconstructed), measurement.maximumReductionDb,
                100.0 * measurement.attenuatedFrames / length))
            assertTrue("$name reconstructed peak: ${db(reconstructed)} dBTP",
                reconstructed <= 10.0.pow(-1.0 / 20.0) + 1e-6)
            if (name == "1kHz -6.0dBFS" || name == "1kHz -3.0dBFS") {
                assertArrayEquals("$name must retain available headroom", input, measurement.output, 0.0)
            }
            if (name == "1kHz 0.0dBFS" || name == "12kHz intersample") {
                assertTrue("$name must expose the conservative margin", measurement.maximumReductionDb > 2.0)
            }
            if (name == "+6dBFS impulse") {
                assertTrue("Overload must be attenuated", measurement.maximumReductionDb > 7.0)
            }
        }
    }

    private data class Measurement(
        val output: DoubleArray,
        val maximumReductionDb: Double,
        val attenuatedFrames: Int
    )

    private fun render(rate: Int, input: DoubleArray): Measurement {
        val limiter = TruePeakLimiter(rate, 1)
        val frame = DoubleArray(1)
        val output = DoubleArray(input.size)
        var emitted = 0
        var maximumReduction = 0.0
        var attenuatedFrames = 0
        fun record() {
            assertTrue("Must not emit extra frames", emitted < output.size)
            output[emitted++] = frame[0]
            val reduction = -db(limiter.lastOutputGain)
            maximumReduction = maxOf(maximumReduction, reduction)
            if (reduction > 0.01) attenuatedFrames++
        }
        for (sample in input) {
            frame[0] = sample
            if (limiter.process(frame, frame)) record()
        }
        while (limiter.drain(frame)) record()
        assertEquals("Drain must preserve every original frame", input.size, emitted)
        assertEquals(0, limiter.pendingFrames)
        return Measurement(output, maximumReduction, attenuatedFrames)
    }

    /** Independent 16x/192-tap Blackman sinc reconstruction with zero-extended edges. */
    private fun reconstructedPeak(input: DoubleArray): Double {
        var peak = input.maxOf { abs(it) }
        for (frame in -96 until input.size + 96) {
            for (phase in RECONSTRUCTION) {
                var sample = 0.0
                for (tap in phase.indices) {
                    val index = frame + tap - 95
                    if (index in input.indices) sample += input[index] * phase[tap]
                }
                peak = maxOf(peak, abs(sample))
            }
        }
        return peak
    }

    private fun db(amplitude: Double): Double = 20 * log10(amplitude)

    private companion object {
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
