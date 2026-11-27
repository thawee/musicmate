package apincer.android.mmate.audio.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StereoCorrectionTest {
    private val left = listOf(PeqBand(PeqBand.Type.PEAK, 100.0, -6.0, 1.0))
    private val right = listOf(PeqBand(PeqBand.Type.PEAK, 2_000.0, -4.0, 1.0))
    private val combined = listOf(PeqBand(PeqBand.Type.PEAK, 500.0, -3.0, 1.0))
    private val personal = listOf(PeqBand(PeqBand.Type.LOW_SHELF, 60.0, 2.0, 0.7))
    private val stereo = StereoCorrection(left, right, rightDelayMs = 1.0)

    /** Output index of the impulse fed at frame 0 on [channel]. */
    private fun arrival(chain: DspChain, channel: Int, channels: Int = 2): Int {
        val frame = DoubleArray(channels)
        for (n in 0 until 2_000) {
            frame.fill(if (n == 0) 1.0 else 0.0)
            chain.process(frame)
            if (frame[channel] > 0.5) return n
        }
        return -1
    }

    @Test
    fun theDelayedSideArrivesLaterByTheDelayAtEachRate() {
        for ((rate, samples) in listOf(44_100 to 44, 48_000 to 48, 96_000 to 96)) {
            val chain = DspChain(emptyList(), rate, 2, rightDelayMs = 1.0)
            assertEquals(0, arrival(chain, 0))
            assertEquals(samples, arrival(DspChain(emptyList(), rate, 2, rightDelayMs = 1.0), 1))
        }
        assertEquals(0, arrival(DspChain(emptyList(), 48_000, 1, leftDelayMs = 1.0), 0, channels = 1))
    }

    @Test
    fun resetDropsWhatTheDelayHolds() {
        val chain = DspChain(emptyList(), 48_000, 2, rightDelayMs = 1.0)
        chain.process(doubleArrayOf(1.0, 1.0))
        chain.reset()
        val frame = DoubleArray(2)
        repeat(200) {
            frame.fill(0.0)
            chain.process(frame)
            assertEquals(0.0, frame[1], 0.0)
        }
    }

    @Test
    fun eachLayoutGetsItsCorrectionWithThePersonalBandsOnTop() {
        val settings = stereo.settings(combined).with(personal)
        fun db(chain: DspChain, hz: Double, channel: Int) = chain.channelResponseDb(hz, channel)
        val two = DspChain.of(settings, 48_000, 2)
        val expectLeft = DspChain(personal + left, 48_000, 1)
        val expectRight = DspChain(personal + right, 48_000, 1)
        val expectOther = DspChain(personal + combined, 48_000, 1)
        for (hz in listOf(60.0, 100.0, 500.0, 2_000.0)) {
            assertEquals(expectLeft.responseDb(hz), db(two, hz, 0), 1e-9)
            assertEquals(expectRight.responseDb(hz), db(two, hz, 1), 1e-9)
            assertEquals(expectOther.responseDb(hz), db(DspChain.of(settings, 48_000, 1), hz, 0), 1e-9)
            assertEquals(expectOther.responseDb(hz), db(DspChain.of(settings, 48_000, 6), hz, 4), 1e-9)
        }
        assertEquals(48, arrival(DspChain.of(settings, 48_000, 2), 1))
    }

    @Test
    fun modeLevelMatchingKeepsTheDelayAndSides() {
        val settings = stereo.settings(combined).with(personal)
        val matched = ModeLevelMatch.select(settings, stereo.settings(combined), hearCorrection = false)
        assertEquals(1.0, matched.rightDelayMs, 0.0)
        assertEquals(left, matched.leftBands)
        assertEquals(combined, matched.monoBands)
        assertTrue(matched.requestedPreampDb <= 0.0)
    }

    @Test
    fun aCarMeasuredSideBySidePlaysItsSidesAndKeepsTheCombinedForOtherLayouts() {
        val choice = CarDspChoice(enabled = true, mode = SoundMode.REFERENCE, correction = combined, stereo = stereo)
        val settings = choice.settings()!!
        assertEquals(left, settings.leftBands.filter { it in left })
        assertEquals(combined, settings.monoBands)
        assertEquals(1.0, settings.rightDelayMs, 0.0)
        val flat = CarDspChoice(enabled = true, mode = SoundMode.REFERENCE, correction = combined).settings()!!
        assertTrue(flat.bands.containsAll(combined))
        assertEquals(0.0, flat.rightDelayMs, 0.0)
    }
}
