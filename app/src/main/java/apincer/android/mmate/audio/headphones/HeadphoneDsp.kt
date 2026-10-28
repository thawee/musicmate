package apincer.android.mmate.audio.headphones

import android.media.AudioDeviceInfo
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import apincer.android.mmate.audio.calibration.CalibrationOutput
import apincer.android.mmate.audio.UsbOutputFormat
import apincer.android.mmate.audio.dsp.CarDspSettings
import apincer.android.mmate.audio.dsp.SoundMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

data class HeadphoneEndpoint(val key: String, val name: String, val usb: Boolean, val wired: Boolean = false,
                             val speaker: Boolean = false) {
    val output: CalibrationOutput get() = if (speaker) CalibrationOutput.SPEAKER else CalibrationOutput.HEADPHONE
    /** Bluetooth and USB identify a transport; the listener identifies what it drives. */
    fun supports(output: CalibrationOutput): Boolean = usb || key.startsWith("bt:") || this.output == output
    companion object {
        fun current(context: Context): HeadphoneEndpoint? {
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            return of(context.getSystemService(AudioManager::class.java).getAudioDevicesForAttributes(attributes).map {
                Triple(it.type, it.address, it.productName?.toString())
            })
        }
        /** A mixed route is deliberately ineligible: the same PCM would reach both outputs. */
        fun of(devices: List<Triple<Int, String?, String?>>): HeadphoneEndpoint? {
            val (type, address, productName) = devices.singleOrNull() ?: return null
            val name = productName?.trim().orEmpty()
            if (UsbOutputFormat.isUsbType(type)) {
                if (name.isBlank() || address.isNullOrBlank()) return null
                return HeadphoneEndpoint("usb:$address:$name", name, usb = true)
            }
            if (type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                return HeadphoneEndpoint("wired", name.ifBlank { "Wired headphones" }, usb = false, wired = true)
            }
            if (type in setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST)) {
                if (address.isNullOrBlank()) return null
                return HeadphoneEndpoint("bt:${address.uppercase(Locale.ROOT)}", name.ifBlank { "Bluetooth output" }, usb = false,
                    speaker = type == AudioDeviceInfo.TYPE_BLE_SPEAKER || type == AudioDeviceInfo.TYPE_BLE_BROADCAST)
            }
            if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE) {
                return HeadphoneEndpoint("speaker:built-in", name.ifBlank { "Phone speaker" }, usb = false, speaker = true)
            }
            if (type == AudioDeviceInfo.TYPE_REMOTE_SUBMIX || type == AudioDeviceInfo.TYPE_BUS ||
                type == AudioDeviceInfo.TYPE_LINE_ANALOG || type == AudioDeviceInfo.TYPE_LINE_DIGITAL) {
                // Anonymous remote routes cannot safely distinguish two external speakers.
                if (address.isNullOrBlank()) return null
                return HeadphoneEndpoint("speaker:$type:$address", name.ifBlank { "Speaker output" }, usb = false, speaker = true)
            }
            return null
        }
    }
}

data class HeadphoneStatus(
    val endpoint: HeadphoneEndpoint? = null,
    val binding: HeadphoneBinding = HeadphoneBinding(),
    val profile: HeadphoneProfile? = null,
    val active: Boolean = false
)

/** Route changes and settings publish one decision; audio blocks never read preferences or files. */
object HeadphoneDsp {
    data class Selection(val handlesRoute: Boolean, val settings: CarDspSettings?, val speakerEligible: Boolean = false)

    @Volatile
    private var selection = Selection(false, null)
    val current: Selection get() = selection
    private val status = MutableStateFlow(HeadphoneStatus())
    val state: StateFlow<HeadphoneStatus> = status
    private var devices = emptyList<Triple<Int, String?, String?>>()
    private var library = HeadphoneLibrary()

    fun updateRoute(routed: List<AudioDeviceInfo>) = updateDevices(routed.map {
        if (it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE)
            Triple(it.type, null, "Phone speaker")
        else Triple(it.type, it.address, it.productName?.toString())
    })

    @Synchronized
    internal fun updateDevices(next: List<Triple<Int, String?, String?>>) {
        if (devices == next) return
        devices = next.toList()
        publish()
    }

    @Synchronized
    fun updateLibrary(next: HeadphoneLibrary) {
        if (library == next) return
        library = next
        publish()
    }

    private fun publish() {
        val endpoint = HeadphoneEndpoint.of(devices)
        val binding = endpoint?.let { library.bindings[it.key] }
        val profile = library.profiles.firstOrNull { it.id == binding?.profileId }
        val handles = endpoint != null && (endpoint.usb || endpoint.wired || binding != null)
        val active = handles && binding?.enabled == true && profile != null && (!endpoint.usb || binding.usbEqAllowed)
        val settings = if (active) settingsFor(profile!!, binding!!) else null
        selection = Selection(handles, settings, devices.isNotEmpty() && devices.none { UsbOutputFormat.isUsbType(it.first) })
        status.value = HeadphoneStatus(endpoint, binding ?: HeadphoneBinding(), profile, active)
    }

    /** Shared by the playback decision and the curve preview. */
    fun settingsFor(profile: HeadphoneProfile, binding: HeadphoneBinding): CarDspSettings {
        val personal = if (binding.soundMode == SoundMode.CUSTOM)
            HeadphoneTone(bassDb = binding.bassDb, trebleDb = binding.trebleDb).bands()
        else binding.soundMode.bands()
        val correction = profile.manual?.settings()
            ?: CarDspSettings(profile.bands, requestedPreampDb = profile.preampDb)
        return CarDspSettings(correction.bands + personal, compress = binding.soundMode.compresses,
            requestedPreampDb = correction.requestedPreampDb, leftBands = correction.leftBands,
            rightBands = correction.rightBands, balanceDb = correction.balanceDb, monoBands = correction.monoBands)
    }
}
