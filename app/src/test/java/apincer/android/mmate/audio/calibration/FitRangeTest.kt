package apincer.android.mmate.audio.calibration

import apincer.android.mmate.audio.dsp.PeqBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class FitRangeTest {
    private val grid = FrequencyGrid.HZ

    private fun at(hz: Double) = grid.indices.minBy { abs(grid[it] - hz) }

    @Test
    fun aBroadCutNearTheTopStaysOutOfTheTreble() {
        val hump = PeqFitter.response(listOf(PeqBand(PeqBand.Type.PEAK, 7_000.0, 6.0, 0.5)))
        val fit = PeqFitter.fit(DoubleArray(grid.size) { CarTarget.CURVE[it] + hump[it] })
        val correction = PeqFitter.response(fit.bands)
        for (hz in listOf(12_000.0, 16_000.0)) {
            assertTrue("$hz Hz: ${correction[at(hz)]} dB", abs(correction[at(hz)]) <= 1.5)
        }
        val inRange = grid.indices.filter { grid[it] in fit.fromHz..PeqFitter.MAX_HZ }
        val rms = sqrt(inRange.sumOf { (fit.predictedDb[it] - fit.achievableDb[it]).let { e -> e * e } } / inRange.size)
        // Before out-of-range points were weighted, this fit had 0.134 dB RMS in range (and -1.67 dB at 12 kHz)
        assertTrue("in-range RMS error $rms dB", rms <= 0.134 + IN_RANGE_MARGIN_DB)
    }

    @Test
    fun aVoiceMicFitOnlyCutsAndStopsAt4kHz() {
        val deviation = PeqFitter.response(listOf(
            PeqBand(PeqBand.Type.PEAK, 1_000.0, -6.0, 3.0),
            PeqBand(PeqBand.Type.PEAK, 2_500.0, 6.0, 3.0),
            PeqBand(PeqBand.Type.PEAK, 5_000.0, 6.0, 3.0),
            PeqBand(PeqBand.Type.PEAK, 7_000.0, -6.0, 3.0)
        ))
        val fit = PeqFitter.fit(DoubleArray(grid.size) { CarTarget.CURVE[it] + deviation[it] },
            toHz = PeqFitter.VOICE_MIC_MAX_HZ, maxBoostDb = 0.0)
        assertEquals(PeqFitter.VOICE_MIC_MAX_HZ, fit.toHz, 0.0)
        val correction = PeqFitter.response(fit.bands)
        for (g in grid.indices) assertTrue("${grid[g]} Hz: ${correction[g]} dB", correction[g] <= 0.01)
        assertTrue("bands ${fit.bands}", fit.bands.all { it.frequencyHz <= PeqFitter.VOICE_MIC_MAX_HZ && it.gainDb <= 0.0 })
        for (g in grid.indices) if (grid[g] >= 5_000.0) assertTrue("${grid[g]} Hz: ${correction[g]} dB", abs(correction[g]) <= 1.0)
        assertTrue("2.5 kHz peak: ${correction[at(2_500.0)]} dB", correction[at(2_500.0)] < -3.0)
    }

    private companion object {
        const val IN_RANGE_MARGIN_DB = 0.25
    }
}
