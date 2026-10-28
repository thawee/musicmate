package apincer.android.mmate.audio.headphones

import apincer.android.mmate.audio.dsp.PeqBand
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ManualHeadphoneTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun manualPairsPersistTypeAndSeparateEarAdjustmentsAlongsideImportedProfiles() {
        val tuning = ManualHeadphoneTuning(HeadphoneKind.IN_EAR,
            HeadphoneTone(2.0, -1.0, 3.0), HeadphoneTone(-2.0, 1.0, -3.0), -4.0)
        val pair = HeadphoneProfile.createManual("My DIY IEM", tuning)
        val imported = HeadphoneProfileParser.parse("Filter 1: ON PK Fc 1000 Hz Gain -2 dB Q 1", "Imported", "eq.txt")
        val store = HeadphoneProfileStore(File(temporary.root, "profiles.json"))
        val library = HeadphoneLibrary(listOf(pair, imported))
        store.save(library)
        assertEquals(library, store.load())
        assertTrue(pair.bands.isEmpty())
        assertEquals("Listening setup", pair.source)
        assertEquals(null, imported.manual)
    }

    @Test fun manualToneBuildsSeparateEarFiltersAndBalanceWithoutCompression() {
        val settings = ManualHeadphoneTuning(left = HeadphoneTone(2.0, -3.0, 0.0),
            right = HeadphoneTone(0.0, 0.0, 1.0), balanceDb = 2.0).settings()
        assertTrue(settings.bands.isEmpty())
        assertEquals(listOf(PeqBand.Type.LOW_SHELF, PeqBand.Type.PEAK), settings.leftBands.map { it.type })
        assertEquals(listOf(PeqBand.Type.HIGH_SHELF), settings.rightBands.map { it.type })
        assertEquals(2.0, settings.balanceDb, 0.0)
        assertEquals(listOf(1.0, -1.5, 0.5), settings.monoBands.map { it.gainDb })
        assertFalse(settings.compress)
    }

    @Test fun invalidManualValuesCannotBeSaved() {
        val store = HeadphoneProfileStore(File(temporary.root, "profiles.json"))
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, -6.1, 6.1)) {
            val pair = HeadphoneProfile.createManual("DIY", ManualHeadphoneTuning())
            assertThrows(IllegalArgumentException::class.java) {
                store.save(HeadphoneLibrary(listOf(pair.copy(manual = ManualHeadphoneTuning(left = HeadphoneTone(midDb = bad))))))
            }
        }
        assertThrows(IllegalArgumentException::class.java) { HeadphoneProfile.createManual(" ", ManualHeadphoneTuning()) }
        val pair = HeadphoneProfile.createManual("DIY", ManualHeadphoneTuning())
        assertThrows(IllegalArgumentException::class.java) {
            store.save(HeadphoneLibrary(listOf(pair.copy(manual = ManualHeadphoneTuning(balanceDb = 12.1)))))
        }
    }
}
