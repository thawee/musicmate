package apincer.android.mmate.audio.dsp

import android.content.Context
import apincer.android.mmate.audio.headphones.HeadphoneDsp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Global selector only: never changes saved manual modes, correction enablement, or USB consent. */
object AutoSoundPreferences {
    private const val FILE = "auto_sound_mode"
    private const val CONFIG = "config"
    private val json = Json { ignoreUnknownKeys = true }
    private val mutableState = MutableStateFlow(AutoSoundState())
    val state: StateFlow<AutoSoundState> = mutableState
    private var loaded = false
    private var genre: String? = null
    private var style: String? = null

    @JvmStatic @Synchronized
    fun init(context: Context) {
        if (loaded) return
        loaded = true
        publish(decode(prefs(context).getString(CONFIG, null)))
    }

    @Synchronized
    fun setEnabled(context: Context, enabled: Boolean) {
        init(context)
        saveSettings(context, state.value.config.copy(enabled = enabled))
    }

    @Synchronized
    fun saveSettings(context: Context, config: AutoSoundConfig) {
        validate(config)
        init(context)
        prefs(context).edit().putString(CONFIG, encode(config)).apply()
        publish(config, held = false)
    }

    @Synchronized
    fun hold() {
        if (state.value.config.enabled) publish(state.value.config, held = true)
    }

    @Synchronized
    fun resume() = publish(state.value.config, held = false)

    @JvmStatic @Synchronized
    fun updateTrack(genre: String?, style: String?) {
        this.genre = genre
        this.style = style
        publish(state.value.config)
    }

    @JvmStatic @Synchronized
    fun resetSession() {
        genre = null
        style = null
        publish(state.value.config, held = false)
    }

    fun effectiveMode(manual: SoundMode): SoundMode = state.value.let {
        if (it.config.enabled) it.resolvedMode else manual
    }

    internal fun encode(config: AutoSoundConfig): String = json.encodeToString(config)

    internal fun decode(value: String?): AutoSoundConfig = try {
        if (value == null) AutoSoundConfig() else json.decodeFromString<AutoSoundConfig>(value).also(::validate)
    } catch (_: IllegalArgumentException) {
        AutoSoundConfig()
    }

    private fun validate(config: AutoSoundConfig) {
        require(config.rules.size <= 50) { "Use at most 50 Auto rules." }
        require(config.rules.all { it.value.isNotBlank() && it.value.length <= 80 }) { "Each Auto tag must contain 1–80 characters." }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun publish(config: AutoSoundConfig, held: Boolean = state.value.held) {
        val previous = state.value
        val freeze = held && config.enabled
        val resolution = if (freeze) AutoSoundResolution(previous.resolvedMode, previous.reason)
            else AutoSoundMode.resolve(config, genre, style)
        mutableState.value = AutoSoundState(config, freeze, resolution.mode, resolution.reason)
        if (previous.config.enabled != config.enabled || previous.resolvedMode != resolution.mode) {
            HeadphoneDsp.refreshSoundMode()
            CarDspPreferences.refreshSoundMode()
        }
    }
}
