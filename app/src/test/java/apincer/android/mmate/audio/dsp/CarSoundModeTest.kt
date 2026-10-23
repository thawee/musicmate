package apincer.android.mmate.audio.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarSoundModeTest {
    @Test
    fun everyModeStaysWithinThreeFiltersAndFourDecibels() {
        for (mode in CarSoundMode.entries) {
            val bands = mode.bands(bassDb = 3.0, trebleDb = 3.0)
            assertTrue(mode.name, bands.size <= 3)
            assertTrue(mode.name, bands.all { it.gainDb <= 4.0 })
        }
    }

    @Test
    fun theAutomaticPreampAbsorbsEachModesBoost() {
        for (mode in CarSoundMode.entries) {
            val chain = DspChain(mode.bands(3.0, 3.0), 48_000, 2)
            val loudest = generateSequence(20.0) { it * 1.01 }.takeWhile { it < 20_000 }.maxOf { chain.responseDb(it) }
            assertTrue(mode.name, loudest + chain.preampDb <= 0.01)
        }
    }

    @Test
    fun customTrimsAreClampedAndFlatTrimsAddNoFilters() {
        assertEquals(emptyList<PeqBand>(), CarSoundMode.CUSTOM.bands(0.0, 0.0))
        val trimmed = CarSoundMode.CUSTOM.bands(bassDb = 9.0, trebleDb = -9.0)
        assertEquals(listOf(3.0, -3.0), trimmed.map { it.gainDb })
        assertEquals(emptyList<PeqBand>(), CarSoundMode.REFERENCE.bands())
    }

    @Test
    fun offMeansNoTarget() {
        assertNull(CarDspChoice(enabled = false, mode = CarSoundMode.DRIVE).settings())
        assertEquals(CarSoundMode.DRIVE.bands(), CarDspChoice(enabled = true, mode = CarSoundMode.DRIVE).settings()!!.bands)
    }
}
