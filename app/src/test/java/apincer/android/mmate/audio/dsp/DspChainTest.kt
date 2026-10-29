package apincer.android.mmate.audio.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class DspChainTest {
    @Test
    fun headroomIncludesShelfBoostOutsideTheAudibleGrid() {
        for (rate in intArrayOf(44_100, 48_000, 96_000, 192_000)) {
            for (type in listOf(PeqBand.Type.LOW_SHELF, PeqBand.Type.HIGH_SHELF)) {
                val frequency = if (type == PeqBand.Type.LOW_SHELF) 20.0 else 8_000.0
                val chain = DspChain(listOf(PeqBand(type, frequency, 6.0, 0.707)), rate, 1)
                assertEquals("$type at $rate Hz", -6.0, chain.preampDb, 0.001)
            }
        }
    }

    @Test
    fun fullScaleUltrasonicSineDoesNotExceedUnityAfterShelfCorrection() {
        val rate = 192_000
        val chain = DspChain(listOf(PeqBand(PeqBand.Type.HIGH_SHELF, 8_000.0, 6.0, 0.707)), rate, 1)
        val frame = DoubleArray(1)
        var peak = 0.0
        repeat(rate) { n ->
            frame[0] = sin(2 * PI * 48_000 * n / rate)
            chain.process(frame)
            if (n >= rate / 2) peak = maxOf(peak, abs(frame[0]))
        }
        assertTrue("Steady sample peak $peak", peak <= 1.0)
    }

    @Test
    fun headroomBoundsResidualSubsonicShelfBoostForEitherEar() {
        val band = PeqBand(PeqBand.Type.LOW_SHELF, 20.0, 6.0, 5.0)
        for (rate in intArrayOf(44_100, 48_000, 96_000, 192_000)) {
            val filter = Biquad.design(band, rate)
            for (left in listOf(true, false)) {
                val chain = DspChain(emptyList(), rate, 2,
                    leftBands = if (left) listOf(band) else emptyList(),
                    rightBands = if (left) emptyList() else listOf(band))
                val remainingBoost = (1..2_000).maxOf { step ->
                    filter.magnitudeDb(step / 100.0, rate) + chain.preampDb
                }
                println("Subsonic shelf rate=$rate left=$left remainingBoostDb=$remainingBoost")
                // The logarithmic grid estimates the resonance; it is not a strict upper bound.
                assertTrue("Shelf resonance estimate at $rate Hz, left=$left: $remainingBoost dB",
                    remainingBoost < 0.05)
            }
        }
    }
}
