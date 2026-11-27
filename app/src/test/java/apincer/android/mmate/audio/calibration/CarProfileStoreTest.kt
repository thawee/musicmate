package apincer.android.mmate.audio.calibration

import apincer.android.mmate.audio.dsp.CarDspChoice
import apincer.android.mmate.audio.dsp.SoundMode
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
    fun findsTheCarForABluetoothAddress() {
        val store = CarProfileStore(File(folder.root, "car_profiles"))
        store.save(profile("mux", 1_000))
        store.save(profile("other", 2_000).copy(bluetoothAddress = "11:22:33:44:55:66"))

        assertEquals("mux", store.forAddress("aa:bb:cc:dd:ee:ff")?.id)
        assertEquals("other", store.forAddress("11:22:33:44:55:66")?.id)
        assertNull(store.forAddress("00:00:00:00:00:00"))
    }

    @Test
    fun aProfileSavedBeforeTheFitRangeWasStoredWasFittedTo8kHz() {
        val dir = folder.newFolder("car_profiles")
        File(dir, "old.json").writeText("""{"id":"old","name":"MU-X","createdAtMillis":1,"bands":[],"fromHz":45.0,""" +
            """"measuredDb":[],"targetDb":[],"predictedDb":[],"micSource":"UNPROCESSED","approximate":false}""")
        val store = CarProfileStore(dir)
        assertEquals(8_000.0, store.get("old")!!.toHz, 0.0)
        store.save(profile("voice", 2).copy(toHz = 4_000.0))
        assertEquals(4_000.0, store.get("voice")!!.toHz, 0.0)
    }

    @Test
    fun aProfileSavedBeforeTargetsCouldBeChosenAimedForTheCarCurve() {
        val dir = folder.newFolder("car_profiles")
        File(dir, "old.json").writeText("""{"id":"old","name":"MU-X","createdAtMillis":1,"bands":[],"fromHz":45.0,""" +
            """"measuredDb":[],"targetDb":[],"predictedDb":[],"micSource":"UNPROCESSED","approximate":false}""")
        val store = CarProfileStore(dir)
        assertEquals(SpeakerTarget.CAR, SpeakerTarget.of(store.get("old")!!.targetId))
        store.save(profile("room", 2).copy(targetId = SpeakerTarget.ROOM.id))
        assertEquals(SpeakerTarget.ROOM, SpeakerTarget.of(store.get("room")!!.targetId))
        assertEquals(null, store.get("old")!!.stereo)
        val stereo = apincer.android.mmate.audio.dsp.StereoCorrection(
            listOf(PeqBand(PeqBand.Type.PEAK, 80.0, -5.0, 2.0)), emptyList(), rightDelayMs = 1.25, balanceDb = 1.5)
        store.save(profile("sides", 3).copy(stereo = stereo))
        assertEquals(stereo, store.get("sides")!!.stereo)
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
        val choice = CarDspChoice(enabled = true, mode = SoundMode.LIVELY, correction = correction)
        assertEquals(correction + SoundMode.LIVELY.bands(), choice.settings()!!.bands)
    }
}
