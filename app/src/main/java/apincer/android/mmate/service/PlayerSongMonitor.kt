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

data class PlayerSong(
    val player: String,
    val title: String,
    val artist: String,
    val album: String,
    val isPlaying: Boolean?
)

/**
 * Shows the song Poweramp, Neutron, UAPP or HiBy Music reports through their broadcasts.
 * Call start/stop with the visible activity's lifecycle. Observation is opt-in and foreground only.
 * These exported broadcasts have no authenticated sender: metadata is untrusted display text only.
 * The package and player extras only pick the display label. No URI, path, artwork, transport
 * command, or file access is derived from their contents.
 */
class PlayerSongMonitor(context: Context) {
    private val context = context.applicationContext
    private val preferences = PreferenceManager.getDefaultSharedPreferences(this.context)
    private val main = Handler(Looper.getMainLooper())
    private val mutableSong = MutableLiveData<PlayerSong?>(null)
    val song: LiveData<PlayerSong?> = mutableSong

    private var started = false
    private var registeredReceiver: BroadcastReceiver? = null
    /** Insertion order is update order, so the last entry is the most recently updated player. */
    private val players = LinkedHashMap<String, PlayerState>()

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
        val filter = IntentFilter().apply {
            Source.entries.forEach { it.actions.forEach(::addAction) }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (registeredReceiver === this && started && enabled()) receive(intent)
            }
        }
        registeredReceiver = receiver
        try {
            // The players are separate apps, so RECEIVER_NOT_EXPORTED would discard their broadcasts.
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
            val action = intent.action ?: return
            val source = Source.entries.firstOrNull { action in it.actions } ?: return
            val extras = intent.extras ?: return
            val report = source.parse(action, extras) ?: return
            val previous = players.remove(report.player) ?: PlayerState(null, Status.UNKNOWN)
            players[report.player] = report.update(previous)
            // The player extra is sender-chosen text, so the number of keys it can create is capped.
            if (players.size > MAX_PLAYERS) players.remove(players.keys.first())
            mutableSong.value = players.values.lastOrNull { it.track != null && it.status != Status.STOPPED }
                ?.let { it.track?.copy(isPlaying = it.status.isPlaying) }
        } catch (_: BadParcelableException) {
            // A malformed external payload must not crash the activity or become a song.
        } catch (_: ClassCastException) {
            // Ignore extras with the wrong type.
        } catch (_: IllegalArgumentException) {
            // Ignore malformed parcel/container values.
        }
    }

    private fun clear() {
        players.clear()
        mutableSong.value = null
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post { action() }
    }

    private data class PlayerState(val track: PlayerSong?, val status: Status)

    private class Report(val player: String, val update: (PlayerState) -> PlayerState)

    private enum class Status(val isPlaying: Boolean?) {
        UNKNOWN(null), PLAYING(true), PAUSED(false), STOPPED(null)
    }

    private enum class Source(vararg val actions: String) {
        POWERAMP("com.maxmpz.audioplayer.TRACK_CHANGED", "com.maxmpz.audioplayer.STATUS_CHANGED") {
            override fun parse(action: String, extras: Bundle): Report? {
                if (action == actions[0]) {
                    val fields = extras.getBundle("track") ?: extras
                    val title = fields.text("title")
                    val track = if (title.isEmpty()) null else PlayerSong(
                        POWERAMP_NAME, title, fields.text("artist"), fields.text("album"), null
                    )
                    return Report(POWERAMP_NAME) { it.copy(track = track) }
                }
                val status = when (extras.getInt("state", -1)) {
                    0 -> Status.STOPPED
                    1 -> Status.PLAYING
                    2 -> Status.PAUSED
                    -1 -> extras.pausedStatus() ?: Status.UNKNOWN
                    else -> return null
                }
                return Report(POWERAMP_NAME) {
                    PlayerState(if (status == Status.STOPPED) null else it.track, status)
                }
            }
        },
        STANDARD(
            "com.android.music.metachanged",
            "com.android.music.playstatechanged",
            "com.android.music.playbackcomplete"
        ) {
            override fun parse(action: String, extras: Bundle) =
                if (action == actions[2]) Report(standardPlayer(extras)) { PlayerState(null, Status.STOPPED) }
                else standardReport(standardPlayer(extras), extras)
        },
        UAPP(
            "com.extreamsd.usbaudioplayershared.metachanged",
            "com.extreamsd.usbaudioplayershared.playstatechanged"
        ) {
            override fun parse(action: String, extras: Bundle) = standardReport("UAPP", extras)
        },
        HIBY("com.hiby.music") {
            override fun parse(action: String, extras: Bundle) = standardReport("HiBy Music", extras)
        };

        abstract fun parse(action: String, extras: Bundle): Report?
    }

    companion object {
        const val PREF_ENABLED = "poweramp_song_detection"
        private const val POWERAMP_NAME = "Poweramp"
        private const val MAX_PLAYERS = 8
        private val KNOWN_PLAYERS = mapOf(
            "com.neutroncode.mp" to "Neutron",
            "com.neutroncode.mpeval" to "Neutron",
            "com.hiby.music" to "HiBy Music",
            "com.extreamsd.usbaudioplayerpro" to "UAPP",
            "com.maxmpz.audioplayer" to POWERAMP_NAME
        )

        private fun Bundle.text(key: String, max: Int = 512): String = getString(key).orEmpty().take(max).trim()

        private fun Bundle.pausedStatus(): Status? =
            if (containsKey("paused")) if (getBoolean("paused")) Status.PAUSED else Status.PLAYING else null

        private fun standardPlayer(extras: Bundle): String {
            val player = extras.text("player", 64)
            val id = extras.text("package").ifEmpty { player }
            return KNOWN_PLAYERS[id] ?: player.ifEmpty { "Music player" }
        }

        /** A missing track or play state leaves the stored one, so state-only intents keep the song. */
        private fun standardReport(player: String, extras: Bundle): Report? {
            val title = extras.text("track")
            val track = if (title.isEmpty()) null else PlayerSong(
                player, title, extras.text("artist"), extras.text("album"), null
            )
            val status = when {
                extras.containsKey("playing") ->
                    if (extras.getBoolean("playing")) Status.PLAYING else Status.PAUSED
                else -> extras.pausedStatus()
            }
            // An empty report must not make an older player the most recent one.
            if (track == null && status == null) return null
            return Report(player) { PlayerState(track ?: it.track, status ?: it.status) }
        }
    }
}
