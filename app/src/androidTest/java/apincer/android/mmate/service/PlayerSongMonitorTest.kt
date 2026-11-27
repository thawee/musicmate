package apincer.android.mmate.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayerSongMonitorTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var context: RecordingContext
    private lateinit var preferences: SharedPreferences
    private lateinit var monitor: PlayerSongMonitor

    @Before
    fun setUp() = onMain {
        context = RecordingContext(instrumentation.targetContext)
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        monitor = PlayerSongMonitor(context)
    }

    @After
    fun tearDown() = onMain {
        monitor.stop()
        preferences.edit().clear().commit()
    }

    @Test
    fun disabledByDefaultAndRegistersOnlyWhileStartedAndEnabled() = onMain {
        monitor.start()
        assertEquals(0, context.registrations)

        enable()
        monitor.start()
        assertEquals(1, context.registrations)
        assertEquals(Context.RECEIVER_EXPORTED, context.flags)
        assertEquals(
            setOf(
                TRACK_CHANGED, STATUS_CHANGED, META_CHANGED, PLAY_STATE_CHANGED, PLAYBACK_COMPLETE,
                UAPP_META_CHANGED, UAPP_PLAY_STATE_CHANGED, HIBY
            ),
            context.actions
        )

        monitor.stop()
        assertEquals(1, context.unregistrations)
        preferences.edit().putBoolean(PlayerSongMonitor.PREF_ENABLED, false).commit()
        enable()
        assertEquals(1, context.registrations)
        monitor.start()
        assertEquals(2, context.registrations)
    }

    @Test
    fun bundleTrackDoesNotInventPlayingAndTopLevelTrackSupportsBuild948() = onMain {
        enable()
        monitor.start()
        context.deliver(Intent(TRACK_CHANGED).putExtra("track", Bundle().apply {
            putString("title", "  Bundle title  ")
            putString("artist", "Artist")
            putString("album", "Album")
            putString("path", "/never/open/this.mp3")
        }))
        assertEquals(PlayerSong("Poweramp", "Bundle title", "Artist", "Album", null), monitor.song.value)

        context.deliver(Intent(TRACK_CHANGED).putExtra("title", "Top-level title"))
        assertEquals(PlayerSong("Poweramp", "Top-level title", "", "", null), monitor.song.value)
    }

    @Test
    fun stickyStatusThenTrackPreservesPausedState() = onMain {
        enable()
        context.sticky = status(2)
        monitor.start()
        assertNull(monitor.song.value)
        context.deliver(track("Paused track"))
        assertEquals(false, monitor.song.value?.isPlaying)
    }

    @Test
    fun stickyTrackThenStatusUpdatesAndStoppedClears() = onMain {
        enable()
        context.sticky = track("Sticky track")
        monitor.start()
        assertNull(monitor.song.value?.isPlaying)
        context.deliver(status(1))
        assertEquals(true, monitor.song.value?.isPlaying)
        context.deliver(status(2))
        assertEquals(false, monitor.song.value?.isPlaying)
        context.deliver(status(0))
        assertNull(monitor.song.value)
        context.deliver(track("Late sticky track"))
        assertNull(monitor.song.value)
        context.deliver(status(1))
        assertEquals(PlayerSong("Poweramp", "Late sticky track", "", "", true), monitor.song.value)
    }

    @Test
    fun disablingClearsImmediatelyAndRejectsQueuedBroadcasts() = onMain {
        enable()
        monitor.start()
        context.deliver(track("Visible"))
        val oldReceiver = context.receiver!!
        preferences.edit().putBoolean(PlayerSongMonitor.PREF_ENABLED, false).commit()
        assertNull(monitor.song.value)
        assertEquals(1, context.unregistrations)
        oldReceiver.onReceive(context, track("Queued after disable"))
        assertNull(monitor.song.value)

        enable()
        context.deliver(track("Visible again"))
        oldReceiver.onReceive(context, track("Queued from previous registration"))
        assertEquals("Visible again", monitor.song.value?.title)
        val currentReceiver = context.receiver!!
        monitor.stop()
        assertNull(monitor.song.value)
        currentReceiver.onReceive(context, track("Queued after stop"))
        assertNull(monitor.song.value)
    }

    @Test
    fun rejectsEmptyOrMistypedTitlesAndBoundsDisplayText() = onMain {
        enable()
        monitor.start()
        context.deliver(track("Valid"))
        context.deliver(track(" \n\t "))
        assertNull(monitor.song.value)
        context.deliver(Intent(TRACK_CHANGED).putExtra("title", 42))
        assertNull(monitor.song.value)
        context.deliver(track("a".repeat(1000)).putExtra("artist", "b".repeat(1000)))
        assertEquals(512, monitor.song.value?.title?.length)
        assertEquals(512, monitor.song.value?.artist?.length)
        context.deliver(Intent("unrelated.action").putExtra("title", "Injected"))
        assertFalse(monitor.song.value?.title == "Injected")
    }

    @Test
    fun pausedBooleanFallbackDoesNotTreatMissingStatusAsPlaying() = onMain {
        enable()
        monitor.start()
        context.deliver(track("Track"))
        context.deliver(Intent(STATUS_CHANGED))
        assertNull(monitor.song.value?.isPlaying)
        context.deliver(Intent(STATUS_CHANGED).putExtra("paused", true))
        assertEquals(false, monitor.song.value?.isPlaying)
        context.deliver(Intent(STATUS_CHANGED).putExtra("paused", false))
        assertEquals(true, monitor.song.value?.isPlaying)
    }

    @Test
    fun standardMetaChangedNamesNeutronFromPackage() = onMain {
        enable()
        monitor.start()
        context.deliver(Intent(META_CHANGED).putExtra("package", "com.neutroncode.mp")
            .putExtra("track", "  Neutron song ").putExtra("artist", "Artist").putExtra("album", "Album")
            .putExtra("playing", true))
        assertEquals(PlayerSong("Neutron", "Neutron song", "Artist", "Album", true), monitor.song.value)
    }

    @Test
    fun uappActionNamesUappAndLaterPausedStateKeepsSong() = onMain {
        enable()
        monitor.start()
        context.deliver(Intent(UAPP_META_CHANGED).putExtra("track", "UAPP song").putExtra("playing", true))
        assertEquals(PlayerSong("UAPP", "UAPP song", "", "", true), monitor.song.value)
        context.deliver(Intent(UAPP_PLAY_STATE_CHANGED).putExtra("playing", false))
        assertEquals(PlayerSong("UAPP", "UAPP song", "", "", false), monitor.song.value)
        context.deliver(Intent(UAPP_META_CHANGED).putExtra("track", "Next UAPP song"))
        assertEquals(PlayerSong("UAPP", "Next UAPP song", "", "", false), monitor.song.value)
    }

    @Test
    fun laterPausedPlayStateFlipsSamePlayer() = onMain {
        enable()
        monitor.start()
        context.deliver(Intent(META_CHANGED).putExtra("package", "com.neutroncode.mp")
            .putExtra("track", "Song").putExtra("playing", true))
        context.deliver(Intent(PLAY_STATE_CHANGED).putExtra("package", "com.neutroncode.mp")
            .putExtra("paused", true))
        assertEquals(PlayerSong("Neutron", "Song", "", "", false), monitor.song.value)
        context.deliver(Intent(HIBY).putExtra("track", "HiBy song").putExtra("playing", true))
        assertEquals(PlayerSong("HiBy Music", "HiBy song", "", "", true), monitor.song.value)
    }

    @Test
    fun mostRecentPlayerWinsAndStopFallsBack() = onMain {
        enable()
        monitor.start()
        context.deliver(Intent(META_CHANGED).putExtra("package", "com.neutroncode.mp")
            .putExtra("track", "Neutron song").putExtra("playing", true))
        context.deliver(track("Poweramp song"))
        context.deliver(status(1))
        assertEquals(PlayerSong("Poweramp", "Poweramp song", "", "", true), monitor.song.value)
        context.deliver(status(0))
        assertEquals(PlayerSong("Neutron", "Neutron song", "", "", true), monitor.song.value)
        context.deliver(track("Poweramp again"))
        context.deliver(status(1))
        context.deliver(Intent(PLAY_STATE_CHANGED).putExtra("package", "com.neutroncode.mp"))
        assertEquals("Poweramp", monitor.song.value?.player)
    }

    @Test
    fun playbackCompleteStopsOnlyThatPlayer() = onMain {
        enable()
        monitor.start()
        context.deliver(Intent(UAPP_META_CHANGED).putExtra("track", "UAPP song").putExtra("playing", true))
        context.deliver(Intent(META_CHANGED).putExtra("package", "com.neutroncode.mp")
            .putExtra("track", "Neutron song").putExtra("playing", false))
        context.deliver(Intent(PLAYBACK_COMPLETE).putExtra("package", "com.neutroncode.mp"))
        assertEquals(PlayerSong("UAPP", "UAPP song", "", "", true), monitor.song.value)
        context.deliver(Intent(META_CHANGED).putExtra("package", "com.neutroncode.mp")
            .putExtra("track", "Neutron next").putExtra("playing", true))
        assertEquals(PlayerSong("Neutron", "Neutron next", "", "", true), monitor.song.value)
    }

    @Test
    fun unknownPackageWithoutPlayerExtraIsGenericAndPlayerExtraIsBounded() = onMain {
        enable()
        monitor.start()
        context.deliver(Intent(META_CHANGED).putExtra("package", "com.example.unknown")
            .putExtra("track", "Unknown"))
        assertEquals(PlayerSong("Music player", "Unknown", "", "", null), monitor.song.value)
        context.deliver(Intent(META_CHANGED).putExtra("player", "  Custom player ")
            .putExtra("track", "Named"))
        assertEquals("Custom player", monitor.song.value?.player)
        context.deliver(Intent(META_CHANGED).putExtra("player", "p".repeat(100))
            .putExtra("track", "Long"))
        assertEquals("p".repeat(64), monitor.song.value?.player)
    }

    private fun enable() {
        assertTrue(preferences.edit().putBoolean(PlayerSongMonitor.PREF_ENABLED, true).commit())
    }

    private fun track(title: String) = Intent(TRACK_CHANGED).putExtra("title", title)
    private fun status(state: Int) = Intent(STATUS_CHANGED).putExtra("state", state)
    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync { action() }

    /** Real Bundle/prefs behavior without registering globally or depending on installed players. */
    private class RecordingContext(base: Context) : ContextWrapper(base) {
        private val preferenceName = "player-monitor-test-${UUID.randomUUID()}"
        var receiver: BroadcastReceiver? = null
        var sticky: Intent? = null
        var registrations = 0
        var unregistrations = 0
        var flags = 0
        var actions = emptySet<String>()

        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences(preferenceName, mode)

        override fun registerReceiver(
            receiver: BroadcastReceiver?, filter: IntentFilter, flags: Int
        ): Intent? {
            registrations++
            this.receiver = receiver
            this.flags = flags
            actions = (0 until filter.countActions()).map(filter::getAction).toSet()
            return sticky
        }

        override fun unregisterReceiver(receiver: BroadcastReceiver) {
            assertEquals(this.receiver, receiver)
            unregistrations++
            this.receiver = null
        }

        fun deliver(intent: Intent) {
            receiver?.onReceive(this, intent)
        }
    }

    companion object {
        private const val TRACK_CHANGED = "com.maxmpz.audioplayer.TRACK_CHANGED"
        private const val STATUS_CHANGED = "com.maxmpz.audioplayer.STATUS_CHANGED"
        private const val META_CHANGED = "com.android.music.metachanged"
        private const val PLAY_STATE_CHANGED = "com.android.music.playstatechanged"
        private const val PLAYBACK_COMPLETE = "com.android.music.playbackcomplete"
        private const val UAPP_META_CHANGED = "com.extreamsd.usbaudioplayershared.metachanged"
        private const val UAPP_PLAY_STATE_CHANGED = "com.extreamsd.usbaudioplayershared.playstatechanged"
        private const val HIBY = "com.hiby.music"
    }
}
