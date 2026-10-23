package apincer.android.mmate.audio.calibration

import apincer.android.mmate.audio.dsp.DspChain
import apincer.android.mmate.audio.dsp.PeqBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.pow

class CalibrationTest {
    private val grid = FrequencyGrid.HZ
    private val car = listOf(
        PeqBand(PeqBand.Type.PEAK, 200.0, 6.0, 2.0),
        PeqBand(PeqBand.Type.PEAK, 1_200.0, -6.0, 3.0),
        PeqBand(PeqBand.Type.PEAK, 4_000.0, 4.0, 2.0)
    )

    @Test
    fun fftRoundTripsAndFindsATone() {
        val fft = Fft(1024)
        val original = DoubleArray(1024) { kotlin.math.cos(2 * Math.PI * 8 * it / 1024) }
        val re = original.copyOf()
        val im = DoubleArray(1024)
        fft.forward(re, im)
        assertEquals(512.0, re[8], 1e-9)
        fft.inverse(re, im)
        for (i in original.indices) assertEquals(original[i], re[i], 1e-9)
    }

    @Test
    fun aCleanSweepMeasuresFlat() {
        val take = analyzer.analyze(record(sweep.samples)) as Take.Accepted
        val band = grid.indices.filter { grid[it] in 30.0..16_000.0 }.map { take.responseDb[it] }
        assertTrue("spread ${band.max() - band.min()}", band.max() - band.min() <= 0.2)
    }

    @Test
    fun aSyntheticCarIsRecoveredAtAnyDelay() {
        val expected = analytic(car)
        for (delay in doubleArrayOf(0.0, 0.3, 1.0)) {
            val take = analyzer.analyze(record(through(car, sweep.samples), delay, noiseRms = 1e-4)) as Take.Accepted
            assertEquals(0.5 + delay, take.delaySeconds, 0.001)
            val differences = grid.indices.filter { grid[it] in 40.0..8_000.0 }.map { take.responseDb[it] - expected[it] }
            val offset = differences.sorted()[differences.size / 2]
            val worst = differences.maxOf { abs(it - offset) }
            assertTrue("delay $delay: worst $worst dB", worst <= 0.5)
        }
    }

    @Test
    fun badTakesAreRejectedWithTheirReason() {
        val hum = record(sweep.samples, noiseRms = 1e-4).also { recording ->
            for (i in recording.indices) recording[i] += 0.3 * kotlin.math.sin(2 * Math.PI * 160 * i / sweep.sampleRate)
        }
        val quiet = analyzer.analyze(hum)
        assertEquals(Take.Reason.TOO_QUIET, (quiet as Take.Rejected).reason)
        assertTrue(quiet.detail, quiet.detail.contains("157 Hz"))
        val late = analyzer.analyze(record(sweep.samples, delaySeconds = 2.0))
        assertEquals(Take.Reason.TOO_SHORT, (late as Take.Rejected).reason)
        val clipped = analyzer.analyze(record(sweep.samples.map { it * 2.5 }.toDoubleArray()))
        assertEquals(Take.Reason.CLIPPED, (clipped as Take.Rejected).reason)
        val silence = analyzer.analyze(record(DoubleArray(sweep.length), noiseRms = 0.01))
        assertEquals(Take.Reason.NO_RESPONSE, (silence as Take.Rejected).reason)
    }

    @Test
    fun theFitReachesTheAchievableTargetWithinItsLimits() {
        val deviation = PeqFitter.response(car)
        val fit = PeqFitter.fit(DoubleArray(grid.size) { CarTarget.CURVE[it] + deviation[it] })
        assertTrue(fit.bands.size <= PeqFitter.MAX_BANDS)
        assertTrue(fit.bands.all { it.gainDb in PeqFitter.MAX_CUT_DB..PeqFitter.MAX_BOOST_DB })
        val worst = grid.indices.filter { grid[it] in 30.0..8_000.0 }.maxOf { abs(fit.predictedDb[it] - fit.achievableDb[it]) }
        assertTrue("worst $worst dB", worst <= 1.5)
    }

    @Test
    fun theCombinedCorrectionNeverBoostsBassOrPastThreeDecibels() {
        val thin = PeqFitter.response(listOf(
            PeqBand(PeqBand.Type.LOW_SHELF, 150.0, -8.0, 0.707),
            PeqBand(PeqBand.Type.PEAK, 3_000.0, -7.0, 0.7)
        ))
        val fit = PeqFitter.fit(DoubleArray(grid.size) { CarTarget.CURVE[it] + thin[it] })
        val correction = PeqFitter.response(fit.bands)
        for (g in grid.indices) {
            if (grid[g] < PeqFitter.NO_BOOST_BELOW_HZ) assertTrue("${grid[g]} Hz: ${correction[g]} dB", correction[g] <= 0.01)
            assertTrue("${grid[g]} Hz: ${correction[g]} dB", correction[g] <= PeqFitter.MAX_BOOST_DB + 0.01)
        }
    }

