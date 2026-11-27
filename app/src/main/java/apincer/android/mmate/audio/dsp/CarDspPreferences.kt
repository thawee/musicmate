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
    /** Replaces [correction] on two-channel tracks when the car was measured side by side. */
    val stereo: StereoCorrection? = null,
    val profileName: String? = null,
    val profileId: String? = null,
    val nightListening: Boolean = false,
    val quietListening: Double = 0.0
) {
    /** The car correction with the mode on top. */
    fun settings(comparingCustom: Boolean = false): CarDspSettings? {
        if (!enabled) return null
        val personal = if (comparingCustom && mode == SoundMode.CUSTOM) emptyList() else mode.bands(bassDb, trebleDb)
        return (stereo?.settings(correction) ?: CarDspSettings(correction))
            .with(personal + QuietListening.bands(quietListening), compress = nightListening)
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
    private const val NIGHT = "night_listening"
    private const val QUIET = "quiet_listening"
    private const val ACTIVE_PROFILE = "active_profile"

    private var modeComparison: Boolean? = null

    @Synchronized
    fun setModeComparison(hearCorrection: Boolean?) {
        comparison.value = false
        modeComparison = hearCorrection.takeIf { choice.value.enabled }
        val selected = choice.value.settings()
        CarDsp.target = if (modeComparison != null && selected != null)
            ModeLevelMatch.select(selected, choice.value.copy(mode = SoundMode.REFERENCE).settings()!!, modeComparison!!)
        else selected
    }

    private var previewing = false
    private var savedChoice = CarDspChoice()
    private val choice = MutableStateFlow(savedChoice)
    val state: StateFlow<CarDspChoice> = choice
    private val comparison = MutableStateFlow(false)
    val comparingCustom: StateFlow<Boolean> = comparison

    @JvmStatic
    @Synchronized
    fun setCustomComparison(comparing: Boolean) {
        modeComparison = null
        comparison.value = comparing && choice.value.enabled && choice.value.mode == SoundMode.CUSTOM
        CarDsp.target = choice.value.settings(comparison.value)
    }

    /** Slider preview is transient until update saves the completed gesture. */
    @JvmStatic
    @Synchronized
    fun previewCustomTrims(bassDb: Double, trebleDb: Double) {
        require(bassDb.isFinite() && trebleDb.isFinite())
        modeComparison = null
        comparison.value = false
        previewing = true
        val next = choice.value.copy(mode = SoundMode.CUSTOM,
            bassDb = bassDb.coerceIn(-SoundMode.TRIM_DB, SoundMode.TRIM_DB),
            trebleDb = trebleDb.coerceIn(-SoundMode.TRIM_DB, SoundMode.TRIM_DB))
        choice.value = next
        CarDsp.target = next.settings()
    }

    @Synchronized
    internal fun onRouteChanged() {
        if (previewing || comparison.value || modeComparison != null) apply(savedChoice)
    }

    @JvmStatic
    @Synchronized
    fun clearCustomPreview() {
        if (previewing) apply(savedChoice)
    }

    /** Loads the saved choice once per process and applies it. */
    @JvmStatic
    @Synchronized
    fun init(context: Context) {
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
        apply(select(prefs(context), profile, savedChoice))
    }

    /** Deletes a profile and its saved mode; if it was active, the newest remaining one takes over. */
    @JvmStatic
    fun deleteProfile(context: Context, id: String) {
        val store = profiles(context)
        store.delete(id)
        val prefs = prefs(context)
        prefs.edit().remove(key(MODE, id)).remove(key(BASS, id)).remove(key(TREBLE, id)).remove(key(NIGHT, id))
            .remove(key(QUIET, id)).apply()
        if (prefs.getString(ACTIVE_PROFILE, null) == id) setActiveProfile(context, store.list().firstOrNull())
    }

    /** Gives a saved profile a new name; the chip follows at once when it is the active one. */
    @JvmStatic
    fun renameProfile(context: Context, id: String, name: String) {
        apply(rename(profiles(context), id, name, savedChoice))
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
        val normalized = next.copy(quietListening = QuietListening.normalize(next.quietListening))
        save(prefs(context), normalized)
        apply(normalized)
    }

    @JvmStatic
    fun setQuietListening(context: Context, strength: Double) {
        update(context, savedChoice.copy(quietListening = strength))
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
            "SLEEP", "NIGHT" -> SoundMode.REFERENCE
            "BASS" -> SoundMode.CUSTOM
            "FOCUS" -> SoundMode.RELAX
            else -> SoundMode.entries.firstOrNull { it.name == savedMode } ?: current.mode
        }
        val legacyNight = savedMode == "NIGHT" || savedMode == "SLEEP"
        val legacyBass = savedMode == "BASS"
        val next = current.copy(
            mode = mode,
            bassDb = if (legacyBass) 4.0 else if (prefs.contains(key(BASS, id))) prefs.getFloat(key(BASS, id), 0f).toDouble() else current.bassDb,
            trebleDb = if (prefs.contains(key(TREBLE, id))) prefs.getFloat(key(TREBLE, id), 0f).toDouble() else current.trebleDb,
            correction = profile?.bands ?: emptyList(),
            stereo = profile?.stereo,
            profileName = profile?.name,
            profileId = id,
            nightListening = legacyNight || prefs.getBoolean(key(NIGHT, id), false),
            quietListening = QuietListening.normalize(prefs.getFloat(key(QUIET, id), 0f).toDouble())
        )
        if (savedMode in listOf("DRIVE", "SLEEP", "FOCUS", "NIGHT", "BASS")) save(prefs, next)
        return next
    }

    internal fun save(prefs: SharedPreferences, next: CarDspChoice) {
        val id = next.profileId
        prefs.edit()
            .putBoolean(ENABLED, next.enabled)
            .putBoolean(key(NIGHT, id), next.nightListening)
            .putFloat(key(QUIET, id), QuietListening.normalize(next.quietListening).toFloat())
            .putString(key(MODE, id), next.mode.name)
            .putFloat(key(BASS, id), next.bassDb.toFloat())
            .putFloat(key(TREBLE, id), next.trebleDb.toFloat())
            .apply()
    }

    private fun key(name: String, profileId: String?) = if (profileId == null) name else "$name:$profileId"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private var loaded = false

    private fun apply(next: CarDspChoice) {
        modeComparison = null
        comparison.value = false
        previewing = false
        savedChoice = next
        choice.value = next
        CarDsp.target = next.settings()
    }
}
