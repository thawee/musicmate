package apincer.android.mmate.audio.calibration

import apincer.android.mmate.audio.dsp.Biquad
import apincer.android.mmate.audio.dsp.PeqBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class PartialCalibrationFitTest {
    private val grid = FrequencyGrid.HZ

    @Test
    fun excludedBandIncludesTheSmoothingMargin() {
        assertTrue(PeqFitter.isExcluded(1_000.0 * 2.0.pow(0.24), listOf(1_000.0)))
        assertTrue(PeqFitter.isExcluded(1_000.0 * 2.0.pow(-0.24), listOf(1_000.0)))
        assertFalse(PeqFitter.isExcluded(1_000.0 * 2.0.pow(0.26), listOf(1_000.0)))
    }

    @Test
    fun contaminatedValuesCannotChangeAlignmentNullDetectionOrFit() {
        // These overlapping masks cover the entire normal alignment interval.
        val excluded = listOf(220.0, 300.0, 410.0, 560.0, 765.0, 1_045.0, 1_430.0, 1_950.0)
        val deviation = PeqFitter.response(listOf(PeqBand(PeqBand.Type.PEAK, 4_000.0, 6.0, 2.0)))
        val measured = DoubleArray(grid.size) { CarTarget.CURVE[it] + deviation[it] }
        val clean = PeqFitter.fit(measured, excludedBandsHz = excluded)
        for (noise in listOf(-40.0, 40.0)) {
            val contaminated = DoubleArray(grid.size) {
                measured[it] + if (PeqFitter.isExcluded(grid[it], excluded)) noise else 0.0
            }
            val fit = PeqFitter.fit(contaminated, excludedBandsHz = excluded)
            assertEquals(excluded, fit.excludedBandsHz)
            val correction = PeqFitter.response(fit.bands)
            val expected = PeqFitter.response(clean.bands)
            for (g in grid.indices) assertEquals("${grid[g]} Hz, noise $noise", expected[g], correction[g], 1e-8)
        }
        val atPeak = grid.indices.minBy { abs(grid[it] - 4_000.0) }
        assertTrue("reliable peak should still be cut", PeqFitter.response(clean.bands)[atPeak] < -3.0)
    }

    @Test
    fun combinedFilterTailsStayBelowOneDecibelInsideProtectedBands() {
        val excluded = listOf(1_000.0)
        val deviation = PeqFitter.response(listOf(
            PeqBand(PeqBand.Type.PEAK, 750.0, 8.0, 1.0),
            PeqBand(PeqBand.Type.PEAK, 4_000.0, 6.0, 2.0)
        ))
        val fit = PeqFitter.fit(DoubleArray(grid.size) { CarTarget.CURVE[it] + deviation[it] }, excludedBandsHz = excluded)
        val correction = PeqFitter.response(fit.bands)
        for (g in grid.indices.filter { PeqFitter.isExcluded(grid[it], excluded) }) {
            assertEquals("desired correction at ${grid[g]} Hz", fit.measuredDb[g], fit.achievableDb[g], 1e-9)
            assertTrue("tail at ${grid[g]} Hz: ${correction[g]} dB", abs(correction[g]) <= 1.0)
        }
        val filters = fit.bands.map { Biquad.design(it, PeqFitter.SAMPLE_RATE) }
        // Check actual filter response between measurement bins and at the exact mask edges.
        for (step in -240..240) {
            val hz = 1_000.0 * 2.0.pow(step / 960.0)
            val combined = filters.sumOf { it.magnitudeDb(hz, PeqFitter.SAMPLE_RATE) }
            assertTrue("off-grid tail at $hz Hz: $combined dB", abs(combined) <= 1.0)
        }
        val atPeak = grid.indices.minBy { abs(grid[it] - 4_000.0) }
        assertTrue("reliable peak should still be cut", correction[atPeak] < -2.0)
    }

    @Test
    fun verificationIgnoresProtectedValuesInDeviationAndLevelAlignment() {
        val excluded = listOf(220.0, 300.0, 410.0, 560.0, 765.0, 1_045.0, 1_430.0, 1_950.0)
        val fit = PeqFitter.fit(CarTarget.CURVE, excludedBandsHz = excluded)
        val corrected = DoubleArray(grid.size) {
            CarTarget.CURVE[it] + 12.0 + if (PeqFitter.isExcluded(grid[it], excluded)) 40.0 else 0.0
        }
        val passed = PeqFitter.verify(fit, corrected)
        assertTrue("worst ${passed.worstDeviationDb} dB at ${passed.worstHz} Hz", passed.passed)
        val failing = corrected.copyOf()
        val peak = grid.indices.minBy { abs(grid[it] - 4_000.0) }
        failing[peak] += 5.0
        assertFalse(PeqFitter.verify(fit, failing).passed)
    }

    @Test
    fun newVerificationNoiseRequiresARetakeRatherThanChangingTheFitMask() {
        val excluded = listOf(1_000.0)
        val fit = PeqFitter.fit(CarTarget.CURVE, excludedBandsHz = excluded)
        assertNotNull(PeqFitter.verify(fit, takeWithNoiseAt(1_000.0)))
        assertNull(PeqFitter.verify(fit, takeWithNoiseAt(2_000.0)))
        assertNotNull(PeqFitter.verify(fit, takeWithNoiseAt(40.0)))
        assertNull(PeqFitter.verify(fit, takeWithNoiseAt(80.0)))
        val bassLimited = PeqFitter.fit(CarTarget.CURVE, fromHz = 100.0)
        assertNotNull(PeqFitter.verify(bassLimited, takeWithNoiseAt(80.0)))
    }

    private fun takeWithNoiseAt(frequencyHz: Double): Take.Accepted {
        val snr = DoubleArray(TakeAnalyzer.SNR_BANDS_HZ.size) { 30.0 }
        val band = snr.indices.minBy { abs(TakeAnalyzer.SNR_BANDS_HZ[it] - frequencyHz) }
        snr[band] = 0.0
        return Take.Accepted(CarTarget.CURVE.copyOf(), snr, 0.5)
    }
}
