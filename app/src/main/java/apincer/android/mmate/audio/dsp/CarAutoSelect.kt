package apincer.android.mmate.audio.dsp

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.function.Consumer

/**
 * Keeps the active car in step with its Bluetooth hands-free link, matched by the address saved at
 * calibration: the car becomes active when it connects, and No correction returns when it
 * disconnects, so its EQ never follows the phone out of the car. A device with no saved car changes
 * nothing: it may be headphones. Each event goes to [events], which release builds keep (they
 * strip Log.i).
 */
class CarAutoSelect(context: Context, private val events: Consumer<String>) {
    private val app = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
            val address = address(device) ?: return
            when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)) {
                BluetoothProfile.STATE_CONNECTED -> later { connected(address) }
                BluetoothProfile.STATE_DISCONNECTED -> later { disconnected(address) }
            }
        }
    }

    fun start() {
        // Without it Android delivers nothing and throws nothing, so say so in the log
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "BLUETOOTH_CONNECT not granted, cars are not switched automatically")
            events.accept("car auto-switch off: Bluetooth permission not granted")
            return
        }
        // Exported because the Bluetooth stack, not the system server, sends this; it is a
        // protected broadcast, so no other app can fake it
        ContextCompat.registerReceiver(
            app, receiver, IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED
        )
        registered = true
        events.accept("car auto-switch listening")
        // The car may have connected, or left, while the app was not running
        val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        adapter.getProfileProxy(app, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                try {
                    if (registered) {
                        val addresses = proxy.connectedDevices.mapNotNull(::address)
                        addresses.forEach { later { connected(it) } }
                        later { leftWhileStopped(addresses) }
                    }
                } catch (error: SecurityException) {
                    Log.w(TAG, "no Bluetooth permission to list connected devices")
                }
                adapter.closeProfileProxy(profile, proxy)
            }

            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.HEADSET)
    }

    fun release() {
        if (registered) {
            runCatching { app.unregisterReceiver(receiver) }
            events.accept("car auto-switch stopped")
        }
        registered = false
        worker.shutdown()
    }

    private fun address(device: BluetoothDevice): String? = try {
        device.address
    } catch (error: SecurityException) {
        null
    }

    /** Runs [task] on the worker, which reads profile files off the main thread, in arrival order. */
    private fun later(task: () -> Unit) {
        // The proxy callback can arrive after release(); a stopped worker must not crash the app
        if (worker.isShutdown) return
        try {
            worker.execute(task)
        } catch (error: RejectedExecutionException) {
            Log.w(TAG, "car switch skipped, the player was released")
        }
    }

    private fun connected(address: String) {
        val car = CarDspPreferences.profiles(app).forAddress(address)
        when {
            car == null -> events.accept("hands-free device connected, no saved car matches")
            car.id == CarDspPreferences.activeProfileId(app) -> events.accept("car ${car.name} connected, already active")
            else -> {
                CarDspPreferences.setActiveProfile(app, car)
                events.accept("car ${car.name} connected, its correction is now active")
            }
        }
    }

    private fun disconnected(address: String) {
        val car = CarDspPreferences.profiles(app).forAddress(address) ?: return
        if (car.id != CarDspPreferences.activeProfileId(app)) return
        CarDspPreferences.setActiveProfile(app, null)
        events.accept("car ${car.name} disconnected, back to No correction")
    }

    /** An active car whose saved address is not connected gives way to No correction. */
    private fun leftWhileStopped(connected: List<String>) {
        val id = CarDspPreferences.activeProfileId(app) ?: return
        val car = CarDspPreferences.profiles(app).get(id) ?: return
        val address = car.bluetoothAddress ?: return
        if (connected.any { it.equals(address, ignoreCase = true) }) return
        CarDspPreferences.setActiveProfile(app, null)
        events.accept("car ${car.name} not connected, back to No correction")
    }

    private companion object {
        const val TAG = "CarDsp"
    }
}
