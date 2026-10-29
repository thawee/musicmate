package apincer.android.mmate.audio.headphones

import apincer.android.mmate.audio.dsp.Biquad
import apincer.android.mmate.audio.dsp.PeqBand
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FiveBandHeadphoneToneTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun legacyJsonRetainsItsThreeBandResponseAndDefaultsNewBandsToNeutral() {
        val file = File(temporary.root, "legacy.json")
        file.writeText("""{"profiles":[{"id":"legacy","name":"Legacy","source":"DIY","preampDb":0.0,"bands":[],"manual":{"left":{"bassDb":2.0,"midDb":-1.0,"trebleDb":3.0},"right":{"bassDb":-2.0,"midDb":1.0,"trebleDb":-3.0}}}]}""")
        val tuning = HeadphoneProfileStore(file).load().profiles.single().manual!!
        val legacyBands = listOf(
            PeqBand(PeqBand.Type.LOW_SHELF, 100.0, 2.0, 0.707),
            PeqBand(PeqBand.Type.PEAK, 1_000.0, -1.0, 0.707),
            PeqBand(PeqBand.Type.HIGH_SHELF, 8_000.0, 3.0, 0.707)
        )
        assertEquals(HeadphoneTone(2.0, -1.0, 3.0), tuning.left)
        for (tone in listOf(tuning.left, tuning.right)) {
            assertEquals(0.0, tone.warmthDb, 0.0)
            assertEquals(0.0, tone.clarityDb, 0.0)
        }
        assertEquals(legacyBands, tuning.settings().leftBands)
        for (sampleRate in listOf(44_100, 48_000, 96_000)) {
            for (frequency in listOf(30.0, 100.0, 300.0, 1_000.0, 3_000.0, 8_000.0, 18_000.0)) {
                val expected = legacyBands.sumOf { Biquad.design(it, sampleRate).magnitudeDb(frequency, sampleRate) }
                val actual = tuning.left.bands().sumOf { Biquad.design(it, sampleRate).magnitudeDb(frequency, sampleRate) }
                assertEquals(expected, actual, 1e-12)
            }
        }
        assertTrue(tuning.settings().monoBands.isEmpty())
    }

    @Test fun allFiveBandsRoundTripWithIndependentEarsAndMonoAverages() {
        val tuning = ManualHeadphoneTuning(
            left = HeadphoneTone(2.0, -3.0, 4.0, warmthDb = 5.0, clarityDb = -6.0),
            right = HeadphoneTone(-4.0, 1.0, -2.0, warmthDb = -1.0, clarityDb = 2.0),
            balanceDb = 2.0
        )
        val library = HeadphoneLibrary(listOf(HeadphoneProfile.createManual("Five band", tuning)))
        val file = File(temporary.root, "five-band.json")
        val store = HeadphoneProfileStore(file)
        store.save(library)
        val loaded = store.load()
        assertEquals(library, loaded)
        assertTrue(file.readText().contains("\"warmthDb\""))
        assertTrue(file.readText().contains("\"clarityDb\""))
        val settings = loaded.profiles.single().manual!!.settings()
        assertEquals(mapOf(100.0 to 2.0, 300.0 to 5.0, 1_000.0 to -3.0, 3_000.0 to -6.0, 8_000.0 to 4.0),
            settings.leftBands.associate { it.frequencyHz to it.gainDb })
        assertEquals(mapOf(100.0 to -4.0, 300.0 to -1.0, 1_000.0 to 1.0, 3_000.0 to 2.0, 8_000.0 to -2.0),
            settings.rightBands.associate { it.frequencyHz to it.gainDb })
        assertEquals(mapOf(100.0 to -1.0, 300.0 to 2.0, 1_000.0 to -1.0, 3_000.0 to -2.0, 8_000.0 to 1.0),
            settings.monoBands.associate { it.frequencyHz to it.gainDb })
        assertEquals(2.0, settings.balanceDb, 0.0)
    }

    @Test fun warmthAndClarityCreateIndependentPeakFilters() {
        assertEquals(listOf(PeqBand(PeqBand.Type.PEAK, 300.0, 3.0, 0.707)),
            HeadphoneTone(warmthDb = 3.0).bands())
        assertEquals(listOf(PeqBand(PeqBand.Type.PEAK, 3_000.0, -2.0, 0.707)),
            HeadphoneTone(clarityDb = -2.0).bands())
        assertTrue(HeadphoneTone().bands().isEmpty())
    }

    @Test fun eachNewBandIsValidatedIndependentlyInEitherEarBeforeSaving() {
        val store = HeadphoneProfileStore(File(temporary.root, "validated.json"))
        val pair = HeadphoneProfile.createManual("DIY", ManualHeadphoneTuning())
        for (bad in listOf(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -6.1, 6.1)) {
            for (tone in listOf(HeadphoneTone(warmthDb = bad), HeadphoneTone(clarityDb = bad))) {
                for (tuning in listOf(ManualHeadphoneTuning(left = tone), ManualHeadphoneTuning(right = tone))) {
                    assertThrows(IllegalArgumentException::class.java) {
                        store.save(HeadphoneLibrary(listOf(pair.copy(manual = tuning))))
                    }
                }
            }
        }
        val limits = ManualHeadphoneTuning(
            left = HeadphoneTone(warmthDb = -6.0, clarityDb = 6.0),
            right = HeadphoneTone(warmthDb = 6.0, clarityDb = -6.0)
        )
        val library = HeadphoneLibrary(listOf(pair.copy(manual = limits)))
        store.save(library)
        assertEquals(library, store.load())
        assertTrue(limits.settings().monoBands.isEmpty())
    }

    @Test fun templatesKeepTheirExistingSoundsWithNeutralNewBands() {
        for (preset in HeadphoneTonePreset.entries) {
            assertEquals(0.0, preset.tone.warmthDb, 0.0)
            assertEquals(0.0, preset.tone.clarityDb, 0.0)
            assertEquals(preset, HeadphoneTonePreset.of(preset.tone))
        }
        assertNull(HeadphoneTonePreset.of(HeadphoneTone(warmthDb = 1.0)))
        assertNull(HeadphoneTonePreset.of(HeadphoneTone(clarityDb = 1.0)))
    }
}