    @Test
    fun aNarrowDeepNullGetsNoBoost() {
        val notch = PeqFitter.response(listOf(PeqBand(PeqBand.Type.PEAK, 2_000.0, -15.0, 8.0)))
        val fit = PeqFitter.fit(DoubleArray(grid.size) { CarTarget.CURVE[it] + notch[it] })
        val near = 2_000.0 * 2.0.pow(-1.0 / 6)..2_000.0 * 2.0.pow(1.0 / 6)
        assertFalse(fit.bands.any { it.gainDb > 0 && it.frequencyHz in near })
    }

    @Test
    fun recordingLevelDoesNotChangeTheFit() {
        val deviation = PeqFitter.response(car)
        val measured = DoubleArray(grid.size) { CarTarget.CURVE[it] + deviation[it] }
        val quiet = PeqFitter.fit(measured).bands
        val loud = PeqFitter.fit(DoubleArray(grid.size) { measured[it] + 10.0 }).bands
        assertEquals(quiet.size, loud.size)
        quiet.zip(loud).forEach { (a, b) ->
            assertEquals(a.frequencyHz, b.frequencyHz, 1e-6)
            assertEquals(a.gainDb, b.gainDb, 1e-6)
        }
    }

    @Test
    fun aPreFilteredVerifySweepPassesWhereTheRawCarFails() {
        val take = analyzer.analyze(record(through(car, sweep.samples), 0.3, noiseRms = 1e-4)) as Take.Accepted
        val fit = PeqFitter.fit(take.responseDb)
        assertFalse(PeqFitter.verify(fit, take.responseDb).passed)

        val corrected = DspChain(fit.bands, sweep.sampleRate, 1).let { chain -> filter(chain, sweep.samples) }
        val verifyTake = analyzer.analyze(record(through(car, corrected), 0.3, noiseRms = 1e-4)) as Take.Accepted
        val verdict = PeqFitter.verify(fit, verifyTake.responseDb)
        assertTrue("worst ${verdict.worstDeviationDb} dB at ${verdict.worstHz} Hz", verdict.passed)
    }

    @Test
    fun bassRumbleLeavesTheBassUncorrectedInsteadOfRejecting() {
        val rumble = record(through(car, sweep.samples), 0.3, noiseRms = 1e-4).also { recording ->
            for (i in recording.indices) recording[i] += 0.3 * kotlin.math.sin(2 * Math.PI * 40 * i / sweep.sampleRate)
        }
        val take = analyzer.analyze(rumble) as Take.Accepted
        val from = TakeAnalyzer.reliableFromHz(listOf(take))
        assertTrue("from $from Hz", from > 40.0 && from < 100.0)
        val fit = PeqFitter.fit(take.responseDb, from)
        val correction = PeqFitter.response(fit.bands)
        for (g in grid.indices) if (grid[g] in 30.0..from - 10) assertTrue("${grid[g]} Hz: ${correction[g]}", abs(correction[g]) <= 1.0)
        val corrected = record(through(car, filter(DspChain(fit.bands, sweep.sampleRate, 1), sweep.samples)), 0.3, noiseRms = 1e-4)
        val verdict = PeqFitter.verify(fit, (analyzer.analyze(corrected) as Take.Accepted).responseDb)
        assertTrue("worst ${verdict.worstDeviationDb} dB at ${verdict.worstHz} Hz", verdict.passed && verdict.worstHz >= from)
    }

    /** What the analyzer would report for a perfect measurement of [bands]: same bins, same smoothing. */
    private fun analytic(bands: List<PeqBand>): DoubleArray {
        val chain = DspChain(bands, sweep.sampleRate, 1)
        val size = TakeAnalyzer.SPECTRUM_SIZE
        val power = DoubleArray(size / 2) { k ->
            val hz = k.toDouble() * sweep.sampleRate / size
            if (k == 0) 1.0 else 10.0.pow(chain.responseDb(hz) / 10)
        }
        return TakeAnalyzer.smoothedDb(power, sweep.sampleRate)
    }

    private fun through(bands: List<PeqBand>, signal: DoubleArray) = filter(DspChain(bands, sweep.sampleRate, 1), signal)

    private fun filter(chain: DspChain, signal: DoubleArray): DoubleArray {
        val frame = DoubleArray(1)
        return DoubleArray(signal.size) {
            frame[0] = signal[it]
            chain.process(frame)
            frame[0]
        }
    }

    /** Like the recorder: 13.5 s long, the signal 0.5 s plus [delaySeconds] in, with optional white noise. */
    private fun record(signal: DoubleArray, delaySeconds: Double = 0.0, noiseRms: Double = 0.0): DoubleArray {
        val rate = sweep.sampleRate
        val lead = (rate * (0.5 + delaySeconds)).toInt()
        val recording = DoubleArray((rate * 13.5).toInt())
        signal.copyInto(recording, lead, 0, minOf(signal.size, recording.size - lead))
        if (noiseRms > 0) {
            val random = Random(42)
            for (i in recording.indices) recording[i] += random.nextGaussian() * noiseRms
        }
        return recording
    }

    private companion object {
        val sweep = LogSweep()
        val analyzer by lazy { TakeAnalyzer(Deconvolver(sweep)) }
    }
}
