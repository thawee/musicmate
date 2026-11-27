package apincer.android.mmate.audio.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StereoSweepTest {
    private val sweep = LogSweep()
    private val analyzer = TakeAnalyzer(Deconvolver(sweep))
    private val rate = sweep.sampleRate
    private val half = (rate * SweepRecorder.TOTAL_SECONDS).toInt()
    private val lead = (rate * SweepRecorder.LEAD_SECONDS).toInt()
    /** Arrival and delay count from the start of the recording. */
    private val start = lead.toDouble() / rate

    /** One side's half: each tap is (samples after the lead, gain). */
    private fun side(vararg taps: Pair<Int, Double>) = DoubleArray(half).also { out ->
        for ((late, gain) in taps) for (n in sweep.samples.indices) out[lead + late + n] += sweep.samples[n] * gain
    }

    @Test
    fun theStreamPlaysTheLeftSweepThenTheRightOneAHalfLater() {
        val pcm = StereoSweep(sweep).pcm()
        assertTrue("${pcm.size}", pcm.size > 2 * (half + sweep.length))
        val reference = sweep.toPcm16()
        val n = sweep.length / 2
        assertEquals(reference[n], pcm[2 * n])
        assertEquals(0.toShort(), pcm[2 * n + 1])
        assertEquals(0.toShort(), pcm[2 * (half + n)])
        assertEquals(reference[n], pcm[2 * (half + n) + 1])
    }

    @Test
    fun theStreamRecordsAsTwoOrdinaryTakes() {
        val take = StereoSweep(sweep).analyze(analyzer, side(0 to 1.0) + side(96 to 0.5)) as Take.Accepted
        val sides = assertNotNullAndGet(take.sides)
        // The band limit rings a few samples ahead of the peak, the same for both sides
        assertEquals(start, sides.left.arrivalSeconds, 4.0 / rate)
        assertEquals(96.0 / rate, sides.right.arrivalSeconds - sides.left.arrivalSeconds, 1.0 / rate)
        assertEquals(sides.left.arrivalSeconds, take.arrivalSeconds, 0.0)
    }

    @Test
    fun aWeakDirectSoundAheadOfAStrongReflectionStillSetsTheArrival() {
        // Off axis, the far speaker's direct sound can be a fifth of a door reflection 3 ms later
        val take = analyzer.analyze(side(0 to 0.2, 144 to 1.0)) as Take.Accepted
        assertEquals(start + 144.0 / rate, take.delaySeconds, 1.0 / rate)
        assertEquals(start, take.arrivalSeconds, 4.0 / rate)
    }

    @Test
    fun aSilentSideLeavesTheOtherSideAlone() {
        val left = side(0 to 1.0)
        val take = StereoSweep(sweep).analyze(analyzer, left + DoubleArray(half)) as Take.Accepted
        assertNull(take.sides)
        assertTrue(take.responseDb.contentEquals((analyzer.analyze(left) as Take.Accepted).responseDb))
    }

    @Test
    fun aSideThatRecordsOnlyRoomNoiseCountsAsSilent() {
        for (seed in 1L..3L) for (level in listOf(1e-4, 1e-2)) {
            val random = java.util.Random(seed)
            val noise = DoubleArray(half) { random.nextGaussian() * level }
            assertEquals("seed $seed level $level", Take.Reason.NO_RESPONSE, (analyzer.analyze(noise) as Take.Rejected).reason)
            assertNull((StereoSweep(sweep).analyze(analyzer, side(0 to 1.0) + noise) as Take.Accepted).sides)
        }
    }

    @Test
    fun theCountdownLengthMatchesTheSweep() {
        assertEquals(sweep.length.toDouble() / rate, SweepRecorder.TOTAL_SECONDS - SweepRecorder.LEAD_SECONDS - SweepRecorder.TAIL_SECONDS, 1e-9)
    }

    @Test
    fun aClippedSideIsReportedAheadOfASilentOne() {
        val clipped = side(0 to 1.0).also { it[lead] = 1.0 }
        assertEquals(Take.Reason.CLIPPED, (StereoSweep(sweep).analyze(analyzer, DoubleArray(half) + clipped) as Take.Rejected).reason)
    }

    @Test
    fun aClippedSideRejectsTheWholeSpot() {
        val take = StereoSweep(sweep).analyze(analyzer, side(0 to 1.0) + side(0 to 0.0).also { it[lead] = 1.0 })
        assertEquals(Take.Reason.CLIPPED, (take as Take.Rejected).reason)
    }

    @Test
    fun delayGoesToTheSideHeardFirstAndAGapTooWideIsDropped() {
        fun at(seconds: Double) = Take.Accepted(DoubleArray(0), DoubleArray(0), seconds, seconds)
        assertEquals(1.5 to 0.0, StereoFit.delaysMs(Sides(at(0.010), at(0.0115))).let { (l, r) -> Math.round(l * 10) / 10.0 to r })
        assertEquals(0.0 to 2.0, StereoFit.delaysMs(Sides(at(0.012), at(0.010))).let { (l, r) -> l to Math.round(r * 10) / 10.0 })
        assertEquals(0.0 to 0.0, StereoFit.delaysMs(Sides(at(0.0), at(0.007))))
    }

    @Test
    fun balanceIsTheMidrangeGapCappedAtSixDb() {
        val grid = FrequencyGrid.HZ
        val right = DoubleArray(grid.size)
        val left = DoubleArray(grid.size) { if (grid[it] in 200.0..2_000.0) 2.0 else 20.0 }
        assertEquals(2.0, StereoFit.balanceDb(left, right), 1e-9)
        assertEquals(-StereoFit.MAX_BALANCE_DB, StereoFit.balanceDb(right, DoubleArray(grid.size) { 9.0 }), 1e-9)
    }

    @Test
    fun bothSidesPlayEquallyLoudAfterTheirOwnCorrection() {
        val grid = FrequencyGrid.HZ
        // A broad mid bump on the left that its own EQ cuts, so the level before EQ overstates the gap
        val bump = PeqFitter.response(listOf(apincer.android.mmate.audio.dsp.PeqBand(apincer.android.mmate.audio.dsp.PeqBand.Type.PEAK, 600.0, 6.0, 1.0)))
        val left = DoubleArray(grid.size) { 2.0 + bump[it] }
        val right = DoubleArray(grid.size)
        fun take(db: DoubleArray) = Take.Accepted(db, DoubleArray(grid.size), 0.01)
        val spots = List(5) { Sides(take(left), take(right)) }
        val fit = StereoFit.of(spots, CalibrationWizard.spotWeights(5, centreFirst = true), emptyList()) { curves ->
            PeqFitter.fit(TakeAnalyzer.powerAverage(curves, CalibrationWizard.spotWeights(5, centreFirst = true)), targetDb = DoubleArray(grid.size))
        }
        assertTrue("left cut ${PeqFitter.response(fit.left.bands).min()} dB", PeqFitter.response(fit.left.bands).min() < -4.0)
        // What the chain plays: each side's EQ, then the louder side turned down by the balance
        val balance = fit.correction.balanceDb
        val playedLeft = DoubleArray(grid.size) { left[it] + PeqFitter.response(fit.left.bands)[it] - balance.coerceAtLeast(0.0) }
        val playedRight = DoubleArray(grid.size) { right[it] + PeqFitter.response(fit.right.bands)[it] + balance.coerceAtMost(0.0) }
        assertEquals(0.0, StereoFit.balanceDb(playedLeft, playedRight), 0.05)
    }

    private fun <T : Any> assertNotNullAndGet(value: T?): T { assertNotNull(value); return value!! }
}
