package apincer.android.mmate.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BadParcelableException
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager

data class PowerampSong(
    val title: String,
    val artist: String,
    val album: String,
    val isPlaying: Boolean?
)

/**
 * Call start/stop with the visible activity's lifecycle. Observation is opt-in and foreground only.
 * These exported broadcasts have no authenticated sender: metadata is untrusted display text only.
 * No URI, path, artwork, transport command, or file access is derived from their contents.
 */
class PowerampSongMonitor(context: Context) {
    private val context = context.applicationContext
    private val preferences = PreferenceManager.getDefaultSharedPreferences(this.context)
    private val main = Handler(Looper.getMainLooper())
    private val mutableSong = MutableLiveData<PowerampSong?>(null)
    val song: LiveData<PowerampSong?> = mutableSong

    private var started = false
    private var registeredReceiver: BroadcastReceiver? = null
    private var track: PowerampSong? = null
    private var status = Status.UNKNOWN

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PREF_ENABLED || key == null) onMain { updateRegistration() }
    }

    fun start() = onMain {
        if (!started) {
            started = true
            preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
            updateRegistration()
        }
    }

    fun stop() = onMain {
        started = false
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        unregister()
        clear()
    }

    private fun updateRegistration() {
        if (!started || !enabled()) {
            unregister()
            clear()
            return
        }
        if (registeredReceiver != null) return
        val filter = IntentFilter(ACTION_TRACK_CHANGED).apply { addAction(ACTION_STATUS_CHANGED) }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (registeredReceiver === this && started && enabled()) receive(intent)
            }
        }
        registeredReceiver = receiver
        try {
            // Poweramp is a separate app, so RECEIVER_NOT_EXPORTED would discard its broadcasts.
            val sticky = context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            if (registeredReceiver === receiver && started && enabled()) receive(sticky)
        } catch (_: SecurityException) {
            registeredReceiver = null
            clear()
        } catch (_: IllegalArgumentException) {
            registeredReceiver = null
            clear()
        }
    }

    private fun unregister() {
        val receiver = registeredReceiver ?: return
        registeredReceiver = null
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // Already removed by the context; the monitor still resets its local state.
        }
    }

    private fun enabled(): Boolean = try {
        preferences.getBoolean(PREF_ENABLED, false)
    } catch (_: ClassCastException) {
        false
    }

    private fun receive(intent: Intent?) {
        if (intent == null) return
        try {
            when (intent.action) {
                ACTION_TRACK_CHANGED -> {
                    val extras = intent.extras ?: return
                    val fields = extras.getBundle("track") ?: extras
                    val title = fields.text("title")
                    track = if (title.isEmpty()) null else PowerampSong(
                        title, fields.text("artist"), fields.text("album"), null
                    )
                }
                ACTION_STATUS_CHANGED -> {
                    val extras = intent.extras ?: return
                    status = when (extras.getInt("state", -1)) {
                        0 -> Status.STOPPED
                        1 -> Status.PLAYING
                        2 -> Status.PAUSED
                        -1 -> if (extras.containsKey("paused")) {
                            if (extras.getBoolean("paused")) Status.PAUSED else Status.PLAYING
                        } else Status.UNKNOWN
                        else -> return
                    }
                    if (status == Status.STOPPED) track = null
                }
                else -> return
            }
            mutableSong.value = if (status == Status.STOPPED) null else track?.copy(
                isPlaying = when (status) {
                    Status.PLAYING -> true
                    Status.PAUSED -> false
                    else -> null
                }
            )
        } catch (_: BadParcelableException) {
            // A malformed external payload must not crash the activity or become a song.
        } catch (_: ClassCastException) {
            // Ignore extras with the wrong type.
        } catch (_: IllegalArgumentException) {
            // Ignore malformed parcel/container values.
        }
    }

    private fun clear() {
        track = null
        status = Status.UNKNOWN
        mutableSong.value = null
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post { action() }
    }

    private fun Bundle.text(key: String): String = getString(key).orEmpty().take(512).trim()

    private enum class Status { UNKNOWN, PLAYING, PAUSED, STOPPED }

    companion object {
        const val PREF_ENABLED = "poweramp_song_detection"
        private const val ACTION_TRACK_CHANGED = "com.maxmpz.audioplayer.TRACK_CHANGED"
        private const val ACTION_STATUS_CHANGED = "com.maxmpz.audioplayer.STATUS_CHANGED"
    }
}
