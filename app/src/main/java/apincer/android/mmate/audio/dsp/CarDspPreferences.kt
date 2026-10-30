package apincer.android.mmate.audio.dsp

import android.content.Context
import android.content.SharedPreferences
import apincer.android.mmate.audio.calibration.CarProfile
import apincer.android.mmate.audio.calibration.CarProfileStore
import apincer.android.mmate.audio.headphones.HeadphonePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** The listener's Car DSP choice, as saved and shown on the Now Playing chip. */
data class CarDspChoice(
    val enabled: Boolean = false,
    val mode: SoundMode = SoundMode.REFERENCE,
    val bassDb: Double = 0.0,
    val trebleDb: Double = 0.0,
    /** The active car profile's correction; empty until a car is calibrated. */
    val correction: List<PeqBand> = emptyList(),
    val profileName: String? = null,
    val profileId: String? = null
) {
    /** The car correction with the mode on top. */
    fun settings(): CarDspSettings? {
        val effective = AutoSoundPreferences.effectiveMode(mode)
        return if (enabled) CarDspSettings(correction + effective.bands(bassDb, trebleDb), compress = effective.compresses) else null
    }
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
        AutoSoundPreferences.init(context)
        HeadphonePreferences.init(context)
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
        val savedMode = prefs.getString(key(MODE, id), null)
        val mode = when (savedMode) {
            "DRIVE" -> SoundMode.LIVELY
            "SLEEP" -> SoundMode.NIGHT
            "FOCUS" -> SoundMode.RELAX
            else -> SoundMode.entries.firstOrNull { it.name == savedMode } ?: current.mode
        }
        if (savedMode in listOf("DRIVE", "SLEEP", "FOCUS")) {
            prefs.edit().putString(key(MODE, id), mode.name).apply()
        }
        return current.copy(
            mode = mode,
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
        refreshSoundMode()
    }

    internal fun refreshSoundMode() {
        CarDsp.target = choice.value.settings()
    }
}
