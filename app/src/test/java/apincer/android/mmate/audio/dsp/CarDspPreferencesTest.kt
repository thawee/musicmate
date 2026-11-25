package apincer.android.mmate.audio.dsp

import android.content.SharedPreferences
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import apincer.android.mmate.audio.calibration.CarProfile
import apincer.android.mmate.audio.calibration.CarProfileStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CarDspPreferencesTest {
    @After
    fun resetTransientState() {
        CarDspPreferences.clearCustomPreview()
        CarDspPreferences.setCustomComparison(false)
        CarDsp.target = null
    }

    private fun assertSettingsEqual(expected: CarDspSettings?, actual: CarDspSettings?) {
        requireNotNull(expected)
        requireNotNull(actual)
        assertEquals(expected.bands, actual.bands)
        assertEquals(expected.leftBands, actual.leftBands)
        assertEquals(expected.rightBands, actual.rightBands)
        assertEquals(expected.monoBands, actual.monoBands)
        assertEquals(expected.requestedPreampDb, actual.requestedPreampDb, 0.0)
        assertEquals(expected.balanceDb, actual.balanceDb, 0.0)
        assertEquals(expected.compress, actual.compress)
    }


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

    @Test fun quietListeningIsSavedPerProfileAndLegacyChoicesDefaultOff() {
        val prefs = MemoryPreferences()
        assertEquals(0.0, CarDspPreferences.load(prefs, null, CarDspChoice(quietListening = 0.8)).quietListening, 0.0)
        val base = CarDspChoice(enabled = true, quietListening = 0.25, nightListening = true)
        CarDspPreferences.save(prefs, base)
        val a = CarDspPreferences.select(prefs, car("quiet-a"), base)
        assertEquals(0.0, a.quietListening, 0.0)
        CarDspPreferences.save(prefs, a.copy(quietListening = 0.75))
        assertEquals(0.25, CarDspPreferences.load(prefs, null, a).quietListening, 0.0)
        assertEquals(0.75, CarDspPreferences.load(prefs, car("quiet-a"), base).quietListening, 0.0)
        prefs.edit().putFloat("quiet_listening:quiet-a", Float.NaN).apply()
        assertEquals(0.0, CarDspPreferences.load(prefs, car("quiet-a"), base).quietListening, 0.0)
        CarDspPreferences.save(prefs, base.copy(quietListening = 5.0))
        assertEquals(1.0, CarDspPreferences.load(prefs, null, base).quietListening, 0.0)
    }

    @Test fun modeComparisonKeepsQuietListeningAndSetterNormalizesIt() {
        val prefs = MemoryPreferences()
        val context = mockk<Context> {
            every { getSharedPreferences("car_dsp", Context.MODE_PRIVATE) } returns prefs
        }
        val saved = CarDspChoice(enabled = true, mode = SoundMode.LIVELY, correction = car("quiet").bands,
            quietListening = 0.5, nightListening = true)
        CarDspPreferences.update(context, saved)
        CarDspPreferences.setModeComparison(true)
        assertEquals(saved.correction + QuietListening.bands(0.5), CarDsp.target!!.bands)
        assertEquals(true, CarDsp.target!!.compress)
        CarDspPreferences.setQuietListening(context, Double.POSITIVE_INFINITY)
        assertEquals(0.0, CarDspPreferences.state.value.quietListening, 0.0)
        assertEquals(saved.correction + saved.mode.bands(), CarDsp.target!!.bands)
    }

    @Test
    fun modeComparisonRestoresOriginalAndClearsOnRoutesSettingsAndBypass() {
        val prefs = MemoryPreferences()
        val context = mockk<Context> {
            every { getSharedPreferences("car_dsp", Context.MODE_PRIVATE) } returns prefs
        }
        val saved = CarDspChoice(enabled = true, mode = SoundMode.LIVELY,
            nightListening = true, correction = car("a").bands)
        CarDspPreferences.update(context, saved)
        val persisted = prefs.all.toMap()
        val original = CarDsp.target
        CarDspPreferences.setModeComparison(true)
        assertEquals(saved.correction, CarDsp.target!!.bands)
        assertEquals(true, CarDsp.target!!.compress)
        assertEquals(saved, CarDspPreferences.state.value)
        assertEquals(persisted, prefs.all)
        CarDspPreferences.setModeComparison(false)
        assertEquals(original!!.bands, CarDsp.target!!.bands)
        assertEquals(true, CarDsp.target!!.compress)
        CarDspPreferences.setModeComparison(null)
        assertSettingsEqual(original, CarDsp.target)
        CarDspPreferences.setModeComparison(true)
        CarDspPreferences.onRouteChanged()
        assertSettingsEqual(original, CarDsp.target)
        CarDspPreferences.setModeComparison(true)
        val changed = saved.copy(mode = SoundMode.VOCAL, nightListening = false)
        CarDspPreferences.update(context, changed)
        assertSettingsEqual(changed.settings(), CarDsp.target)
        CarDspPreferences.update(context, changed.copy(enabled = false))
        for (comparison in listOf(true, false, null)) {
            CarDspPreferences.setModeComparison(comparison)
            assertNull(CarDsp.target)
        }
        CarDspPreferences.update(context, CarDspChoice())
    }

    @Test
    fun transientComparisonAndSliderPreviewRestoreSavedChoiceWithoutWrites() {
        val prefs = MemoryPreferences()
        val context = mockk<Context> {
            every { getSharedPreferences("car_dsp", Context.MODE_PRIVATE) } returns prefs
        }
        val saved = CarDspChoice(enabled = true, mode = SoundMode.CUSTOM,
            bassDb = 2.0, nightListening = true, correction = car("a").bands)
        CarDspPreferences.update(context, saved)
        val persisted = prefs.all.toMap()
        CarDspPreferences.setCustomComparison(true)
        assertEquals(true, CarDspPreferences.comparingCustom.value)
        assertEquals(saved.correction, CarDsp.target!!.bands)
        assertEquals(true, CarDsp.target!!.compress)
        assertEquals(persisted, prefs.all)
        CarDspPreferences.onRouteChanged()
        assertEquals(false, CarDspPreferences.comparingCustom.value)
        assertSettingsEqual(saved.settings(), CarDsp.target)
        CarDspPreferences.previewCustomTrims(4.0, -2.0)
        assertEquals(4.0, CarDspPreferences.state.value.bassDb, 0.0)
        assertEquals(persisted, prefs.all)
        CarDspPreferences.clearCustomPreview()
        assertEquals(saved, CarDspPreferences.state.value)
        CarDspPreferences.previewCustomTrims(4.0, -2.0)
        CarDspPreferences.onRouteChanged()
        assertEquals(saved, CarDspPreferences.state.value)
        CarDspPreferences.setCustomComparison(true)
        CarDspPreferences.update(context, saved.copy(nightListening = false))
        assertEquals(false, CarDspPreferences.comparingCustom.value)
        assertEquals(false, CarDsp.target!!.compress)
        CarDspPreferences.update(context, CarDspChoice())
    }

    @Test
    fun legacyBassAndNightMigrateOnceAndStayIndependentPerCar() {
        val prefs = MemoryPreferences()
        prefs.edit().putString("mode:a", "BASS").putFloat("custom_treble_db:a", -2f)
            .putString("mode:b", "NIGHT").apply()
        val bass = CarDspPreferences.load(prefs, car("a"), CarDspChoice())
        assertEquals(SoundMode.CUSTOM, bass.mode)
        assertEquals(4.0, bass.bassDb, 0.0)
        assertEquals(-2.0, bass.trebleDb, 0.0)
        val night = CarDspPreferences.load(prefs, car("b"), bass)
        assertEquals(SoundMode.REFERENCE, night.mode)
        assertEquals(true, night.nightListening)
        assertEquals("REFERENCE", prefs.getString("mode:b", null))
        CarDspPreferences.save(prefs, night.copy(nightListening = false))
        assertEquals(false, CarDspPreferences.load(prefs, car("b"), night).nightListening)
        assertEquals(bass, CarDspPreferences.load(prefs, car("a"), bass))
    }

    @Test
    fun comparisonOnlyRemovesCustomTrimsAndNeverChangesSavedPreferences() {
        val prefs = MemoryPreferences()
        val choice = CarDspChoice(enabled = true, mode = SoundMode.CUSTOM, bassDb = 3.0,
            trebleDb = -2.0, correction = car("a").bands, nightListening = true)
        CarDspPreferences.save(prefs, choice)
        val before = prefs.all.toMap()
        val comparing = choice.settings(comparingCustom = true)!!
        assertEquals(choice.correction, comparing.bands)
        assertEquals(true, comparing.compress)
        assertEquals(before, prefs.all)
        assertEquals(choice.copy(correction = emptyList()), CarDspPreferences.load(prefs, null, choice))
        val warm = choice.copy(mode = SoundMode.RELAX)
        assertSettingsEqual(warm.settings(), warm.settings(comparingCustom = true))
        assertNull(choice.copy(enabled = false).settings(comparingCustom = true))
    }

    @Test
    fun legacyModesKeepTheirIntentAndProfileSettings() {
        for (profile in listOf(null, car("a"))) {
            for ((saved, label) in mapOf("DRIVE" to "Lively", "SLEEP" to "Neutral", "FOCUS" to "Warm")) {
                val prefs = MemoryPreferences()
                val suffix = profile?.let { ":${it.id}" } ?: ""
                prefs.edit().putString("mode$suffix", saved)
                    .putFloat("custom_bass_db$suffix", 2.5f).apply()
                val loaded = CarDspPreferences.select(prefs, profile, CarDspChoice(enabled = true))
                assertEquals(saved, label, loaded.mode.label)
                assertEquals(saved == "SLEEP", loaded.nightListening)
                assertEquals(profile?.bands ?: emptyList<PeqBand>(), loaded.correction)
                assertEquals(2.5, loaded.bassDb, 1e-9)
                assertEquals(true, loaded.enabled)
                assertEquals(loaded.mode.name, prefs.getString("mode$suffix", null))
            }
        }
    }

    @Test
    fun eachCarKeepsItsOwnMode() {
        val prefs = MemoryPreferences()
        var choice = CarDspPreferences.select(prefs, car("a"), CarDspChoice(enabled = true))
        choice = choice.copy(mode = SoundMode.LIVELY)
        CarDspPreferences.save(prefs, choice)

        choice = CarDspPreferences.select(prefs, car("b"), choice)
        assertEquals("a new car starts from the current mode", SoundMode.LIVELY, choice.mode)
        choice = choice.copy(mode = SoundMode.CUSTOM, bassDb = 2.5)
        CarDspPreferences.save(prefs, choice)

        choice = CarDspPreferences.select(prefs, car("a"), choice)
        assertEquals(SoundMode.LIVELY, choice.mode)
        assertEquals("Car a", choice.profileName)
        assertEquals(car("a").bands, choice.correction)

        choice = CarDspPreferences.select(prefs, car("b"), choice)
        assertEquals(SoundMode.CUSTOM, choice.mode)
        assertEquals(2.5, choice.bassDb, 1e-9)
    }

    @Test
    fun noCorrectionHasItsOwnModeAndOnOffIsShared() {
        val prefs = MemoryPreferences()
        var choice = CarDspPreferences.select(prefs, null, CarDspChoice(enabled = true, mode = SoundMode.VOCAL))
        CarDspPreferences.save(prefs, choice)
        choice = CarDspPreferences.select(prefs, car("a"), choice).copy(mode = SoundMode.BASS, enabled = false)
        CarDspPreferences.save(prefs, choice)

        choice = CarDspPreferences.select(prefs, null, choice)
        assertEquals(SoundMode.VOCAL, choice.mode)
        assertEquals(false, choice.enabled)
        assertNull(choice.profileId)
        assertEquals(emptyList<PeqBand>(), choice.correction)
    }

    @Test
    fun renameKeepsTheModeAndUpdatesTheActiveName() {
        val store = CarProfileStore(folder.root)
        store.save(car("a"))
        store.save(car("b"))
        val active = CarDspChoice(enabled = true, mode = SoundMode.LIVELY, profileName = "Car a", profileId = "a")

        val renamed = CarDspPreferences.rename(store, "a", "Weekend MU-X", active)
        assertEquals("Weekend MU-X", store.get("a")?.name)
        assertEquals("Weekend MU-X", renamed.profileName)
        assertEquals(SoundMode.LIVELY, renamed.mode)

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
