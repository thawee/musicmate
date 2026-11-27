package apincer.android.mmate.audio.calibration

import apincer.android.mmate.audio.dsp.PeqBand
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln

class CheckSweepTest {
    private val grid = FrequencyGrid.HZ
    private val speaker = PeqFitter.response(listOf(
        PeqBand(PeqBand.Type.PEAK, 1_200.0, -6.0, 3.0),
        PeqBand(PeqBand.Type.PEAK, 3_000.0, 5.0, 2.0)
    ))
    /** What only the centre spot hears: a reflection the five-spot average smooths away. */
    private val centreOnly = PeqFitter.response(listOf(PeqBand(PeqBand.Type.PEAK, 5_000.0, 6.0, 4.0)))
    private val average = DoubleArray(grid.size) { CarTarget.CURVE[it] + speaker[it] }
    private val centre = DoubleArray(grid.size) { average[it] + centreOnly[it] }
    private val fit = PeqFitter.fit(average)
    /** The preamp lowers the whole check sweep; alignment must remove it. */
    private val preampDb = -4.0

    private fun take(responseDb: DoubleArray) = Take.Accepted(responseDb, DoubleArray(TakeAnalyzer.SNR_BANDS_HZ.size) { 30.0 }, 0.5)

    private fun correctedCentre(extraDb: DoubleArray = DoubleArray(grid.size)): DoubleArray {
        val bands = PeqFitter.response(fit.bands)
        return DoubleArray(grid.size) { centre[it] + bands[it] + preampDb + extraDb[it] }
    }

    @Test
    fun aChangeThatMatchesTheBandsPassesWhereTheCentreDiffersFromTheAverage() {
        val at5k = grid.indices.minBy { abs(grid[it] - 5_000.0) }
        assertTrue("centre differs from the average by ${centre[at5k] - average[at5k]} dB", centre[at5k] - average[at5k] > 5.0)
        val verdict = PeqFitter.verify(fit, take(centre), take(correctedCentre()))
        assertNotNull(verdict)
        assertTrue("worst ${verdict!!.worstDeviationDb} dB at ${verdict.worstHz} Hz", verdict.passed)
    }

    @Test
    fun aChangeThatMisses4dBAtOneFrequencyFailsThere() {
        val miss = PeqFitter.response(listOf(PeqBand(PeqBand.Type.PEAK, 1_500.0, 4.0, 6.0)))
        val verdict = PeqFitter.verify(fit, take(centre), take(correctedCentre(miss)))
        assertNotNull(verdict)
        assertFalse("worst ${verdict!!.worstDeviationDb} dB at ${verdict.worstHz} Hz", verdict.passed)
        assertTrue("worst at ${verdict.worstHz} Hz", abs(ln(verdict.worstHz / 1_500.0) / ln(2.0)) <= 1.0 / 12)
    }
}
