package apincer.android.mmate.audio.calibration

import apincer.android.mmate.audio.dsp.DspChain
import apincer.android.mmate.audio.dsp.PeqBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

class EngineNoiseCalibrationTest {
    @Test
    fun engineToneAllowsPartialCalibration() {
        val recording = recording()
        addTone(recording, 160.0, 0.3)
        val take = analyzer.analyze(recording)
        assertTrue("A narrow engine tone should allow partial calibration: ${(take as? Take.Rejected)?.detail}", take is Take.Accepted)
        take as Take.Accepted
        val engineBand = TakeAnalyzer.SNR_BANDS_HZ.indices.minBy { kotlin.math.abs(TakeAnalyzer.SNR_BANDS_HZ[it] - 160.0) }
        assertTrue("The noisy band must stay marked unreliable", take.bandSnrDb[engineBand] < TakeAnalyzer.MIN_SNR_DB)
        assertTrue("High frequencies should remain usable", take.bandSnrDb.last() >= TakeAnalyzer.MIN_SNR_DB)
    }

    @Test
    fun broadBackgroundNoiseStillRejectsTheTake() {
        val random = Random(71)
        val recording = recording().also { samples ->
            for (i in samples.indices) samples[i] += (random.nextDouble() * 2 - 1) * 0.3
        }
        val take = analyzer.analyze(recording)
        assertTrue("Broad noise must not produce a partial calibration", take is Take.Rejected)
        assertEquals(Take.Reason.TOO_QUIET, (take as Take.Rejected).reason)
    }

    @Test
    fun clippingIsNeverAllowedAsPartialCalibration() {
        val recording = recording()
        addTone(recording, 160.0, 0.6)
        assertEquals(Take.Reason.CLIPPED, (analyzer.analyze(recording) as Take.Rejected).reason)
    }

    @Test
    fun noiseBeforePlaybackIsMeasuredEvenWhenItStopsDuringTheSweep() {
        val recording = recording()
        for (i in 0 until sweep.sampleRate / 2) recording[i] += 0.3 * sin(2 * PI * 160 * i / sweep.sampleRate)
        val take = analyzer.analyze(recording) as Take.Accepted
        val engineBand = TakeAnalyzer.SNR_BANDS_HZ.indices.minBy { kotlin.math.abs(TakeAnalyzer.SNR_BANDS_HZ[it] - 160.0) }
        assertTrue("The silent lead must contribute to the noise estimate", take.bandSnrDb[engineBand] < TakeAnalyzer.MIN_SNR_DB)
    }

    @Test
    fun noiseAfterPlaybackIsMeasuredEvenWhenAbsentDuringTheSweep() {
        val recording = recording()
        for (i in recording.size - sweep.sampleRate / 4 until recording.size) {
            recording[i] += 0.3 * sin(2 * PI * 160 * i / sweep.sampleRate)
        }
        val take = analyzer.analyze(recording) as Take.Accepted
        val engineBand = TakeAnalyzer.SNR_BANDS_HZ.indices.minBy { kotlin.math.abs(TakeAnalyzer.SNR_BANDS_HZ[it] - 160.0) }
        assertTrue("The silent tail must contribute to the noise estimate", take.bandSnrDb[engineBand] < TakeAnalyzer.MIN_SNR_DB)
    }

    @Test
    fun changingNoiseCannotAccumulateTooManyExclusionsAcrossPositions() {
        fun take(hz: Double?) = Take.Accepted(DoubleArray(FrequencyGrid.HZ.size), DoubleArray(TakeAnalyzer.SNR_BANDS_HZ.size) { b ->
            if (hz != null && b == TakeAnalyzer.SNR_BANDS_HZ.indices.minBy { kotlin.math.abs(TakeAnalyzer.SNR_BANDS_HZ[it] - hz) }) 0.0 else 60.0
        }, 0.5)
        val session = MeasurementSession()
        val accepted = listOf(take(125.0), take(315.0), take(1_000.0))
        for (position in accepted) assertTrue(session.add(position))
        assertFalse("A fourth distinct noisy band must require a retry", session.add(take(3_150.0)))
        assertEquals(accepted, session.takes)
        assertTrue("A clean retry must preserve earlier spots and let measurement continue", session.add(take(null)))
        assertEquals(4, session.takes.size)
        assertEquals(3, TakeAnalyzer.unreliableBands(session.takes).size)
        session.clear()
        assertTrue(session.takes.isEmpty())
    }

    @Test
    fun engineNoiseAloneIsNotASpeakerResponse() {
        val recording = DoubleArray((sweep.sampleRate * 13.5).toInt())
        addTone(recording, 160.0, 0.3)
        assertTrue(analyzer.analyze(recording) is Take.Rejected)
    }

    @Test
    fun tooManyContaminatedBandsRejectRatherThanSkippingMostOfTheSpectrum() {
        val recording = recording()
        for (hz in listOf(125.0, 315.0, 1_000.0, 3_150.0)) addTone(recording, hz, 0.1)
        val take = analyzer.analyze(recording)
        assertEquals(Take.Reason.TOO_QUIET, (take as Take.Rejected).reason)
    }

    @Test
    fun engineNoiseAllowsTheFullMeasurementFitAndVerificationPath() {
        val speaker = listOf(
            PeqBand(PeqBand.Type.PEAK, 1_200.0, -6.0, 3.0),
            PeqBand(PeqBand.Type.PEAK, 4_000.0, 4.0, 2.0)
        )
        val measured = recording(filter(speaker, sweep.samples))
        addTone(measured, 160.0, 0.2)
        val take = analyzer.analyze(measured) as Take.Accepted
        val fit = PeqFitter.fit(take.responseDb, TakeAnalyzer.reliableFromHz(listOf(take)), TakeAnalyzer.unreliableBands(listOf(take)))
        assertTrue(fit.excludedBandsHz.isNotEmpty())
        val corrected = recording(filter(speaker, filter(fit.bands, sweep.samples)))
        addTone(corrected, 160.0, 0.2)
        val checked = analyzer.analyze(corrected) as Take.Accepted
        val verdict = PeqFitter.verify(fit, checked)
        assertTrue("Reliable frequencies should verify: ${verdict?.worstDeviationDb} dB", verdict?.passed == true)
    }

    private fun filter(bands: List<PeqBand>, signal: DoubleArray): DoubleArray {
        val chain = DspChain(bands, sweep.sampleRate, 1)
        val frame = DoubleArray(1)
        return DoubleArray(signal.size) {
            frame[0] = signal[it]
            chain.process(frame)
            frame[0]
        }
    }

    private fun recording(signal: DoubleArray = sweep.samples): DoubleArray = DoubleArray((sweep.sampleRate * 13.5).toInt()).also {
        signal.copyInto(it, sweep.sampleRate / 2)
    }

    private fun addTone(samples: DoubleArray, hz: Double, amplitude: Double) {
        for (i in samples.indices) samples[i] += amplitude * sin(2 * PI * hz * i / sweep.sampleRate)
    }

    private companion object {
        val sweep = LogSweep()
        val analyzer by lazy { TakeAnalyzer(Deconvolver(sweep)) }
    }
}
