package apincer.android.mmate.audio

import android.app.UiModeManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.util.Log
import apincer.android.mmate.audio.dsp.CarDsp
import apincer.android.mmate.utils.AudioOutputHelper
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Records which output Android routes MusicMate's media to, so the car's route signature
 * (wired or wireless Android Auto) can be read from a real drive before Car DSP relies on it
 * (docs/technical/CAR_AUDIO_DSP.md). Each change goes to logcat and to [FILE_NAME] in the app's
 * external files folder, which `adb pull` can read without a debuggable build. Each entry also
 * carries every output device, the UI mode (3 = car), the format MusicMate asked a USB DAC for and
 * the connected Bluetooth devices, since wireless Android Auto shows up only as a generic remote
 * submix and the car's Bluetooth link is what can name the car.
 */
class MediaRouteLog internal constructor(
    private val audioManager: AudioManager,
    private val uiModeManager: UiModeManager?,
    private val mediaAttributes: AudioAttributes,
    private val usbFormat: (Int) -> AudioFormat?,
    private val bluetooth: Links,
    private val file: File?,
    private val onRoute: (List<AudioDeviceInfo>) -> Unit = {}
) {
    /** The Bluetooth devices connected right now, as one log field. */
    interface Links {
        fun describe(): String
        fun close() {}

        /** Runs [action] whenever Bluetooth becomes readable, which happens after [start]. */
        fun whenReady(action: () -> Unit) {}
    }

    constructor(context: Context, usbOutputFormat: UsbOutputFormat) : this(
        context.getSystemService(AudioManager::class.java),
        context.getSystemService(UiModeManager::class.java),
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
        usbOutputFormat::requestedFormat,
        BluetoothLinks(context),
        context.getExternalFilesDir(null)?.let { File(it, FILE_NAME) },
        CarDsp::updateRoute
    )

    private var lastRoute: String? = null

    /** Also told each checked route, after Car DSP's USB guard; Speaker Tuning's auto-switch listens here. */
    @Volatile
    var routeListener: ((List<AudioDeviceInfo>) -> Unit)? = null
    private var lastCheckNanos = 0L

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = check("devices added")
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = check("devices removed")
    }

    /** Android calls back once on registration, so the starting route is logged too. */
    fun start() {
        bluetooth.whenReady { check("bluetooth ready") }
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
    }

    fun release() {
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        bluetooth.close()
    }

    /**
     * Call often during playback: checks at most every [POLL_INTERVAL_NANOS], so a route Android
     * changes without adding or removing a device is still caught.
     */
    @Synchronized
    fun poll() {
        if (System.nanoTime() - lastCheckNanos >= POLL_INTERVAL_NANOS) check("playing")
    }

    /** Adds one event line, such as an automatic car switch, so a release build can show it. */
    @Synchronized
    fun note(event: String) {
        val line = "${LocalDateTime.now().format(TIME)} $event"
        Log.i(TAG, line)
        file?.let { append(it, line) }
    }

    /**
     * Hands the media route to [onRoute], which feeds Car DSP's USB guard (keep this call if the
     * logging is ever removed), then logs the route if it changed.
     */
    @Synchronized
    fun check(reason: String) {
        lastCheckNanos = System.nanoTime()
        val route = try {
            val routed = audioManager.getAudioDevicesForAttributes(mediaAttributes)
            onRoute(routed)
            routeListener?.invoke(routed)
            val media = routed.joinToString(" + ") { describe(it) }.ifEmpty { "none" }
            val usb = routed.firstNotNullOfOrNull { usbFormat(it.id) }?.let { UsbOutputFormat.describe(it) } ?: "android default"
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .joinToString(", ") { "${it.type}:\"${it.productName}\"#${it.id}" }
            "$media | usbFormat=$usb | uiMode=${uiModeManager?.currentModeType} | outputs=[$outputs] | bt=${bluetooth.describe()}"
        } catch (error: RuntimeException) {
            Log.w(TAG, "Media route unavailable", error)
            return
        }
        if (route == lastRoute) return
        lastRoute = route
        val line = "${LocalDateTime.now().format(TIME)} $reason: $route"
        Log.i(TAG, line)
        file?.let { append(it, line) }
    }

    private fun append(target: File, line: String) {
        try {
            val lines = if (target.exists()) target.readLines() else emptyList()
            target.writeText((lines + line).takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
        } catch (error: IOException) {
            Log.w(TAG, "Media route log not written", error)
        }
    }

    companion object {
        private const val TAG = "MediaRoute"
        const val FILE_NAME = "media_route_log.txt"
        const val MAX_LINES = 200
        private const val POLL_INTERVAL_NANOS = 5_000_000_000L
        private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        /** The fields Car DSP may match on, plus what MusicMate's USB DAC logic makes of the device. */
        @JvmStatic
        fun describe(device: AudioDeviceInfo): String =
            "type=${device.type} (${AudioOutputHelper.typeToString(device.type)})" +
                " product=\"${device.productName}\" id=${device.id} address=\"${device.address}\"" +
                " rates=${device.sampleRates.contentToString()} encodings=${device.encodings.contentToString()}" +
                " usbDacPath=${UsbOutputFormat.isUsbType(device.type)}"
    }
}

/** Devices connected for calls (HFP) and media (A2DP); the car keeps HFP up during wireless Android Auto. */
private class BluetoothLinks(context: Context) : MediaRouteLog.Links {
    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val proxies = ConcurrentHashMap<Int, BluetoothProfile>()
    @Volatile private var onReady: (() -> Unit)? = null

    init {
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                proxies[profile] = proxy
                onReady?.invoke()
            }

            override fun onServiceDisconnected(profile: Int) {
                proxies.remove(profile)
            }
        }
        PROFILES.forEach { (profile, _) -> adapter?.getProfileProxy(context.applicationContext, listener, profile) }
    }

    override fun describe(): String = try {
        PROFILES.joinToString(" ") { (profile, label) ->
            val devices = proxies[profile]?.connectedDevices
            "$label=" + (devices?.joinToString(",", "[", "]") { "\"${it.name}\"@${it.address}" } ?: "n/a")
        }
    } catch (error: SecurityException) {
        "no permission"
    }

    override fun whenReady(action: () -> Unit) {
        onReady = action
    }

    override fun close() {
        proxies.forEach { (profile, proxy) -> adapter?.closeProfileProxy(profile, proxy) }
        proxies.clear()
    }

    private companion object {
        val PROFILES = listOf(BluetoothProfile.HEADSET to "hfp", BluetoothProfile.A2DP to "a2dp")
    }
}
