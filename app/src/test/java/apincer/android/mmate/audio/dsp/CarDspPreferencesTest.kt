package apincer.android.mmate.audio.dsp

import android.content.SharedPreferences
import apincer.android.mmate.audio.calibration.CarProfile
import apincer.android.mmate.audio.calibration.CarProfileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CarDspPreferencesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun car(id: String) = CarProfile(
        id = id,
        name = "Car $id",
        createdAtMillis = 1,
        bands = listOf(PeqBand(PeqBand.Type.PEAK, 200.0, -4.0, 2.0)),
        fromHz = 30.0,
        measuredDb = emptyList(),
        targetDb = emptyList(),
        predictedDb = emptyList(),
        micSource = "UNPROCESSED",
        approximate = false
    )

    @Test
    fun eachCarKeepsItsOwnMode() {
        val prefs = MemoryPreferences()
        var choice = CarDspPreferences.select(prefs, car("a"), CarDspChoice(enabled = true))
        choice = choice.copy(mode = CarSoundMode.DRIVE)
        CarDspPreferences.save(prefs, choice)

        choice = CarDspPreferences.select(prefs, car("b"), choice)
        assertEquals("a new car starts from the current mode", CarSoundMode.DRIVE, choice.mode)
        choice = choice.copy(mode = CarSoundMode.CUSTOM, bassDb = 2.5)
        CarDspPreferences.save(prefs, choice)

        choice = CarDspPreferences.select(prefs, car("a"), choice)
        assertEquals(CarSoundMode.DRIVE, choice.mode)
        assertEquals("Car a", choice.profileName)
        assertEquals(car("a").bands, choice.correction)

        choice = CarDspPreferences.select(prefs, car("b"), choice)
        assertEquals(CarSoundMode.CUSTOM, choice.mode)
        assertEquals(2.5, choice.bassDb, 1e-9)
    }

    @Test
    fun noCorrectionHasItsOwnModeAndOnOffIsShared() {
        val prefs = MemoryPreferences()
        var choice = CarDspPreferences.select(prefs, null, CarDspChoice(enabled = true, mode = CarSoundMode.VOCAL))
        CarDspPreferences.save(prefs, choice)
        choice = CarDspPreferences.select(prefs, car("a"), choice).copy(mode = CarSoundMode.BASS, enabled = false)
        CarDspPreferences.save(prefs, choice)

        choice = CarDspPreferences.select(prefs, null, choice)
        assertEquals(CarSoundMode.VOCAL, choice.mode)
        assertEquals(false, choice.enabled)
        assertNull(choice.profileId)
        assertEquals(emptyList<PeqBand>(), choice.correction)
    }

    @Test
    fun renameKeepsTheModeAndUpdatesTheActiveName() {
        val store = CarProfileStore(folder.root)
        store.save(car("a"))
        store.save(car("b"))
        val active = CarDspChoice(enabled = true, mode = CarSoundMode.DRIVE, profileName = "Car a", profileId = "a")

        val renamed = CarDspPreferences.rename(store, "a", "Weekend MU-X", active)
        assertEquals("Weekend MU-X", store.get("a")?.name)
        assertEquals("Weekend MU-X", renamed.profileName)
        assertEquals(CarSoundMode.DRIVE, renamed.mode)

        val other = CarDspPreferences.rename(store, "b", "Kitchen speaker", renamed)
        assertEquals("Kitchen speaker", store.get("b")?.name)
        assertEquals("renaming another speaker leaves the active one alone", renamed, other)
        assertEquals("an unknown id changes nothing", other, CarDspPreferences.rename(store, "gone", "X", other))
    }
}

/** Just enough SharedPreferences for the preference logic; edits apply at once. */
private class MemoryPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values
    override fun getString(key: String, defValue: String?) = values[key] as String? ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = defValues
    override fun getInt(key: String, defValue: Int) = values[key] as Int? ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as Float? ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as Boolean? ?: defValue
    override fun contains(key: String) = key in values
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { values[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = this
        override fun putInt(key: String, value: Int) = apply { this@MemoryPreferences.values[key] = value }
        override fun putLong(key: String, value: Long) = apply { this@MemoryPreferences.values[key] = value }
        override fun putFloat(key: String, value: Float) = apply { this@MemoryPreferences.values[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { this@MemoryPreferences.values[key] = value }
        override fun remove(key: String) = apply { values.remove(key) }
        override fun clear() = apply { values.clear() }
        override fun commit() = true
        override fun apply() {}
    }
}
