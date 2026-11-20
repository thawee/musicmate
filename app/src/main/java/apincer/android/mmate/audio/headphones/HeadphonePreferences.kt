package apincer.android.mmate.audio.headphones

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import apincer.android.mmate.audio.dsp.SoundMode
import apincer.android.mmate.audio.dsp.CarDsp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Headphone choices are independent of speaker measurements, sound modes and their preferences. */
object HeadphonePreferences {
    private val library = MutableStateFlow(HeadphoneLibrary())
    val state: StateFlow<HeadphoneLibrary> = library
    private var loaded = false

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        val saved = store(context).load()
        library.value = saved
        HeadphoneDsp.updateLibrary(saved)
        loaded = true
    }

    fun refreshRoute(context: Context) {
        val media = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        try {
            CarDsp.updateRoute(context.getSystemService(AudioManager::class.java).getAudioDevicesForAttributes(media))
        } catch (error: RuntimeException) {
            CarDsp.updateRoute(emptyList())
            throw error
        }
    }

    @Synchronized
    fun import(context: Context, profile: HeadphoneProfile) = update(context) {
        it.copy(profiles = it.profiles.filterNot { saved -> saved.id == profile.id } + profile)
    }

    @Synchronized
    fun saveManual(context: Context, profile: HeadphoneProfile, endpoint: HeadphoneEndpoint? = null) = update(context) { saved ->
        require(profile.manual != null) { "Choose a listening profile." }
        val old = saved.profiles.firstOrNull { it.id == profile.id }
        require(old == null || old.manual != null) { "Imported profiles cannot be replaced with listening profiles." }
        val profiles = saved.profiles.filterNot { it.id == profile.id } + profile
        val bindings = if (endpoint == null) saved.bindings else {
            val binding = saved.bindings[endpoint.key] ?: HeadphoneBinding()
            val retainEnabled = binding.profileId == profile.id && binding.enabled
            // Saving is the user asking for this EQ; USB alone keeps its explicit consent switch
            saved.bindings + (endpoint.key to binding.copy(profileId = profile.id, enabled = retainEnabled || !endpoint.usb,
                usbEqAllowed = retainEnabled && binding.usbEqAllowed, bassDb = 0.0, trebleDb = 0.0))
        }
        saved.copy(profiles = profiles, bindings = bindings)
    }

    @Synchronized
    fun select(context: Context, endpoint: HeadphoneEndpoint, profileId: String?) = update(context) {
        require(profileId == null || it.profiles.any { profile -> profile.id == profileId }) { "Choose a headphone profile." }
        val old = it.bindings[endpoint.key] ?: HeadphoneBinding()
        it.copy(bindings = it.bindings + (endpoint.key to old.copy(profileId = profileId)))
    }

    /** The USB control explicitly tells the user that enabling EQ changes the samples. */
    @Synchronized
    fun setEnabled(context: Context, endpoint: HeadphoneEndpoint, enabled: Boolean) = update(context) {
        val old = it.bindings[endpoint.key] ?: HeadphoneBinding()
        it.copy(bindings = it.bindings + (endpoint.key to old.copy(enabled = enabled, usbEqAllowed = endpoint.usb && enabled)))
    }

    @Synchronized
    fun adjust(context: Context, endpoint: HeadphoneEndpoint, bassDb: Double, trebleDb: Double) = update(context) {
        val old = it.bindings[endpoint.key] ?: HeadphoneBinding()
        it.copy(bindings = it.bindings + (endpoint.key to old.copy(bassDb = bassDb, trebleDb = trebleDb, soundMode = SoundMode.CUSTOM)))
    }

    @Synchronized
    fun setSoundMode(context: Context, endpoint: HeadphoneEndpoint, mode: SoundMode) = update(context) {
        val old = it.bindings[endpoint.key] ?: HeadphoneBinding()
        it.copy(bindings = it.bindings + (endpoint.key to old.copy(soundMode = mode)))
    }

    @Synchronized
    fun setNightListening(context: Context, endpoint: HeadphoneEndpoint, enabled: Boolean) = update(context) {
        val old = it.bindings[endpoint.key] ?: HeadphoneBinding()
        it.copy(bindings = it.bindings + (endpoint.key to old.copy(nightListening = enabled)))
    }

    @Synchronized
    fun setCrossfeed(context: Context, endpoint: HeadphoneEndpoint, enabled: Boolean) = update(context) {
        val old = it.bindings[endpoint.key] ?: HeadphoneBinding()
        it.copy(bindings = it.bindings + (endpoint.key to old.copy(crossfeed = enabled)))
    }

    @Synchronized
    fun delete(context: Context, id: String) = update(context) { saved ->
        saved.copy(
            profiles = saved.profiles.filterNot { it.id == id },
            bindings = saved.bindings.mapValues { (_, binding) ->
                if (binding.profileId == id) binding.copy(profileId = null) else binding
            }
        )
    }

    private fun update(context: Context, change: (HeadphoneLibrary) -> HeadphoneLibrary) {
        init(context)
        val next = change(library.value)
        store(context).save(next)
        library.value = next
        HeadphoneDsp.updateLibrary(next)
    }

    private fun store(context: Context) = HeadphoneProfileStore(File(context.filesDir, "headphone_profiles.json"))
}
