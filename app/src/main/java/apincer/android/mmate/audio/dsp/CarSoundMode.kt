package apincer.android.mmate.audio.dsp

import android.content.Context
import apincer.android.mmate.audio.calibration.CarProfile
import apincer.android.mmate.audio.calibration.CarProfileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Taste layered on top of the car correction (docs/technical/CAR_AUDIO_DSP.md, section 5): at most
 * three filters and +4 dB per mode, no compression. The automatic preamp absorbs every boost.
 */
enum class CarSoundMode(val label: String, val summary: String) {
    REFERENCE("Reference", "Balanced, the car target"),
    BASS("Bass", "Deeper, punchier low end"),
    VOCAL("Vocal", "Voices forward and clear"),
    DRIVE("Drive", "Lively: tight bass, crisp attack, sparkle"),
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
        CUSTOM -> listOfNotNull(
            PeqBand(PeqBand.Type.LOW_SHELF, 100.0, bassDb.coerceIn(-TRIM_DB, TRIM_DB), SHELF_Q).takeIf { bassDb != 0.0 },
            PeqBand(PeqBand.Type.HIGH_SHELF, 8_000.0, trebleDb.coerceIn(-TRIM_DB, TRIM_DB), SHELF_Q).takeIf { trebleDb != 0.0 }
        )
    }

    companion object {
        const val TRIM_DB = 3.0
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
    val profileName: String? = null
) {
    /** The car correction with the mode on top. */
    fun settings(): CarDspSettings? = if (enabled) CarDspSettings(correction + mode.bands(bassDb, trebleDb)) else null
}

/** Keeps the choice in preferences and hands it to [CarDsp] whenever it changes. */
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
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val profile = prefs.getString(ACTIVE_PROFILE, null)?.let { profiles(context).get(it) }
        apply(CarDspChoice(
            enabled = prefs.getBoolean(ENABLED, false),
            mode = CarSoundMode.entries.firstOrNull { it.name == prefs.getString(MODE, null) } ?: CarSoundMode.REFERENCE,
            bassDb = prefs.getFloat(BASS, 0f).toDouble(),
            trebleDb = prefs.getFloat(TREBLE, 0f).toDouble(),
            correction = profile?.bands ?: emptyList(),
            profileName = profile?.name
        ))
    }

    @JvmStatic
    fun profiles(context: Context) = CarProfileStore(File(context.filesDir, "car_profiles"))

    /** Makes [profile]'s correction the active one; null goes back to no correction. */
    @JvmStatic
    fun setActiveProfile(context: Context, profile: CarProfile?) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(ACTIVE_PROFILE, profile?.id).apply()
        apply(choice.value.copy(correction = profile?.bands ?: emptyList(), profileName = profile?.name))
    }

    /** Deletes a profile; if it was active, the newest remaining one takes over. */
    @JvmStatic
    fun deleteProfile(context: Context, id: String) {
        val store = profiles(context)
        store.delete(id)
        val active = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(ACTIVE_PROFILE, null)
        if (active == id) setActiveProfile(context, store.list().firstOrNull())
    }

    /** The id of the active profile, if any. */
    @JvmStatic
    fun activeProfileId(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(ACTIVE_PROFILE, null)

    @JvmStatic
    fun update(context: Context, next: CarDspChoice) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(ENABLED, next.enabled)
            .putString(MODE, next.mode.name)
            .putFloat(BASS, next.bassDb.toFloat())
            .putFloat(TREBLE, next.trebleDb.toFloat())
            .apply()
        apply(next)
    }

    private var loaded = false

    private fun apply(next: CarDspChoice) {
        choice.value = next
        CarDsp.target = next.settings()
    }
}
