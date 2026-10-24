package apincer.android.mmate.audio.dsp

import android.content.Context
import android.content.SharedPreferences
import apincer.android.mmate.audio.calibration.CarProfile
import apincer.android.mmate.audio.calibration.CarProfileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Taste layered on top of the car correction (docs/technical/CAR_AUDIO_DSP.md, section 5): at most
 * three filters and +4 dB per mode. The automatic preamp absorbs every boost. Only [SLEEP]
 * compresses, so a loud moment does not wake the listener.
 */
enum class CarSoundMode(val label: String, val summary: String, val compresses: Boolean = false) {
    REFERENCE("Reference", "Audiophile: the pure tuning target"),
    BASS("Bass", "Deeper, punchier low end"),
    VOCAL("Vocal", "Voices forward and clear"),
    DRIVE("Drive", "Lively: tight bass, crisp attack, sparkle"),
    RELAX("Relax", "Softer highs, warm and easy"),
    FOCUS("Focus", "Voices set back for work or study"),
    SLEEP("Sleep", "Soft and even, loud moments tamed", compresses = true),
    CUSTOM("Custom", "Your own bass and treble");

    /** Bands this mode adds; [bassDb] and [trebleDb] apply to [CUSTOM] only. */
    fun bands(bassDb: Double = 0.0, trebleDb: Double = 0.0): List<PeqBand> = when (this) {
        REFERENCE -> emptyList()
        BASS -> listOf(
            PeqBand(PeqBand.Type.LOW_SHELF, 80.0, 4.0, SHELF_Q),
            PeqBand(PeqBand.Type.PEAK, 250.0, -1.0, 1.0)
        )
        VOCAL -> listOf(
            PeqBand(PeqBand.Type.PEAK, 2_500.0, 2.5, 1.0),
            PeqBand(PeqBand.Type.LOW_SHELF, 120.0, -1.5, SHELF_Q),
            PeqBand(PeqBand.Type.PEAK, 300.0, -1.0, 1.0)
        )
        DRIVE -> listOf(
            PeqBand(PeqBand.Type.LOW_SHELF, 70.0, 3.0, SHELF_Q),
            PeqBand(PeqBand.Type.PEAK, 3_000.0, 1.5, 1.0),
            PeqBand(PeqBand.Type.HIGH_SHELF, 8_000.0, 2.0, SHELF_Q)
        )
        RELAX -> listOf(
            PeqBand(PeqBand.Type.HIGH_SHELF, 6_000.0, -2.5, SHELF_Q),
            PeqBand(PeqBand.Type.PEAK, 3_000.0, -1.5, 1.0),
            PeqBand(PeqBand.Type.LOW_SHELF, 150.0, 1.5, SHELF_Q)
        )
        FOCUS -> listOf(
            PeqBand(PeqBand.Type.PEAK, 2_500.0, -2.0, 0.8),
            PeqBand(PeqBand.Type.HIGH_SHELF, 8_000.0, -1.0, SHELF_Q)
        )
        SLEEP -> listOf(
            PeqBand(PeqBand.Type.HIGH_SHELF, 4_000.0, -4.0, SHELF_Q),
            PeqBand(PeqBand.Type.LOW_SHELF, 60.0, -2.0, SHELF_Q)
        )
        CUSTOM -> listOfNotNull(
            PeqBand(PeqBand.Type.LOW_SHELF, 100.0, bassDb.coerceIn(-TRIM_DB, TRIM_DB), SHELF_Q).takeIf { bassDb != 0.0 },
            PeqBand(PeqBand.Type.HIGH_SHELF, 8_000.0, trebleDb.coerceIn(-TRIM_DB, TRIM_DB), SHELF_Q).takeIf { trebleDb != 0.0 }
        )
    }

    companion object {
        const val TRIM_DB = 4.0
        private const val SHELF_Q = 0.707
    }
}

/** The listener's Car DSP choice, as saved and shown on the Now Playing chip. */
data class CarDspChoice(
    val enabled: Boolean = false,
    val mode: CarSoundMode = CarSoundMode.REFERENCE,
    val bassDb: Double = 0.0,
    val trebleDb: Double = 0.0,
    /** The active car profile's correction; empty until a car is calibrated. */
    val correction: List<PeqBand> = emptyList(),
    val profileName: String? = null,
    val profileId: String? = null
) {
    /** The car correction with the mode on top. */
    fun settings(): CarDspSettings? =
        if (enabled) CarDspSettings(correction + mode.bands(bassDb, trebleDb), compress = mode.compresses) else null
}

