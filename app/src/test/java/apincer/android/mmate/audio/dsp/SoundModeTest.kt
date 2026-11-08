package apincer.android.mmate.audio.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundModeTest {
    @Test
    fun presetsDescribeListeningPreferences() {
        assertEquals(
            listOf("Neutral", "Warm", "Vocal", "Lively", "Custom"),
            SoundMode.selectable.map { it.label }
        )
    }

    @Test
    fun everyModeStaysWithinThreeFiltersAndFourDecibels() {
        for (mode in SoundMode.entries) {
            val bands = mode.bands(bassDb = 4.0, trebleDb = 4.0)
            assertTrue(mode.name, bands.size <= 3)
            assertTrue(mode.name, bands.all { it.gainDb <= 4.0 })
        }
    }

    @Test
    fun theAutomaticPreampAbsorbsEachModesBoost() {
        for (mode in SoundMode.entries) {
            val chain = DspChain(mode.bands(4.0, 4.0), 48_000, 2)
            val loudest = generateSequence(20.0) { it * 1.01 }.takeWhile { it < 20_000 }.maxOf { chain.responseDb(it) }
            assertTrue(mode.name, loudest + chain.preampDb <= 0.01)
        }
    }

    @Test
    fun customTrimsAreClampedAndFlatTrimsAddNoFilters() {
        assertEquals(emptyList<PeqBand>(), SoundMode.CUSTOM.bands(0.0, 0.0))
        val trimmed = SoundMode.CUSTOM.bands(bassDb = 9.0, trebleDb = -9.0)
        assertEquals(listOf(4.0, -4.0), trimmed.map { it.gainDb })
        assertEquals(emptyList<PeqBand>(), SoundMode.REFERENCE.bands())
    }

    @Test
    fun nightListeningIsIndependentOfEverySoundMode() {
        for (mode in SoundMode.entries) {
            val settings = CarDspChoice(enabled = true, mode = mode).settings()!!
            assertEquals(mode.name, false, settings.compress)
            assertEquals(mode.name, true, CarDspChoice(enabled = true, mode = mode, nightListening = true).settings()!!.compress)
        }
    }

    @Test
    fun offMeansNoTarget() {
        assertNull(CarDspChoice(enabled = false, mode = SoundMode.LIVELY).settings())
        assertEquals(SoundMode.LIVELY.bands(), CarDspChoice(enabled = true, mode = SoundMode.LIVELY).settings()!!.bands)
    }
}
