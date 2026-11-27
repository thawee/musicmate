package apincer.android.mmate.audio.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpeakerTargetTest {
    private fun SpeakerTarget.at(hz: Double) = curve[FrequencyGrid.HZ.indices.minBy { abs(FrequencyGrid.HZ[it] - hz) }]

    @Test
    fun theRoomTargetHasTheStudysBassAndTreble() {
        val bass = SpeakerTarget.ROOM.at(31.5)
        val treble = SpeakerTarget.ROOM.at(16_000.0)
        assertTrue("bass $bass dB", bass in 6.0..6.6)
        assertTrue("treble $treble dB", treble in -2.4..-1.5)
    }

    @Test
    fun flatIsFlatAndAnUnknownIdFallsBackToCar() {
        assertTrue(SpeakerTarget.FLAT.curve.all { it == 0.0 })
        assertEquals(FrequencyGrid.HZ.size, SpeakerTarget.ROOM.curve.size)
        assertEquals(SpeakerTarget.CAR, SpeakerTarget.of(null))
        assertEquals(SpeakerTarget.CAR, SpeakerTarget.of("harman-2031"))
        assertTrue(SpeakerTarget.CAR.curve.contentEquals(CarTarget.CURVE))
    }
}
