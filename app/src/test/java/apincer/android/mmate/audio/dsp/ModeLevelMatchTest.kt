package apincer.android.mmate.audio.dsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

class ModeLevelMatchTest {
    @Test
    fun flatSignalEnergyIncludesRequestedAttenuationAndStereoBalance() {
        assertEquals(0.0, ModeLevelMatch.energyDb(CarDspSettings(emptyList())), 1e-9)
        for (balance in listOf(-6.0, 6.0)) {
            val settings = CarDspSettings(emptyList(), requestedPreampDb = -12.0, balanceDb = balance)
            val expected = -12.0 + 10 * log10((1.0 + 10.0.pow(-6.0 / 10)) / 2)
            assertEquals(expected, ModeLevelMatch.energyDb(settings), 1e-9)
        }
    }

    @Test
    fun eitherLouderSideIsAttenuatedToTheQuieterSide() {
        val quiet = CarDspSettings(emptyList(), requestedPreampDb = -12.0)
        val loud = CarDspSettings(emptyList())
        for ((selected, correction) in listOf(quiet to loud, loud to quiet)) {
            for (hearCorrection in listOf(false, true)) {
                val matched = ModeLevelMatch.select(selected, correction, hearCorrection)
                assertEquals(-12.0, matched.requestedPreampDb, 1e-9)
                assertEquals(-12.0, ModeLevelMatch.energyDb(matched), 1e-9)
            }
        }
    }

    @Test
    fun everySelectableModeMatchesCorrectionEnergyWithoutRaisingEffectivePreamp() {
        val correction = CarDspSettings(
            listOf(PeqBand(PeqBand.Type.PEAK, 270.0, -3.0, 1.2)),
            requestedPreampDb = -2.0,
            leftBands = listOf(PeqBand(PeqBand.Type.LOW_SHELF, 100.0, 5.0, 0.707)),
            rightBands = listOf(PeqBand(PeqBand.Type.HIGH_SHELF, 8_000.0, 4.0, 0.707)),
            balanceDb = -3.0
        )
        for (mode in SoundMode.selectable) {
            val trims = if (mode == SoundMode.CUSTOM) {
                listOf(-4.0 to -4.0, -4.0 to 4.0, 4.0 to -4.0, 4.0 to 4.0)
            } else listOf(0.0 to 0.0)
            for ((bass, treble) in trims) {
                val selected = CarDspSettings(correction.bands + mode.bands(bass, treble),
                    requestedPreampDb = correction.requestedPreampDb, leftBands = correction.leftBands,
                    rightBands = correction.rightBands, balanceDb = correction.balanceDb)
                val selectedMatch = ModeLevelMatch.select(selected, correction, hearCorrection = false)
                val correctionMatch = ModeLevelMatch.select(selected, correction, hearCorrection = true)
                val targetEnergy = minOf(ModeLevelMatch.energyDb(selected), ModeLevelMatch.energyDb(correction))
                val label = "$mode ($bass/$treble)"
                assertEquals(label, targetEnergy, ModeLevelMatch.energyDb(selectedMatch), 1e-9)
                assertEquals(label, targetEnergy, ModeLevelMatch.energyDb(correctionMatch), 1e-9)
                assertTrue(label, chain(selectedMatch).preampDb <= chain(selected).preampDb + 1e-9)
                assertTrue(label, chain(correctionMatch).preampDb <= chain(correction).preampDb + 1e-9)
            }
        }
    }

    @Test
    fun selectionPreservesEachSourcesBandsAndOutputSettings() {
        val correction = CarDspSettings(
            listOf(PeqBand(PeqBand.Type.PEAK, 300.0, -3.0, 1.0)), compress = true,
            leftBands = listOf(PeqBand(PeqBand.Type.PEAK, 1_000.0, 2.0, 1.0)),
            rightBands = listOf(PeqBand(PeqBand.Type.PEAK, 2_000.0, -2.0, 1.0)),
            balanceDb = 3.0, monoBands = listOf(PeqBand(PeqBand.Type.PEAK, 1_500.0, 1.0, 1.0)),
            crossfeed = true
        )
        val selected = CarDspSettings(correction.bands + SoundMode.LIVELY.bands(),
            leftBands = correction.rightBands, rightBands = correction.leftBands, balanceDb = -2.0,
            monoBands = listOf(PeqBand(PeqBand.Type.PEAK, 750.0, -1.0, 1.0)))
        for (hearCorrection in listOf(false, true)) {
            val source = if (hearCorrection) correction else selected
            val matched = ModeLevelMatch.select(selected, correction, hearCorrection)
            assertEquals(source.bands, matched.bands)
            assertEquals(source.leftBands, matched.leftBands)
            assertEquals(source.rightBands, matched.rightBands)
            assertEquals(source.monoBands, matched.monoBands)
            assertEquals(source.balanceDb, matched.balanceDb, 0.0)
            assertEquals(source.compress, matched.compress)
            assertEquals(source.crossfeed, matched.crossfeed)
        }
    }

    @Test
    fun identicalNeutralChoicesLeaveEffectiveGainAndPcmUnchanged() {
        val neutral = CarDspSettings(SoundMode.REFERENCE.bands(), compress = true,
            requestedPreampDb = -3.0, balanceDb = 2.0, crossfeed = true)
        for (hearCorrection in listOf(false, true)) {
            val matched = ModeLevelMatch.select(neutral, neutral, hearCorrection)
            assertEquals(neutral.requestedPreampDb, matched.requestedPreampDb, 0.0)
            val originalChain = chain(neutral)
            val matchedChain = chain(matched)
            assertEquals(originalChain.preampDb, matchedChain.preampDb, 0.0)
            repeat(2_048) { sample ->
                val expected = doubleArrayOf(0.2 * sin(sample * 0.17), 0.15 * sin(sample * 0.23))
                val actual = expected.copyOf()
                originalChain.process(expected)
                matchedChain.process(actual)
                assertArrayEquals(expected, actual, 0.0)
            }
        }
    }

    private fun chain(settings: CarDspSettings) = DspChain(settings.bands, 48_000, 2,
        settings.requestedPreampDb, settings.leftBands, settings.rightBands,
        settings.balanceDb, settings.monoBands, settings.crossfeed)
}
