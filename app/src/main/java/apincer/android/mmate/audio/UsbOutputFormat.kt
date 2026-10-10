package apincer.android.mmate.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.util.Log

/**
 * Asks Android to run the USB DAC that media plays to at the track's own sample rate and the
 * DAC's deepest bit depth, instead of the shared 48 kHz mixer. Android still mixes and applies
 * volume; this only avoids resampling when the DAC offers the track's rate.
 */
class UsbOutputFormat internal constructor(
    private val audioManager: AudioManager,
    private val mediaAttributes: AudioAttributes
) {
    constructor(context: Context) : this(
        context.getSystemService(AudioManager::class.java),
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    )

    private class Owned(val device: AudioDeviceInfo, val mixer: AudioMixerAttributes)

    private var owned: Owned? = null

    /** The last track format asked for, kept across a pause so resume can ask again. */
    private var lastRequest: Pair<Int, Int>? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            synchronized(this@UsbOutputFormat) {
                if (removedDevices.any { it.id == owned?.device?.id }) owned = null
            }
        }
    }

    fun start() = audioManager.registerAudioDeviceCallback(deviceCallback, null)

    fun release() {
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        clear()
    }

    /** Call before each AudioTrack is created; Android applies the format when it opens. */
    @Synchronized
    fun prepare(sampleRate: Int, channelCount: Int) {
        lastRequest = sampleRate to channelCount
        val device = mediaUsbDevice()
        val mixer = device?.let {
            try {
                choose(audioManager.getSupportedMixerAttributes(it), sampleRate, channelCount)
            } catch (error: RuntimeException) {
                Log.w(TAG, "USB mixer formats unavailable", error)
                null
            }
        }
        val current = owned
        if (current != null && current.device.id == device?.id && current.mixer == mixer && androidHolds(current)) return
        drop(current)
        if (device == null || mixer == null) return
        try {
            if (audioManager.setPreferredMixerAttributes(mediaAttributes, device, mixer)) owned = Owned(device, mixer)
        } catch (error: RuntimeException) {
            Log.w(TAG, "USB mixer format request failed", error)
        }
    }

    /** Returns the USB DAC to Android's default format when playback stops. */
    @Synchronized
    fun clear() {
        lastRequest = null
        drop(owned)
    }

    /** Lets other apps use the DAC at Android's default format while MusicMate is paused. */
    @Synchronized
    fun suspend() = drop(owned)

    /** Asks for the paused track's format again. */
    @Synchronized
    fun resume() {
        val (sampleRate, channelCount) = lastRequest ?: return
        prepare(sampleRate, channelCount)
    }

    /**
     * The format the USB DAC runs at for MusicMate, or null when Android's default applies.
     * Android must still hold the request and still route media to [deviceId].
     */
    @Synchronized
    fun requestedFormat(deviceId: Int): AudioFormat? {
        val current = owned?.takeIf { it.device.id == deviceId } ?: return null
        if (mediaUsbDevice()?.id != deviceId || !androidHolds(current)) return null
        return current.mixer.format
    }

    private fun drop(current: Owned?) {
        if (current == null) return
        owned = null
        try {
            audioManager.clearPreferredMixerAttributes(mediaAttributes, current.device)
        } catch (error: RuntimeException) {
            Log.w(TAG, "USB mixer format reset failed", error)
        }
    }

    private fun androidHolds(current: Owned): Boolean = try {
        audioManager.getPreferredMixerAttributes(mediaAttributes, current.device) == current.mixer
    } catch (error: RuntimeException) {
        false
    }

    private fun mediaUsbDevice(): AudioDeviceInfo? =
        audioManager.getAudioDevicesForAttributes(mediaAttributes).firstOrNull()?.takeIf { it.isUsb() }

    companion object {
        private const val TAG = "UsbOutputFormat"

        /** The default-behavior mixer at exactly [sampleRate] with the most bits, if the DAC offers one. */
        @JvmStatic
        fun choose(offered: List<AudioMixerAttributes>, sampleRate: Int, channelCount: Int): AudioMixerAttributes? =
            offered.filter {
                it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_DEFAULT &&
                    it.format.sampleRate == sampleRate &&
                    Integer.bitCount(it.format.channelMask) == channelCount
            }.maxByOrNull { bits(it.format.encoding) }

        /** Labels a format for the listener, e.g. "88.2 kHz / 32-bit". */
        @JvmStatic
        fun describe(format: AudioFormat): String {
            val khz = format.sampleRate / 1000.0
            val rate = if (khz % 1.0 == 0.0) khz.toInt().toString() else khz.toString()
            return "$rate kHz / ${bits(format.encoding)}-bit"
        }

        @JvmStatic
        fun bits(encoding: Int): Int = when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> 16
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
            AudioFormat.ENCODING_PCM_32BIT -> 32
            else -> 0
        }

        private fun AudioDeviceInfo.isUsb() = type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            type == AudioDeviceInfo.TYPE_USB_HEADSET || type == AudioDeviceInfo.TYPE_USB_ACCESSORY
    }
}
