package apincer.android.mmate.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import apincer.android.mmate.utils.AudioOutputHelper
import apincer.music.core.Settings
import apincer.music.core.model.Track

/**
 * UsbBitPerfectSession manages native Android 14+ USB bit-perfect mixer requests.
 *
 * Implements the lifecycle designed in tasks/usb-bit-perfect-design.md:
 * - Snapshots user preference from Settings
 * - Discovers connected USB audio output devices
 * - Matches the decoded stream format to the device's bit-perfect mixer attributes
 * - Handles USB hotplugging via AudioDeviceCallback (borrowed from liquid-music-android)
 * - Safely rolls back and clears preferred mixer attributes on detach, error, or shutdown
 */
class UsbBitPerfectSession(private val context: Context) {

    companion object {
        private const val TAG = "UsbBitPerfectSession"
    }

    enum class State {
        DISABLED,
        UNAVAILABLE,
        PENDING,
        REQUESTED
    }

    data class Status(
        val state: State,
        val deviceId: Int = -1,
        val deviceName: String? = null,
        val sampleRate: Int = 0,
        val encoding: Int = 0,
        val reason: String = ""
    ) {
        val isRequested: Boolean get() = state == State.REQUESTED

        companion object {
            @JvmStatic
            fun disabled(): Status = Status(
                state = State.DISABLED,
                reason = "USB bit-perfect disabled in Settings"
            )

            @JvmStatic
            fun unavailable(reason: String, deviceId: Int = -1, deviceName: String? = null): Status = Status(
                state = State.UNAVAILABLE,
                deviceId = deviceId,
                deviceName = deviceName,
                reason = reason
            )

            @JvmStatic
            fun pending(deviceId: Int, deviceName: String?, sampleRate: Int): Status = Status(
                state = State.PENDING,
                deviceId = deviceId,
                deviceName = deviceName,
                sampleRate = sampleRate,
                reason = "Configuring USB bit-perfect output..."
            )

            @JvmStatic
            fun requested(
                deviceId: Int,
                deviceName: String?,
                sampleRate: Int,
                encoding: Int,
                reason: String = ""
            ): Status = Status(
                state = State.REQUESTED,
                deviceId = deviceId,
                deviceName = deviceName,
                sampleRate = sampleRate,
                encoding = encoding,
                reason = if (reason.isNotEmpty()) reason else "Bit-perfect requested (${sampleRate}Hz)"
            )
        }
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val mediaAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    @Volatile
    private var currentStatus: Status = Status.disabled()

    private var activeDeviceId: Int = -1
    private var activeMixerAttributes: Any? = null // AudioMixerAttributes on API 34+

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refresh()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            if (removedDevices != null && removedDevices.any { it.id == activeDeviceId }) {
                Log.i(TAG, "Active USB DAC disconnected; clearing preferred mixer")
                clearMixerPreference()
            }
            refresh()
        }
    }

    fun start() {
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
        refresh()
    }

    fun release() {
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        clearMixerPreference()
        currentStatus = Status.disabled()
    }

    fun getStatus(): Status = currentStatus

    fun isBitPerfectActive(): Boolean = currentStatus.isRequested

    fun hasActiveMixerPreference(): Boolean = activeDeviceId != -1

    @Synchronized
    fun refresh() {
        if (!Settings.isUsbBitPerfectEnabled(context)) {
            if (activeDeviceId != -1) {
                clearMixerPreference()
            }
            currentStatus = Status.disabled()
            return
        }

        val usbDevice = getConnectedUsbDevice()
        if (usbDevice == null) {
            if (activeDeviceId != -1) {
                clearMixerPreference()
            }
            currentStatus = Status.unavailable("No USB DAC connected")
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            currentStatus = Status.unavailable(
                "USB bit-perfect requires Android 14+",
                usbDevice.id,
                usbDevice.productName?.toString()
            )
            return
        }

        if (activeDeviceId == usbDevice.id && currentStatus.isRequested) {
            // Already active on this device
            return
        }

        val deviceName = usbDevice.productName?.toString() ?: "USB Audio"
        currentStatus = Status.pending(usbDevice.id, deviceName, 0)
    }

    @Synchronized
    fun onTrackStarting(track: Track?) {
        if (!Settings.isUsbBitPerfectEnabled(context)) {
            if (activeDeviceId != -1) {
                clearMixerPreference()
            }
            currentStatus = Status.disabled()
            return
        }

        val usbDevice = getConnectedUsbDevice()
        if (usbDevice == null) {
            currentStatus = Status.unavailable("No USB DAC connected")
            return
        }

        val sampleRate = if (track != null && track.audioSampleRate > 0) track.audioSampleRate.toInt() else 44100
        val deviceName = usbDevice.productName?.toString() ?: "USB Audio"
        currentStatus = Status.pending(usbDevice.id, deviceName, sampleRate)
    }

    @Synchronized
    fun prepareMixerPreference(targetSampleRate: Int, targetChannelMask: Int, targetEncoding: Int): Boolean {
        if (!Settings.isUsbBitPerfectEnabled(context)) {
            if (activeDeviceId != -1) {
                clearMixerPreference()
            }
            currentStatus = Status.disabled()
            return false
        }

        val usbDevice = getConnectedUsbDevice()
        if (usbDevice == null) {
            clearMixerPreference()
            currentStatus = Status.unavailable("No USB DAC connected")
            return false
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            clearMixerPreference()
            currentStatus = Status.unavailable(
                "USB bit-perfect requires Android 14+",
                usbDevice.id,
                usbDevice.productName?.toString()
            )
            return false
        }

        return configurePreferredMixerApi34(usbDevice, targetSampleRate, targetChannelMask, targetEncoding)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @SuppressLint("WrongConstant")
    private fun configurePreferredMixerApi34(
        device: AudioDeviceInfo,
        targetSampleRate: Int,
        targetChannelMask: Int,
        targetEncoding: Int
    ): Boolean {
        try {
            val supportedMixers = audioManager.getSupportedMixerAttributes(device)
            if (supportedMixers.isNullOrEmpty()) {
                clearMixerPreference()
                currentStatus = Status.unavailable(
                    "Device does not report custom mixer attributes",
                    device.id,
                    device.productName?.toString()
                )
                return false
            }

            val bitPerfectMixers = supportedMixers.filter {
                it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
            }

            if (bitPerfectMixers.isEmpty()) {
                clearMixerPreference()
                currentStatus = Status.unavailable(
                    "USB DAC driver does not offer bit-perfect mode",
                    device.id,
                    device.productName?.toString()
                )
                return false
            }

            // Find matching mixer format for the track's sample rate
            val matchedMixer = bitPerfectMixers.firstOrNull { mixer ->
                mixer.format.sampleRate == targetSampleRate
            }

            if (matchedMixer == null) {
                clearMixerPreference()
                currentStatus = Status.unavailable(
                    "USB DAC does not support bit-perfect at ${targetSampleRate}Hz",
                    device.id,
                    device.productName?.toString()
                )
                return false
            }

            val success = audioManager.setPreferredMixerAttributes(mediaAudioAttributes, device, matchedMixer)
            if (success) {
                activeDeviceId = device.id
                activeMixerAttributes = matchedMixer
                currentStatus = Status.requested(
                    deviceId = device.id,
                    deviceName = device.productName?.toString(),
                    sampleRate = targetSampleRate,
                    encoding = matchedMixer.format.encoding,
                    reason = "Bit-perfect requested (${targetSampleRate}Hz)"
                )
                Log.i(TAG, "Preferred bit-perfect mixer accepted for ${device.productName} at ${targetSampleRate}Hz")
                return true
            } else {
                clearMixerPreference()
                currentStatus = Status.unavailable(
                    "Android system rejected preferred mixer request",
                    device.id,
                    device.productName?.toString()
                )
                Log.w(TAG, "setPreferredMixerAttributes returned false for device ${device.id}")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring preferred mixer attributes", e)
            clearMixerPreference()
            currentStatus = Status.unavailable(
                "Mixer error: ${e.message}",
                device.id,
                device.productName?.toString()
            )
            return false
        }
    }

    @Synchronized
    fun onAudioTrackCreated(audioTrack: AudioTrack?) {
        if (audioTrack == null || !currentStatus.isRequested) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val routed = audioTrack.routedDevice
            if (routed != null) {
                if (isUsbDevice(routed)) {
                    Log.d(TAG, "AudioTrack confirmed routed to USB DAC: ${routed.productName} (id=${routed.id})")
                } else {
                    Log.w(TAG, "AudioTrack routed to unexpected device: ${AudioOutputHelper.typeToString(routed.type)}")
                    currentStatus = Status.unavailable(
                        "Audio routed to ${AudioOutputHelper.typeToString(routed.type)} instead of USB",
                        activeDeviceId,
                        routed.productName?.toString()
                    )
                }
            }
        }
    }

    @Synchronized
    fun clearMixerPreference() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && activeDeviceId != -1) {
            val device = getConnectedUsbDevice()
            if (device != null && device.id == activeDeviceId) {
                try {
                    audioManager.clearPreferredMixerAttributes(mediaAudioAttributes, device)
                    Log.d(TAG, "Cleared preferred mixer attributes for device ${device.id}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear preferred mixer attributes", e)
                }
            }
        }
        activeDeviceId = -1
        activeMixerAttributes = null
    }

    private fun getConnectedUsbDevice(): AudioDeviceInfo? {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.firstOrNull { isUsbDevice(it) }
    }

    private fun isUsbDevice(device: AudioDeviceInfo): Boolean {
        val type = device.type
        return type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                type == AudioDeviceInfo.TYPE_USB_ACCESSORY
    }
}
