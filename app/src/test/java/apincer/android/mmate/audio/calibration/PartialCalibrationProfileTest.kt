package apincer.android.mmate.audio.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PartialCalibrationProfileTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun savedProfilesRetainExcludedFrequencies() {
        val profile = profile().copy(excludedBandsHz = listOf(157.49, 198.43))
        val store = CarProfileStore(folder.root)
        store.save(profile)
        assertEquals(profile, store.get(profile.id))
    }

    @Test
    fun profilesWithoutNoiseMetadataRemainReadable() {
        val profile = profile()
        val store = CarProfileStore(folder.root)
        store.save(profile)
        assertFalse(File(folder.root, "speaker.json").readText().contains("excludedBandsHz"))
        assertEquals(emptyList<Double>(), store.get(profile.id)!!.excludedBandsHz)
    }

    private fun profile() = CarProfile(
        id = "speaker", name = "Car", createdAtMillis = 1,
        bands = emptyList(), fromHz = 30.0,
        measuredDb = emptyList(), targetDb = emptyList(), predictedDb = emptyList(),
        micSource = "UNPROCESSED", approximate = false
    )
}
