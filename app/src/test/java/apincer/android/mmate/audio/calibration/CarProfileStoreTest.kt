package apincer.android.mmate.audio.calibration

import apincer.android.mmate.audio.dsp.CarDspChoice
import apincer.android.mmate.audio.dsp.CarSoundMode
import apincer.android.mmate.audio.dsp.PeqBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CarProfileStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun profile(id: String, created: Long) = CarProfile(
        id = id,
        name = "MU-X $id",
        createdAtMillis = created,
        bluetoothName = "CAR2-4BF4",
        bluetoothAddress = "AA:BB:CC:DD:EE:FF",
        bands = listOf(PeqBand(PeqBand.Type.PEAK, 202.0, -6.0, 2.14)),
        fromHz = 45.0,
        measuredDb = listOf(1.0, 2.0),
        targetDb = listOf(0.0, 0.0),
        predictedDb = listOf(0.5, 0.5),
        micSource = "UNPROCESSED",
        approximate = false
    )

    @Test
    fun savesListsNewestFirstAndDeletes() {
        val store = CarProfileStore(File(folder.root, "car_profiles"))
        store.save(profile("old", 1_000))
        store.save(profile("new", 2_000))

        assertEquals(listOf("new", "old"), store.list().map { it.id })
        assertEquals(profile("old", 1_000), store.get("old"))

        store.delete("new")
        assertEquals(listOf("old"), store.list().map { it.id })
        assertNull(store.get("new"))
    }

    @Test
    fun anUnreadableFileIsSkipped() {
        val dir = folder.newFolder("car_profiles")
        File(dir, "broken.json").writeText("{ not json")
        val store = CarProfileStore(dir)
        store.save(profile("good", 1))
        assertEquals(listOf("good"), store.list().map { it.id })
    }

    @Test
    fun theCorrectionSitsUnderTheMode() {
        val correction = profile("car", 1).bands
        val choice = CarDspChoice(enabled = true, mode = CarSoundMode.DRIVE, correction = correction)
        assertEquals(correction + CarSoundMode.DRIVE.bands(), choice.settings()!!.bands)
    }
}
