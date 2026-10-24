package apincer.android.mmate.audio.dsp

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.core.content.ContextCompat
import apincer.android.mmate.audio.UsbOutputFormat
import apincer.android.mmate.audio.calibration.CarProfile

/**
 * Where media plays, as far as Speaker Tuning cares. Wireless Android Auto shows up only as a
 * remote submix whose address changes every session, so the car itself is named by the Bluetooth
 * handshake that starts the session (see [CarHandshake]); the car's hands-free link drops within
 * seconds of it (MU-X drive, 2026-10-24).
 */
sealed class CarRoute(val key: String) {
    class Identity(val name: String?, val address: String?, val kind: String)

    object Usb : CarRoute("usb")
    object AndroidAuto : CarRoute("android-auto")
    class Bluetooth(val address: String, val name: String?) : CarRoute("bt:" + address.uppercase())
    object Other : CarRoute("other")

    companion object {
        /** [routed] as (type, address, product name) for each device Android routes media to. */
        fun of(routed: List<Triple<Int, String?, String?>>): CarRoute {
            if (routed.any { UsbOutputFormat.isUsbType(it.first) }) return Usb
            if (routed.any { it.first == AudioDeviceInfo.TYPE_REMOTE_SUBMIX }) return AndroidAuto
            routed.firstOrNull { it.first in BLUETOOTH_TYPES && !it.second.isNullOrEmpty() }
                ?.let { return Bluetooth(it.second!!, it.third) }
            return Other
        }

        @JvmStatic
        fun ofDevices(routed: List<AudioDeviceInfo>): CarRoute =
            of(routed.map { Triple(it.type, it.address, it.productName?.toString()) })

        /** Where media plays right now. */
        fun current(context: Context): CarRoute {
            val media = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            return ofDevices(context.getSystemService(AudioManager::class.java).getAudioDevicesForAttributes(media))
        }

        /**
         * Who is being tuned, from where the sweep plays: in Android Auto the car of the last
         * handshake (its hands-free link is gone by then), on a Bluetooth speaker the speaker
         * itself. Null on the phone's own outputs.
         */
        fun identity(context: Context): Identity? = when (val route = current(context)) {
            AndroidAuto -> CarHandshake.last(context).let { Identity(it?.name, it?.address, CarProfile.KIND_CAR) }
            is Bluetooth -> Identity(route.name, route.address, CarProfile.KIND_SPEAKER)
            else -> null
        }

        /**
         * The profile for [route]: a Bluetooth speaker by its own address; Android Auto by the car
         * whose handshake came last, else the only car profile; anything else none. Null means
         * No correction. A USB route never reaches here, since Car DSP bypasses it.
         */
        fun choose(route: CarRoute, profiles: List<CarProfile>, handshakeAddress: String?): CarProfile? = when (route) {
            is Bluetooth -> profiles.firstOrNull { it.bluetoothAddress.equals(route.address, ignoreCase = true) }
            AndroidAuto -> handshakeAddress?.let { address -> profiles.firstOrNull { it.bluetoothAddress.equals(address, ignoreCase = true) } }
                ?: profiles.filter { it.kind == CarProfile.KIND_CAR }.singleOrNull()
            else -> null
        }

        private val BLUETOOTH_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST
        )
    }
}

/**
 * The last car that connected over Bluetooth, kept in preferences so a drive that started while
 * MusicMate was closed is still known. Only devices whose Bluetooth class says car audio or
 * hands-free count, so headphones and speakers never pose as the car.
 */
object CarHandshake {
    private const val FILE = "car_dsp"
    private const val ADDRESS = "handshake_address"
    private const val NAME = "handshake_name"
    private const val MILLIS = "handshake_millis"

    class Car(val address: String, val name: String?)

    /** Told when a new handshake is saved, so a running app can pick the car at once. */
    @Volatile
    var onRecorded: (() -> Unit)? = null

    fun last(context: Context): Car? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val address = prefs.getString(ADDRESS, null) ?: return null
        return Car(address, prefs.getString(NAME, null))
    }

    fun isCar(deviceClass: Int?) =
        deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO || deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE

    internal fun record(context: Context, address: String, name: String?) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(ADDRESS, address).putString(NAME, name).putLong(MILLIS, System.currentTimeMillis()).apply()
        onRecorded?.invoke()
    }
}

/**
 * Declared in the manifest because the hands-free broadcast is exempt from the background limit
 * (developer.android.com, implicit broadcast exceptions), so the handshake is caught even when
 * MusicMate is not running.
 */
class CarHandshakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED) return
        if (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) != BluetoothProfile.STATE_CONNECTED) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        try {
            if (CarHandshake.isCar(device.bluetoothClass?.deviceClass)) CarHandshake.record(context, device.address, device.name)
        } catch (error: SecurityException) {
            // Permission revoked between the check and the read: nothing to record
        }
    }
}
