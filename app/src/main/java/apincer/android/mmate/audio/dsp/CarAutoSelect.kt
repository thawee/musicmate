package apincer.android.mmate.audio.dsp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.function.Consumer

/**
 * Keeps the active speaker in step with where media plays (see [CarRoute.choose]): a Bluetooth
 * speaker by its address, Android Auto by the car's last handshake, anything else No correction,
 * so a speaker's EQ never follows the music elsewhere. It acts only when the route changes, so a
 * speaker picked by hand holds until then. Each switch goes to [events], which release builds keep
 * (they strip Log.i).
 */
class CarAutoSelect(context: Context, private val events: Consumer<String>) {
    private val app = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile
    private var route: CarRoute? = null

    fun start() {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "BLUETOOTH_CONNECT not granted, the car is not recognised in Android Auto")
            events.accept("speaker auto-switch: Bluetooth permission not granted, cars are not recognised")
        }
        // A car that connects while Android Auto already plays is picked at once
        CarHandshake.onRecorded = {
            later {
                events.accept("car handshake: ${CarHandshake.last(app)?.name}")
                if (route == CarRoute.AndroidAuto) select(CarRoute.AndroidAuto)
            }
        }
        events.accept("speaker auto-switch listening")
    }

    fun release() {
        CarHandshake.onRecorded = null
        events.accept("speaker auto-switch stopped")
        worker.shutdown()
    }

    /** Fed by every route check; acts only when the route differs from the last one. */
    @Synchronized
    fun onRoute(routed: List<AudioDeviceInfo>) {
        val next = CarRoute.ofDevices(routed)
        if (next.key == route?.key) return
        route = next
        // A USB DAC is never processed, so its speaker choice does not matter
        if (next != CarRoute.Usb) later { select(next) }
    }

    /** Runs [task] on the worker, which reads profile files off the caller's thread, in order. */
    private fun later(task: () -> Unit) {
        if (worker.isShutdown) return
        try {
            worker.execute(task)
        } catch (error: RejectedExecutionException) {
            Log.w(TAG, "speaker switch skipped, the player was released")
        }
    }

    private fun select(route: CarRoute) {
        val car = CarRoute.choose(route, CarDspPreferences.profiles(app).list(), CarHandshake.last(app)?.address)
        if (car?.id == CarDspPreferences.activeProfileId(app)) return
        CarDspPreferences.setActiveProfile(app, car)
        events.accept("${describe(route)}: " + (car?.let { "${it.name} correction is now active" } ?: "No correction"))
    }

    private fun describe(route: CarRoute) = when (route) {
        is CarRoute.Bluetooth -> "Bluetooth ${route.name ?: "speaker"}"
        CarRoute.AndroidAuto -> "Android Auto" + (CarHandshake.last(app)?.name?.let { " ($it)" } ?: "")
        CarRoute.Usb -> "USB"
        CarRoute.Other -> "phone output"
    }

    private companion object {
        const val TAG = "CarDsp"
    }
}
