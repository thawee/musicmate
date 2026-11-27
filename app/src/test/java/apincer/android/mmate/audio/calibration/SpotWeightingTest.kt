package apincer.android.mmate.audio.calibration

import apincer.android.mmate.audio.dsp.PeqBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10

class SpotWeightingTest {
    private val grid = FrequencyGrid.HZ
    private val dip = PeqFitter.response(listOf(PeqBand(PeqBand.Type.PEAK, 1_000.0, -8.0, 1.5)))
    private val flat = CarTarget.CURVE.copyOf()
    private val dipped = DoubleArray(grid.size) { flat[it] + dip[it] }
    // Shallower than the 6 dB null rule, so only the spread decides whether it is boosted
    private val shared = PeqFitter.response(listOf(PeqBand(PeqBand.Type.PEAK, 1_000.0, -5.0, 1.0)))

    private fun at(hz: Double) = grid.indices.minBy { abs(grid[it] - hz) }

    private fun boostAt1k(spots: List<DoubleArray>, useSpread: Boolean = true): Double {
        val fit = PeqFitter.fit(TakeAnalyzer.powerAverage(spots, CalibrationWizard.spotWeights(spots.size, centreFirst = true)),
            spreadDb = if (useSpread) TakeAnalyzer.spreadDb(spots) else null)
        return PeqFitter.response(fit.bands)[at(1_000.0)]
    }

    @Test
    fun theCentreCountsForHalfAndHeadphoneSeatingsCountEqually() {
        assertEquals(listOf(0.5, 0.125, 0.125, 0.125, 0.125), CalibrationWizard.spotWeights(5, centreFirst = true))
        assertEquals(List(5) { 0.2 }, CalibrationWizard.spotWeights(5, centreFirst = false))
        val centre = DoubleArray(grid.size) { 0.0 }
        val others = DoubleArray(grid.size) { -10.0 }
        val average = TakeAnalyzer.powerAverage(listOf(centre, others, others, others, others), CalibrationWizard.spotWeights(5, true))
        assertEquals(10 * log10(0.5 + 0.5 * 0.1), average[0], 1e-9)
    }

    @Test
    fun aSpotThatIsLouderEverywhereIsNotDisagreement() {
        val spread = TakeAnalyzer.spreadDb(listOf(dipped, DoubleArray(grid.size) { dipped[it] + 4.0 }, dipped))
        assertTrue("spread ${spread.max()} dB", spread.all { it < 1e-9 })
    }

    @Test
    fun aDipAtOneSpotIsNotFilled() {
        val centreOnly = listOf(dipped, flat, flat, flat, flat)
        // Without the spread the weighted average still shows a 2.4 dB dip, and the fit boosts it
        val unguarded = boostAt1k(centreOnly, useSpread = false)
        assertTrue("boost without spread $unguarded dB", unguarded > 1.0)
        val cap = PeqFitter.MAX_BOOST_DB * PeqFitter.spreadScale(TakeAnalyzer.spreadDb(centreOnly)[at(1_000.0)])
        assertTrue("boost ${boostAt1k(centreOnly)} dB over the $cap dB spread cap", boostAt1k(centreOnly) <= cap + 0.01)
        assertTrue("boost ${boostAt1k(centreOnly)} dB", boostAt1k(centreOnly) < unguarded / 2)
        val sideOnly = listOf(flat, dipped, flat, flat, flat)
        assertTrue("boost ${boostAt1k(sideOnly)} dB", boostAt1k(sideOnly) < 0.3)
    }

    @Test
    fun aDipEverySpotHearsIsStillBoosted() {
        val spots = List(5) { DoubleArray(grid.size) { flat[it] + shared[it] } }
        assertEquals(boostAt1k(spots, useSpread = false), boostAt1k(spots), 0.0)
        assertTrue("boost ${boostAt1k(spots)} dB", boostAt1k(spots) > 2.0)
    }

    @Test
    fun noiseInAnExcludedBandDoesNotLowerTheBoostBesideIt() {
        val noiseHz = 1_600.0
        val spots = List(5) { spot ->
            DoubleArray(grid.size) { g ->
                val noisy = abs(grid[g] / noiseHz - 1) < 0.15 && spot > 0
                flat[g] + shared[g] + if (noisy) (if ((spot + g) % 2 == 0) 6.0 else -6.0) else 0.0
            }
        }
        val spread = TakeAnalyzer.spreadDb(spots)
        assertTrue("spread ${spread[at(noiseHz)]} dB", spread[at(noiseHz)] > PeqFitter.SPREAD_NO_BOOST_DB)
        val average = TakeAnalyzer.powerAverage(spots, CalibrationWizard.spotWeights(spots.size, centreFirst = true))
        fun boost(spreadDb: DoubleArray?) =
            PeqFitter.response(PeqFitter.fit(average, excludedBandsHz = listOf(noiseHz), spreadDb = spreadDb).bands)[at(1_000.0)]
        assertTrue("boost ${boost(null)} dB", boost(null) > 1.0)
        assertEquals(boost(null), boost(spread), 1e-9)
    }
}
