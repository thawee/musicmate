package apincer.android.mmate.audio.headphones

import android.media.AudioDeviceInfo
import apincer.android.mmate.audio.UsbOutputFormat
import apincer.android.mmate.audio.dsp.CarDspSettings
import apincer.android.mmate.audio.dsp.PeqBand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

data class HeadphoneEndpoint(val key: String, val name: String, val usb: Boolean, val wired: Boolean = false) {
    companion object {
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
            if (type in setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET)) {
                if (address.isNullOrBlank()) return null
                return HeadphoneEndpoint("bt:${address.uppercase(Locale.ROOT)}", name.ifBlank { "Bluetooth output" }, usb = false)
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
        val type = it.type
        if (UsbOutputFormat.isUsbType(type) || type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        ) Triple(type, it.address, it.productName?.toString()) else Triple(type, null, null)
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
        val settings = if (active) {
            val personal = buildList {
                if (binding.bassDb != 0.0) add(PeqBand(PeqBand.Type.LOW_SHELF, 100.0, binding.bassDb, 0.707))
                if (binding.trebleDb != 0.0) add(PeqBand(PeqBand.Type.HIGH_SHELF, 8_000.0, binding.trebleDb, 0.707))
            }
            profile.manual?.settings() ?: CarDspSettings(profile.bands + personal, requestedPreampDb = profile.preampDb)
        } else null
        selection = Selection(handles, settings, devices.isNotEmpty() && devices.none { UsbOutputFormat.isUsbType(it.first) })
        status.value = HeadphoneStatus(endpoint, binding ?: HeadphoneBinding(), profile, active)
    }
}