/**
 * Keeps the choice in preferences and hands it to [CarDsp] whenever it changes. On/off is one
 * switch for all cars; the sound mode and Custom trims are kept per car (keys suffixed with the
 * profile id), and "No correction" has its own set.
 */
object CarDspPreferences {
    private const val FILE = "car_dsp"
    private const val ENABLED = "enabled"
    private const val MODE = "mode"
    private const val BASS = "custom_bass_db"
    private const val TREBLE = "custom_treble_db"
    private const val ACTIVE_PROFILE = "active_profile"

    private val choice = MutableStateFlow(CarDspChoice())
    val state: StateFlow<CarDspChoice> = choice

    /** Loads the saved choice once per process and applies it. */
    @JvmStatic
    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = prefs(context)
        val profile = prefs.getString(ACTIVE_PROFILE, null)?.let { profiles(context).get(it) }
        // Load "No correction"'s set first so a car with none saved starts from it
        val base = load(prefs, null, CarDspChoice(enabled = prefs.getBoolean(ENABLED, false)))
        apply(if (profile == null) base else load(prefs, profile, base))
    }

    @JvmStatic
    fun profiles(context: Context) = CarProfileStore(File(context.filesDir, "car_profiles"))

    /**
     * Makes [profile]'s correction the active one, with the mode last used in that car; a car with
     * none saved keeps the current mode. Null goes back to no correction.
     */
    @JvmStatic
    fun setActiveProfile(context: Context, profile: CarProfile?) {
        apply(select(prefs(context), profile, choice.value))
    }

    /** Deletes a profile and its saved mode; if it was active, the newest remaining one takes over. */
    @JvmStatic
    fun deleteProfile(context: Context, id: String) {
        val store = profiles(context)
        store.delete(id)
        val prefs = prefs(context)
        prefs.edit().remove(key(MODE, id)).remove(key(BASS, id)).remove(key(TREBLE, id)).apply()
        if (prefs.getString(ACTIVE_PROFILE, null) == id) setActiveProfile(context, store.list().firstOrNull())
    }

    /** Gives a saved profile a new name; the chip follows at once when it is the active one. */
    @JvmStatic
    fun renameProfile(context: Context, id: String, name: String) {
        apply(rename(profiles(context), id, name, choice.value))
    }

    /** Its mode stays, since modes are keyed by id, not name. */
    internal fun rename(store: CarProfileStore, id: String, name: String, current: CarDspChoice): CarDspChoice {
        val profile = store.get(id) ?: return current
        store.save(profile.copy(name = name))
        return if (current.profileId == id) current.copy(profileName = name) else current
    }

    /** The id of the active profile, if any. */
    @JvmStatic
    fun activeProfileId(context: Context): String? = prefs(context).getString(ACTIVE_PROFILE, null)

    /** Saves on/off for every car and the mode for the active car only. */
    @JvmStatic
    fun update(context: Context, next: CarDspChoice) {
        save(prefs(context), next)
        apply(next)
    }

    internal fun select(prefs: SharedPreferences, profile: CarProfile?, current: CarDspChoice): CarDspChoice {
        prefs.edit().putString(ACTIVE_PROFILE, profile?.id).apply()
        val next = load(prefs, profile, current)
        save(prefs, next)
        return next
    }

    internal fun load(prefs: SharedPreferences, profile: CarProfile?, current: CarDspChoice): CarDspChoice {
        val id = profile?.id
        return current.copy(
            mode = CarSoundMode.entries.firstOrNull { it.name == prefs.getString(key(MODE, id), null) } ?: current.mode,
            bassDb = if (prefs.contains(key(BASS, id))) prefs.getFloat(key(BASS, id), 0f).toDouble() else current.bassDb,
            trebleDb = if (prefs.contains(key(TREBLE, id))) prefs.getFloat(key(TREBLE, id), 0f).toDouble() else current.trebleDb,
            correction = profile?.bands ?: emptyList(),
            profileName = profile?.name,
            profileId = id
        )
    }

    internal fun save(prefs: SharedPreferences, next: CarDspChoice) {
        val id = next.profileId
        prefs.edit()
            .putBoolean(ENABLED, next.enabled)
            .putString(key(MODE, id), next.mode.name)
            .putFloat(key(BASS, id), next.bassDb.toFloat())
            .putFloat(key(TREBLE, id), next.trebleDb.toFloat())
            .apply()
    }

    private fun key(name: String, profileId: String?) = if (profileId == null) name else "$name:$profileId"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private var loaded = false

    private fun apply(next: CarDspChoice) {
        choice.value = next
        CarDsp.target = next.settings()
    }
}
